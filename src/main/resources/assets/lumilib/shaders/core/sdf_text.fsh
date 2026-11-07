#version 330

// LumiLib - Text aus dem Abstandsfeld, Bildpunktteil.
//
// DER UNTERSCHIED ZU MINECRAFT:
// Minecraft rastert eine Schrift EINMAL in fester Groesse und skaliert
// danach das fertige Bild. Deshalb wirkt sie neben geglaetteten Kanten
// wie aufgeklebt.
//
// Hier steht im Atlas pro Bildpunkt kein Farbwert, sondern der ABSTAND
// zum Rand des Buchstabens. Die Kante wird in der Groesse neu berechnet,
// in der wirklich gezeichnet wird - dasselbe Verfahren wie bei den
// runden Ecken.
//
// screenPxRange rechnet um, wie viele Bildschirmpixel einer Einheit des
// Abstandsfelds entsprechen. fwidth(vUV) sagt, wie weit die
// Atlaskoordinate von einem Bildpunkt zum naechsten springt - daraus
// folgt der Massstab, ohne dass ihn jemand uebergeben muesste.

uniform sampler2D Sampler0;

in vec2 vUV;
in vec4 vColor;

out vec4 fragColor;

void main() {
    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / max(fwidth(vUV), vec2(1e-8));
    vec2 unitRange = vec2(DISTANCE_RANGE) / texSize;
    float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);

    // 0,5 ist genau die Kontur - so hat der Erzeuger es abgelegt.
    float d = texture(Sampler0, vUV).a - 0.5;
    float alpha = clamp(d * screenPxRange + 0.5, 0.0, 1.0);
    if (alpha <= 0.002) {
        discard;
    }
    fragColor = vec4(vColor.rgb, vColor.a * alpha);
}
