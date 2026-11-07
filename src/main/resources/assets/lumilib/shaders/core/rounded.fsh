#version 330

// LumiLib - Glasflaeche, Bildpunktteil.
//
// Das Herzstueck ist eine vorzeichenbehaftete Abstandsfunktion (SDF).
// Sie liefert fuer jeden Punkt den Abstand zum Rand der Form:
//     negativ -> innerhalb
//     null    -> genau auf der Kante
//     positiv -> ausserhalb
//
// Fuer ein Rechteck mit runden Ecken ist das vier Zeilen. Der Trick:
// Man schrumpft das Rechteck um den Radius, misst den Abstand zu diesem
// kleineren Rechteck und zieht den Radius wieder ab.
//
// WARUM DAS MEHR KANN ALS EINE FORM AUSSCHNEIDEN:
// Weil der Abstand UEBERALL bekannt ist, nicht nur an der Kante, lassen
// sich Lichtkante, Fase und Innenschatten daraus ableiten - als Baender
// in bestimmten Abstaenden. Frueher waren das drei zusaetzliche PNGs,
// die bei jeder Groesse anders gedehnt wurden. Jetzt sind es drei
// Rechnungen, die bei jeder Groesse gleich sitzen.
//
// WOHER DAS LICHT KOMMT:
// Die Richtung der Kante ergibt sich aus der Aenderung des Abstands von
// einem Bildpunkt zum naechsten. Das ist die Normale. Zeigt sie nach
// oben, faengt die Kante Licht; zeigt sie nach unten, liegt sie im
// Schatten. Genau dieses Gefaelle liest das Auge als Materialdicke -
// und es dreht sich korrekt mit, wenn die Ecke rund ist.
//
// WAS HIER NICHT DRIN IST - und das ehrlich gesagt:
// Apples Liquid Glass lebt von BRECHUNG. Der Hintergrund wird am Rand
// verbogen, als laege dort wirklich eine Linse. Dafuer muesste dieser
// Shader den bereits gezeichneten Hintergrund als Textur bekommen. Hier
// steht die Optik der Kante - Glanz, Linsenband, Dichteverlauf -, nicht
// die Brechung selbst.
//
// WEICHE KANTE OHNE KANTENGLAETTUNG:
// fwidth(d) sagt, um wie viel sich der Abstand von einem Bildpunkt zum
// naechsten aendert. Genau ueber diese Spanne wird ausgeblendet. Dadurch
// bleibt die Kante bei JEDER Groesse und GUI-Skalierung gleich weich.

in vec4 vColor;
in vec2 vLocal;
in vec2 vHalf;
in float vRadius;

out vec4 fragColor;

// Breiten der Baender, in logischen Pixeln.
const float RIM_W   = 1.5;
const float BEVEL_W = 3.0;
const float INNER_W = 6.0;

// Die Linsenkante: ein zweites helles Band direkt INNERHALB der
// Lichtkante. Echtes Glas ist am Rand dicker, dort buendelt sich das
// Licht zu einem schmalen Streifen. Genau dieser zweite Streifen, mit
// einer dunklen Senke dazwischen, laesst eine Flaeche wie ein Stueck
// Material aussehen statt wie eine eingefaerbte Scheibe.
const float LENS_W  = 2.5;

// Richtung des Lichts, von links oben.
//
// ZU DEN VORZEICHEN: n zeigt nach AUSSEN. Oben ist n.y positiv (siehe
// die Anmerkung zu dFdy weiter unten), links ist n.x negativ. Links
// oben ist also (-x, +y).
const vec2 LIGHT = normalize(vec2(-0.55, 1.0));

/**
 * Legt eine Lage ueber die bisherige - genau das, was frueher ein
 * weiterer Zeichenaufruf mit einem weiteren PNG gemacht hat.
 *
 * DAS IST DER PUNKT, AN DEM ICH ES ERST FALSCH HATTE:
 * Nur die FARBE zu mischen reicht nicht. Jede Lage macht die Flaeche
 * auch UNDURCHSICHTIGER. Die Fuellung ist bereits fast weiss (243), eine
 * weisse Lage aendert daran farblich fast nichts - sichtbar wird sie
 * allein dadurch, dass mehr vom Hintergrund verdeckt wird.
 *
 * Nachgerechnet gegen eine dunkle Szene: richtig gestapelt ergibt die
 * Lichtkante 65 Helligkeitsstufen Unterschied zur Flaeche, nur
 * farbgemischt waren es 5.
 */
