package com.lumitier.lumilib.client.gpu;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fc;

/**
 * Ein Bild mit runden Ecken als GUI-Element.
 *
 * Die Texturkoordinate laeuft von 0 bis 1 ueber das ganze Rechteck - der
 * Shader rechnet daraus die oertliche Lage fuer die Abstandsfunktion
 * zurueck. Groesse und Radius reisen wie bei RoundedRectState in UV2 mit,
 * weil Minecraft gleichartige Elemente zu einem Zeichenvorgang
 * zusammenfasst und Uniforms deshalb fuer alle gleich waeren.
 */
public record RoundedTexState(
	RenderPipeline pipeline,
	TextureSetup textureSetup,
	Matrix3x2fc pose,
	int x,
	int y,
	int w,
	int h,
	int radius,
	int argb,
	@Nullable ScreenRect scissorArea,
	ScreenRect bounds
) implements SimpleGuiElementRenderState {

	@Override
	public void setupVertices(VertexConsumer vc) {
		int rr = Math.min(Math.max(radius, 0), RoundedRectState.MAX_RADIUS);
		int hh = Math.min(Math.max(h, 0), RoundedRectState.MAX_HEIGHT);
		int packed = (rr << 10) | hh;

		emit(vc, x,     y,     0.0F, 0.0F, packed);
		emit(vc, x,     y + h, 0.0F, 1.0F, packed);
		emit(vc, x + w, y + h, 1.0F, 1.0F, packed);
		emit(vc, x + w, y,     1.0F, 0.0F, packed);
	}

	private void emit(VertexConsumer vc, int sx, int sy,
	                  float u, float v, int packed) {
		vc.vertex(pose, sx, sy)
			.texture(u, v)
			.color(argb)
			.light(w, packed);
	}
}
