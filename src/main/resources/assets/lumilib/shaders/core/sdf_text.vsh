#version 330

// LumiLib - Text aus dem Abstandsfeld, Eckpunktteil.
//
// Schlicht: Lage durchreichen, Atlaskoordinate durchreichen, Farbe
// durchreichen. Die ganze Arbeit passiert im Bildpunktteil.

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

out vec2 vUV;
out vec4 vColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vUV = UV0;
    vColor = Color * ColorModulator;
}
