package com.lumitier.lumilib.client.gpu;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fc;

/**
 * Eine Glasflaeche mit Brechung als GUI-Element.
 *
 * Wie RoundedRectState, mit einem Unterschied: Es bringt die
 * Bildschirmkopie als Textur mit. Nur deshalb kann der Shader
 * nachsehen, was hinter der Flaeche liegt.
 *
 * ECKPUNKTE IDENTISCH zu RoundedRectState - dieselbe Packung, derselbe
 * Vertexshader-Aufbau. Wer eines von beiden aendert, muss das andere
 * mitaendern; sie teilen sich die Konstanten MAX_RADIUS und MAX_HEIGHT.
 */
public record GlassState(
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

		float halfW = w * 0.5F;
		float halfH = h * 0.5F;

		emit(vc, x,     y,     -halfW, -halfH, packed);
		emit(vc, x,     y + h, -halfW,  halfH, packed);
		emit(vc, x + w, y + h,  halfW,  halfH, packed);
		emit(vc, x + w, y,      halfW, -halfH, packed);
	}

	private void emit(VertexConsumer vc, int sx, int sy,
	                  float lx, float ly, int packed) {
		vc.vertex(pose, sx, sy)
			.texture(lx, ly)
			.color(argb)
			.light(w, packed);
	}
}
