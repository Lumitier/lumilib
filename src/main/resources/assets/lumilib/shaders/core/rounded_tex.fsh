#version 330

// LumiLib - Textur mit runden Ecken, Bildpunktteil.
//
// Dieselbe Abstandsfunktion wie bei den Flaechen, hier aber nur als
// Maske: das Bild wird gezeichnet, die Ecken werden weggerechnet. Die
// Kante bleibt dadurch bei jeder Groesse gleich weich - ein vorab rund
// gemaltes PNG waere bei 13 Pixeln eine Treppe.

uniform sampler2D Sampler0;

in vec2 vUV;
in vec4 vColor;
in vec2 vLocal;
in vec2 vHalf;
in float vRadius;

out vec4 fragColor;

void main() {
    vec2 q = abs(vLocal) - vHalf + vRadius;
    float d = min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - vRadius;
    float aa = max(fwidth(d), 0.0001);
    float mask = 1.0 - smoothstep(-aa, aa, d);

    vec4 tex = texture(Sampler0, vUV);
    float a = tex.a * vColor.a * mask;
    if (a <= 0.002) {
        discard;
    }
    fragColor = vec4(tex.rgb * vColor.rgb, a);
}
