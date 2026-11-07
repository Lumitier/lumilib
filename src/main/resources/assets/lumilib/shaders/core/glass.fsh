#version 330

// LumiLib - Glas mit echter Brechung, Bildpunktteil.
//
// DER UNTERSCHIED ZU rounded.fsh, und es ist der entscheidende:
// Dort wird eine Farbe eingefaerbt und die Kante beleuchtet. Hier wird
// der HINTERGRUND gelesen und verbogen. Das ist es, was Apples Liquid
// Glass ausmacht - nicht der Glanz, sondern dass sich die Welt hinter
// der Scheibe am Rand verzieht, als laege dort wirklich Material.
//
// WIE DIE BRECHUNG ZUSTANDE KOMMT:
// Die Abstandsfunktion liefert fuer jeden Bildpunkt den Abstand zur
// Kante. Ihr Gefaelle ist die Normale - die Richtung, in die die
// Oberflaeche zeigt. Am Rand steht sie fast waagrecht, in der Mitte
// flach. Verschiebt man die Lesestelle entlang dieser Normalen, und
// zwar umso weiter, je naeher man der Kante ist, entsteht genau die
// Linse: Mitte unverzerrt, Rand gestaucht.
//
// WARUM NICHT EINFACH GLEICHMAESSIG VERSCHIEBEN:
// Dann waere es ein Versatz, kein Glas. Die Staerke MUSS zur Kante hin
// anwachsen, sonst sieht es aus, als sei das Bild dahinter verrutscht.
//
// WEICHZEICHNEN:
// Neun Abgriffe in einem Kreuz. Nicht, weil das besonders gut
// weichzeichnet, sondern weil es billig ist und reicht: Die Scheibe ist
// ohnehin milchig, die letzten Prozent Schaerfe sieht niemand.

uniform sampler2D Sampler0;

in vec4 vColor;
in vec2 vLocal;
in vec2 vHalf;
in float vRadius;

out vec4 fragColor;

const float RIM_W   = 1.5;
const float BEVEL_W = 3.0;
const float LENS_W  = 2.5;
const vec2  LIGHT   = vec2(-0.48191875, 0.8762106);

void layer(inout vec3 c, inout float a, vec3 srcColor, float srcAlpha) {
    float na = srcAlpha + a * (1.0 - srcAlpha);
    c = (srcColor * srcAlpha + c * a * (1.0 - srcAlpha)) / max(na, 1e-5);
    a = na;
}

