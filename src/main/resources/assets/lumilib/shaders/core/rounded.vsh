#version 330

// LumiLib - runde Flaeche, Eckpunktteil.
//
// WOZU DAS GANZE:
// Eine runde Ecke aus einem PNG ist ein Bild. Wird die Flaeche groesser,
// wird das Bild gedehnt - und genau das sieht man. Hier wird die Ecke
// stattdessen fuer JEDEN Bildpunkt neu berechnet. Dadurch ist sie bei
// jeder Groesse gleich sauber.
//
// WARUM DIE DATEN AN DEN ECKPUNKTEN HAENGEN:
// Minecraft fasst gleichartige GUI-Elemente zu EINEM Zeichenvorgang
// zusammen. Groesse und Radius koennen deshalb nicht als Uniform kommen -
// die waeren fuer alle Flaechen des Stapels gleich. Also reisen sie an
// jedem Eckpunkt mit.
//
// Zur Verfuegung stehen laut Jar:
//     UV0  zwei Gleitkommazahlen  -> lokale Lage im Rechteck, in Pixeln
//     UV2  zwei vorzeichenbehaftete Kurzzahlen (max 32767)
//     NORMAL waere nur ein Byte je Kanal - zu grob, daher ungenutzt
//
// Packung in UV2:
//     x = Breite in Pixeln
//     y = (Radius << 10) | Hoehe       Radius 0..31, Hoehe 0..1023
//         groesster Wert 31*1024+1023 = 32767, passt genau hinein

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

out vec4 vColor;
out vec2 vLocal;
out vec2 vHalf;
out float vRadius;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vColor = Color * ColorModulator;
    vLocal = UV0;

    // Halbe Kantenlaengen. Uebergeben wird die VOLLE Groesse, halbiert
    // wird hier - sonst gingen ungerade Breiten verloren.
    vHalf = vec2(float(UV2.x), float(UV2.y & 1023)) * 0.5;

    vRadius = float((UV2.y >> 10) & 31);
}
