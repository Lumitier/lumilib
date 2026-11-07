package com.lumitier.lumilib.client.gpu;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.lumitier.lumilib.LumiLib;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;

import static com.lumitier.lumilib.LumiLib.MOD_ID;

/**
 * Text zeichnen - aus dem Abstandsfeld statt aus gerasterten Bildern.
 *
 * DAS IST DER UNTERSCHIED, DEN MAN SIEHT:
 * Minecraft rastert eine Schrift einmal in fester Groesse und skaliert
 * danach. Hier wird die Kante jedes Buchstabens in der Groesse neu
 * gerechnet, in der tatsaechlich gezeichnet wird.
 *
 * SCHNITTSTELLE:
 * drawTop setzt die SICHTBARE Oberkante eines Grossbuchstabens auf eine
 * Zeile - nicht die Schreiblinie. Genauso rechnet der Rest der
 * Oberflaeche, deshalb passen die Werte aus Ui unveraendert weiter.
 */
public final class SdfText {

	private SdfText() {}

	/** Reichweite des Abstandsfelds in Atlaspixeln - muss zum Erzeuger passen. */
	private static final float DISTANCE_RANGE = 12.0F;

	private static RenderPipeline pipeline;
	private static boolean broken;
	private static SdfFont regular;
	private static SdfFont bold;
	private static boolean fontsTried;

	private static RenderPipeline pipeline() {
		if (pipeline != null || broken) {
			return pipeline;
		}
		try {
			pipeline = RenderPipeline.builder()
				.withLocation(Identifier.of(MOD_ID, "pipeline/sdf_text"))
				.withVertexShader(Identifier.of(MOD_ID, "core/sdf_text"))
				.withFragmentShader(Identifier.of(MOD_ID, "core/sdf_text"))
				.withShaderDefine("DISTANCE_RANGE", DISTANCE_RANGE)
				.withSampler("Sampler0")
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR,
					VertexFormat.DrawMode.QUADS)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withDepthWrite(false)
				.withCull(false)
				.build();
		} catch (Throwable t) {
			broken = true;
			LumiLib.LOGGER.warn("[LumiLib] Distance-field font unavailable, "
				+ "falling back to Minecraft's font.", t);
		}
		return pipeline;
	}

	private static void loadFonts() {
		if (fontsTried) {
			return;
		}
		fontsTried = true;
		regular = SdfFont.load("regular");
		bold = SdfFont.load("bold");
	}

	/** Der passende Schnitt, oder null wenn nicht ladbar. */
	public static SdfFont font(boolean boldWeight) {
		loadFonts();
		return boldWeight ? bold : regular;
	}

	/** True, wenn ueber das Abstandsfeld gezeichnet werden kann. */
	public static boolean available() {
		loadFonts();
		return pipeline() != null && regular != null && bold != null;
	}

	/** Breite in Bildschirmpixeln, oder -1 wenn nicht verfuegbar. */
	public static float width(String s, float px, boolean boldWeight) {
		SdfFont f = font(boldWeight);
		return f == null ? -1.0F : f.width(s, px);
	}

	/**
	 * Zeichnet Text so, dass die SICHTBARE Oberkante auf top liegt.
	 *
	 * @return false, wenn der Weg nicht verfuegbar ist - dann muss der
	 *         Aufrufer mit Minecrafts Schrift zeichnen
	 */
	public static boolean drawTop(DrawContext ctx, String s, float x, float top,
	                              float px, int argb, boolean boldWeight) {
		if (s == null || s.isEmpty()) {
			return true;
		}
		RenderPipeline p = pipeline();
		SdfFont f = font(boldWeight);
		if (p == null || f == null) {
			return false;
		}
		AbstractTexture tex = MinecraftClient.getInstance()
			.getTextureManager().getTexture(f.texture);
		if (tex == null) {
			return false;
		}

		float sc = f.scale(px);
		// top ist die Oberkante eines Grossbuchstabens. Die Zeichen im
		// Atlas sind aber zur Oberkante des SCHNITTS vermasst - also
		// zurueckrechnen.
		float originY = top - f.capTopOffset(px);

		float[] verts = new float[s.length() * 16];
		int n = 0;
		float pen = x;
		char prev = 0;
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			SdfFont.Glyph g = f.glyph(c);
			if (g == null) {
				continue;
			}
			if (prev != 0) {
				pen += f.kern(prev, c) * sc;
			}
			if (g.w > 0 && g.h > 0) {
				float x0 = pen + g.bx * sc;
				float y0 = originY + g.by * sc;
				float x1 = x0 + g.w * sc;
				float y1 = y0 + g.h * sc;
				float u0 = (float) g.x / f.atlasW();
				float v0 = (float) g.y / f.atlasH();
				float u1 = (float) (g.x + g.w) / f.atlasW();
				float v1 = (float) (g.y + g.h) / f.atlasH();

				verts[n++] = x0; verts[n++] = y0; verts[n++] = u0; verts[n++] = v0;
				verts[n++] = x0; verts[n++] = y1; verts[n++] = u0; verts[n++] = v1;
				verts[n++] = x1; verts[n++] = y1; verts[n++] = u1; verts[n++] = v1;
				verts[n++] = x1; verts[n++] = y0; verts[n++] = u1; verts[n++] = v0;

				minX = Math.min(minX, x0); minY = Math.min(minY, y0);
				maxX = Math.max(maxX, x1); maxY = Math.max(maxY, y1);
			}
			pen += g.adv * sc;
			prev = c;
		}
		if (n == 0) {
			return true;
		}
		float[] used = new float[n];
		System.arraycopy(verts, 0, used, 0, n);

		var pose = ctx.getMatrices();
		ScreenRect box = new ScreenRect(
			(int) Math.floor(minX), (int) Math.floor(minY),
			(int) Math.ceil(maxX - minX) + 1, (int) Math.ceil(maxY - minY) + 1);

		ctx.state.addSimpleElement(new GlyphRunState(
			p,
			TextureSetup.of(tex.getGlTextureView(),
				RenderSystem.getSamplerCache().get(FilterMode.LINEAR)),
			new org.joml.Matrix3x2f(pose),
			// Schnittfenster mitgeben - siehe Sdf.clip.
			used, argb, Sdf.clip(), box.transformEachVertex(pose)));
		return true;
	}
}