void layer(inout vec3 c, inout float a, vec3 srcColor, float srcAlpha) {
    float na = srcAlpha + a * (1.0 - srcAlpha);
    c = (srcColor * srcAlpha + c * a * (1.0 - srcAlpha)) / max(na, 1e-5);
    a = na;
}

void main() {
    // Abstand zum Rand des abgerundeten Rechtecks.
    vec2 q = abs(vLocal) - vHalf + vRadius;
    float d = min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - vRadius;

    // Breite eines Bildpunkts, in denselben Einheiten wie d.
    // Die untere Schranke verhindert eine Division durch null, falls der
    // Treiber keine Ableitungen liefert.
    float aa = max(fwidth(d), 0.0001);

    float fill = 1.0 - smoothstep(-aa, aa, d);
    if (fill <= 0.001) {
        discard;
    }

    // Richtung der Kante. Faellt die Ableitung aus, wird von oben
    // beleuchtet - das ist der unauffaelligste Fehlerfall.
    vec2 g = vec2(dFdx(d), dFdy(d));
    vec2 n = (dot(g, g) > 1e-12) ? normalize(g) : vec2(0.0, -1.0);

    // ACHTUNG VORZEICHEN:
    // OpenGL zaehlt Bildschirmzeilen von UNTEN nach oben, Minecrafts
    // Oberflaeche von OBEN nach unten. dFdy liefert deshalb das
    // umgekehrte Vorzeichen zu dem, was man erwartet. Dreht man das hier
    // um, sitzt die Lichtkante unten und die Fase oben - gemessen hatte
    // ich oben -32 und unten +47 Helligkeitsstufen, also genau spiegel-
    // verkehrt.
    float up   = clamp( n.y, 0.0, 1.0);
    float down = clamp(-n.y, 0.0, 1.0);

    // Abstand nach innen, positiv.
    float inside = -d;

    float rim   = 1.0 - smoothstep(0.0, RIM_W,   inside);
    float bevel = 1.0 - smoothstep(0.0, BEVEL_W, inside);
    float inner = 1.0 - smoothstep(0.0, INNER_W, inside);

    // GLAS IST AM RAND DICHTER.
    // In der Mitte schaut man fast durch, zur Kante hin wird es milchig.
    // Ohne das wirkt die Flaeche wie gleichmaessig eingefaerbtes Milchglas -
    // der Unterschied zwischen Mitte und Rand ist das, was das Auge als
    // "Scheibe mit Dicke" liest.
    float edgeGain = 1.0 - smoothstep(0.0, INNER_W * 2.5, inside);

    // Gerichtetes Glanzlicht. Die hohe Potenz macht daraus einen kurzen
    // Bogen an der oberen linken Rundung statt eines Schimmers rundherum.
    float spec = pow(clamp(dot(n, LIGHT), 0.0, 1.0), 7.0);

    // Das Band der Linsenkante, zwischen Lichtkante und Fase.
    float lens = smoothstep(0.0, RIM_W, inside)
               * (1.0 - smoothstep(RIM_W, RIM_W + LENS_W, inside));

    vec3 col = vColor.rgb;
    float a  = min(1.0, vColor.a * (1.0 + edgeGain * EDGE_GAIN));

    // Reihenfolge wie frueher die vier Zeichenaufrufe:
    // Flaeche, Innenschatten, Fase, Lichtkante.
    layer(col, a, vec3(0.0), inner * INNER_A);
    layer(col, a, vec3(1.0), bevel * up   * BEVEL_L);
    layer(col, a, vec3(0.0), bevel * down * BEVEL_D);
    layer(col, a, vec3(1.0), lens * (0.3 + 0.7 * up) * LENS_A);
    layer(col, a, vec3(1.0), rim * (0.35 + 0.65 * up) * RIM_A);
    // Das Glanzlicht ZULETZT: Es liegt auf allem anderen, so wie eine
    // Spiegelung auf dem Glas liegt und nicht darin.
    layer(col, a, vec3(1.0), rim * spec * SPEC_A);

    fragColor = vec4(col, a * fill);
}
