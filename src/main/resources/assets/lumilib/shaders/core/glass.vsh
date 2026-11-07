#version 330

// LumiLib - Glas mit Brechung, Eckpunktteil.
//
// Gleiche Packung wie rounded.vsh - siehe dort. Hier kommt nur eines
// dazu: die Lage des Eckpunkts auf dem Bildschirm. Der Bildpunktteil
// braucht sie, um in der Bildschirmkopie nachzusehen, was hinter der
// Flaeche liegt.

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
    vHalf = vec2(float(UV2.x), float(UV2.y & 1023)) * 0.5;
    vRadius = float((UV2.y >> 10) & 31);
}
