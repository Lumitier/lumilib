package com.lumitier.lumilib.client.gpu;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fc;

/**
 * Eine ganze Zeichenkette als EIN GUI-Element.
 *
 * WARUM NICHT EIN ELEMENT JE ZEICHEN:
 * Minecraft fasst gleichartige Elemente zwar zusammen, aber jedes
 * Element kostet ein Objekt und einen Eintrag in der Liste - pro Bild.
 * Bei einer Oberflaeche voller Beschriftungen waeren das schnell
 * Tausende. Hier wird die ganze Zeile in einem Zug ausgegeben: die
 * Eckpunkte stehen schon fertig im Feld, setupVertices schiebt sie nur
 * noch hinaus.
 *
 * Die Eckpunkte werden beim Erzeugen gerechnet, nicht beim Zeichnen.
 * Das ist Absicht: setupVertices laeuft im Zeichendurchgang, und dort
 * gehoert keine Rechnerei hin.
 */
public record GlyphRunState(
	RenderPipeline pipeline,
	TextureSetup textureSetup,
	Matrix3x2fc pose,
	float[] verts,          // je Eckpunkt: x, y, u, v
	int argb,
	@Nullable ScreenRect scissorArea,
	ScreenRect bounds
) implements SimpleGuiElementRenderState {

	@Override
	public void setupVertices(VertexConsumer vc) {
		for (int i = 0; i < verts.length; i += 4) {
			vc.vertex(pose, verts[i], verts[i + 1])
				.texture(verts[i + 2], verts[i + 3])
				.color(argb);
		}
	}
}