void main() {
    vec2 q = abs(vLocal) - vHalf + vRadius;
    float d = min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - vRadius;

    float aa = max(fwidth(d), 0.0001);
    float fill = 1.0 - smoothstep(-aa, aa, d);
    if (fill <= 0.001) {
        discard;
    }

    vec2 g = vec2(dFdx(d), dFdy(d));
    vec2 n = (dot(g, g) > 1e-12) ? normalize(g) : vec2(0.0, -1.0);

    // Vorzeichen siehe rounded.fsh: OpenGL zaehlt Zeilen von unten.
    float up   = clamp( n.y, 0.0, 1.0);
    float down = clamp(-n.y, 0.0, 1.0);
    float inside = -d;

    // Groesse der Bildschirmkopie - direkt aus der Textur, damit beim
    // Fenstergroessenwechsel nichts nachgefuehrt werden muss.
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));

    // Wie weit die Lesestelle wandert. An der Kante am staerksten,
    // nach innen schnell auf null. Quadriert, damit die Mitte wirklich
    // ruhig bleibt und die Kante deutlich zieht.
    float bend = 1.0 - smoothstep(0.0, THICK_PX, inside);
    bend = bend * bend * REFRACT_PX;

    // gl_FragCoord zaehlt wie die Kopie von unten - kein Umdrehen noetig.
    vec2 uv = gl_FragCoord.xy * texel;
    vec2 dir = n * texel;

    // DISPERSION - das, was Glas von einer Lupe unterscheidet.
    // Echtes Glas bricht kurze Wellenlaengen staerker als lange. Blau
    // wandert also weiter als Rot, und am Rand entsteht ein feiner
    // Farbsaum. Walksy nennt das REF_DISPERSION und fuehrt es als
    // eigenen Wert; ohne ihn sieht die Brechung aus wie ein Versatz.
    vec3 off = vec3(1.0 - DISPERSE, 1.0, 1.0 + DISPERSE) * bend;

    // Weichzeichnen: dreizehn Abgriffe auf zwei Ringen. Ein echter
    // Gauss waere zwei Durchgaenge und ein zweites Ziel - hier gibt es
    // nur diesen einen Durchgang, also wird der Kreis direkt abgetastet.
    vec2 b = BLUR_PX * texel;
    const vec2 RING[12] = vec2[12](
        vec2( 1.0,  0.0), vec2( 0.5,  0.87), vec2(-0.5,  0.87),
        vec2(-1.0,  0.0), vec2(-0.5, -0.87), vec2( 0.5, -0.87),
        vec2( 0.87, 0.5), vec2( 0.0,  1.0), vec2(-0.87, 0.5),
        vec2(-0.87,-0.5), vec2( 0.0, -1.0), vec2( 0.87,-0.5)
    );

    vec3 bg = vec3(0.0);
    float wsum = 0.0;

    // Mitte, dreifach gewichtet.
    bg.r += texture(Sampler0, uv + dir * off.r).r * 3.0;
    bg.g += texture(Sampler0, uv + dir * off.g).g * 3.0;
    bg.b += texture(Sampler0, uv + dir * off.b).b * 3.0;
    wsum += 3.0;

    for (int i = 0; i < 12; i++) {
        // Innerer Ring enger, aeusserer weiter - zusammen ergibt das
        // einen weicheren Verlauf als zwoelf Abgriffe auf einem Kreis.
        float scale = (i < 6) ? 0.55 : 1.0;
        vec2 o = RING[i] * b * scale;
        float wgt = (i < 6) ? 1.3 : 0.8;
        bg.r += texture(Sampler0, uv + dir * off.r + o).r * wgt;
        bg.g += texture(Sampler0, uv + dir * off.g + o).g * wgt;
        bg.b += texture(Sampler0, uv + dir * off.b + o).b * wgt;
        wsum += wgt;
    }
    bg /= wsum;

    // TONWERT BEGRENZEN - Walksys OVER_WORLD_TONE_FLOOR/CEILING.
    //
    // DAS PROBLEM, das es loest: Eine Scheibe, die wirklich durchlaesst,
    // ist ueber einer Nachtszene schwarz und ueber Schnee blendend. Die
    // Schrift darauf ist dann mal unlesbar dunkel, mal unlesbar hell -
    // und zwar abhaengig davon, wohin der Spieler gerade schaut.
    //
    // Deshalb wird die HELLIGKEIT des Hintergrunds in ein schmales Band
    // gestaucht, bevor getoent wird. Der Farbton bleibt dabei erhalten,
    // weil nur skaliert und nicht gemischt wird - man sieht also weiter,
    // was dahinter liegt, nur nicht mehr, wie dunkel es ist.
    float lum = dot(bg, vec3(0.2126, 0.7152, 0.0722));
    float target = TONE_FLOOR + lum * (TONE_CEIL - TONE_FLOOR);

    // DER FAKTOR MUSS BEGRENZT WERDEN, und das hatte ich vergessen.
    // Bei einem fast schwarzen Bildpunkt wird target/lum riesig - und
    // multipliziert dann nicht nur die Helligkeit hoch, sondern auch
    // den Farbunterschied. Aus einem dunklen Blaustich wird so ein
    // kraeftiges Blau. Genau daher kamen die rosa und gruenen Flecken
    // in der leeren Flaeche.
    float gain = clamp(target / max(lum, 0.02), 0.5, 2.2);
    vec3 toned = bg * gain;

    // Danach die Saettigung zuruecknehmen. Mattiertes Glas streut das
    // Licht; es behaelt den Farbton, aber nicht dessen Kraft. Ohne das
    // bleibt jede Unebenheit im Hintergrund als Farbfleck stehen.
    float tl = dot(toned, vec3(0.2126, 0.7152, 0.0722));
    bg = clamp(mix(vec3(tl), toned, SATURATION), 0.0, 1.0);

    // Die Toenung der Scheibe ueber dem gebrochenen Hintergrund. Die
    // Deckkraft aus vColor sagt, wie milchig das Glas ist.
    vec3 col = mix(bg, vColor.rgb, vColor.a);
    float a = 1.0;

    float rim   = 1.0 - smoothstep(0.0, RIM_W,   inside);
    float bevel = 1.0 - smoothstep(0.0, BEVEL_W, inside);
    float lens  = smoothstep(0.0, RIM_W, inside)
                * (1.0 - smoothstep(RIM_W, RIM_W + LENS_W, inside));
    float spec  = pow(clamp(dot(n, LIGHT), 0.0, 1.0), 7.0);

    // FRESNEL: Flach auf Glas geschaut ist es durchsichtig, streifend
    // wird es zum Spiegel. Am Rand der Flaeche schaut man am
    // streifendsten - deshalb hellt es dort auf, und zwar weiter nach
    // innen als die duenne Lichtkante. Das ist der Unterschied
    // zwischen "Rand mit Linie" und "Scheibe mit Dicke".
    float fres = pow(1.0 - clamp(inside / FRESNEL_PX, 0.0, 1.0), FRESNEL_HARD);

    layer(col, a, vec3(1.0), fres * FRESNEL_A);
    layer(col, a, vec3(1.0), bevel * up   * BEVEL_L);
    layer(col, a, vec3(0.0), bevel * down * BEVEL_D);
    layer(col, a, vec3(1.0), lens * (0.3 + 0.7 * up) * LENS_A);
    layer(col, a, vec3(1.0), rim * (0.35 + 0.65 * up) * RIM_A);
    layer(col, a, vec3(1.0), rim * spec * SPEC_A);

    fragColor = vec4(col, fill);
}
