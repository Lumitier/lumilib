#version 330

// LumiLib - Textur mit runden Ecken, Eckpunktteil.
//
// WOZU:
// Mod-Icons kommen aus den installierten Mods und bringen ihren eigenen
// Hintergrund mit - meist ein deckendes Quadrat. In einer Oberflaeche mit
// runden Flaechen liest sich das als aufgeklebter Kasten. Hier wird das
// Bild beim Zeichnen rund beschnitten.
//
// WOHER DIE ORTSANGABE KOMMT:
// UV0 traegt die Texturkoordinate von 0 bis 1 - also die Lage INNERHALB
// des Rechtecks. Daraus und aus der Groesse in UV2 laesst sich die
// oertliche Lage zurueckrechnen; ein eigener Kanal dafuer waere
// ueberfluessig.
//
// Packung in UV2 wie bei rounded.vsh:
//     x = Breite, y = (Radius << 10) | Hoehe

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

out vec2 vUV;
out vec4 vColor;
out vec2 vLocal;
out vec2 vHalf;
out float vRadius;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vUV = UV0;
    vColor = Color * ColorModulator;

    vec2 size = vec2(float(UV2.x), float(UV2.y & 1023));
    vHalf = size * 0.5;
    vLocal = (UV0 - 0.5) * size;
    vRadius = float((UV2.y >> 10) & 31);
}
