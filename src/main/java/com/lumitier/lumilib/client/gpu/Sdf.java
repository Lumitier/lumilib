package com.lumitier.lumilib.client.gpu;

import com.lumitier.lumilib.LumiLib;
import com.lumitier.lumilib.client.Theme;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

import static com.lumitier.lumilib.LumiLib.MOD_ID;

/**
 * Runde Flaechen, berechnet statt gezeichnet.
 *
 * EINSTIEGSPUNKT fuer alles, was frueher ein Neun-Scheiben-PNG war.
 *
 * WARUM ZWEI PIPELINES:
 * Die Staerken von Lichtkante, Fase und Innenschatten gehen als
 * Konstanten in den Shader - die gelten fuer die ganze Pipeline, nicht
 * pro Flaeche. Fenster und Karten haben aber unterschiedliche Staerken
 * (eine Karte liegt AUF dem Fenster und braucht weniger Kontrast, sonst
 * sticht sie heraus). Also eine Pipeline je Stil, beide mit denselben
 * Shadern.
 *
 * ZUM RUECKFALL:
 * Shader laufen auf der Grafikkarte, und Grafiktreiber sind
 * unterschiedlich gutmuetig. Schlaegt der Aufbau fehl, wird das EINMAL
 * vermerkt und ab da sofort false geliefert - der Aufrufer zeichnet dann
 * wie bisher mit Sprites weiter. Keine Ausnahme nach oben, kein Absturz,
 * kein Fehler pro Bild im Log.
 *
 * WARUM DIE PIPELINE ERST BEIM ERSTEN AUFRUF ENTSTEHT:
 * Zur Ladezeit der Klasse gibt es noch kein Grafikgeraet. Wer dort schon
 * baut, stuerzt beim Start ab.
 */
public final class Sdf {

	private Sdf() {}

	/** Flaechenart - bestimmt die Staerken der Kanten. */
	public enum Style { PANEL, CARD }

	/** Die Glaspipeline - getrennt, weil sie als einzige einen Sampler hat. */
	private static RenderPipeline glassPipeline;
	private static boolean glassBroken;

	private static final Map<Style, RenderPipeline> PIPELINES = new EnumMap<>(Style.class);
	private static boolean broken;

	/** Alphaanteil eines ARGB als 0..1 - so erwartet es GLSL. */
	private static float alpha(int argb) {
		return ((argb >>> 24) & 0xFF) / 255.0F;
	}

	private static RenderPipeline pipeline(Style style) {
		if (broken) {
			return null;
		}
		RenderPipeline cached = PIPELINES.get(style);
		if (cached != null) {
			return cached;
		}
		try {
			boolean card = style == Style.CARD;
			RenderPipeline built = RenderPipeline.builder()
				.withLocation(Identifier.of(MOD_ID,
					"pipeline/rounded_" + (card ? "card" : "panel")))
				.withVertexShader(Identifier.of(MOD_ID, "core/rounded"))
				.withFragmentShader(Identifier.of(MOD_ID, "core/rounded"))
				// Staerken der Baender als Konstanten in den Shader.
				.withShaderDefine("RIM_A",
					alpha(card ? Theme.RIM_CARD : Theme.RIM))
				.withShaderDefine("BEVEL_L",
					alpha(card ? Theme.BEVEL_LIGHT_CARD : Theme.BEVEL_LIGHT))
				.withShaderDefine("BEVEL_D",
					alpha(card ? Theme.BEVEL_DARK_CARD : Theme.BEVEL_DARK))
				.withShaderDefine("INNER_A",
					alpha(card ? Theme.INNER_CARD : Theme.INNER))
				// Glaskante - siehe Theme. SPEC und LENS sind Deckkraefte,
				// EDGE_GAIN ist ein Faktor und kommt schon als Zahl.
				.withShaderDefine("SPEC_A",
					alpha(card ? Theme.SPEC_CARD : Theme.SPEC))
				.withShaderDefine("LENS_A",
					alpha(card ? Theme.LENS_CARD : Theme.LENS))
				.withShaderDefine("EDGE_GAIN",
					card ? Theme.DENS_CARD : Theme.DENS)
				// MUSS gemeldet werden, sonst verweigert Minecraft die
				// Pipeline mit "unknown and unsupported uniform" und
				// zeichnet stillschweigend nichts. Die Namen muessen
				// exakt den Bloecken im Shader entsprechen.
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT,
					VertexFormat.DrawMode.QUADS)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withDepthWrite(false)
				.withCull(false)
				.build();
			PIPELINES.put(style, built);
			return built;
		} catch (Throwable t) {
			broken = true;
			com.lumitier.lumilib.LumiLib.LOGGER.warn(
				"[LumiLib] Shader-drawn rounded surfaces unavailable, "
				+ "falling back to sprites.", t);
			return null;
		}
	}

	// ------------------------------------------------------------------
	// SCHNITTFENSTER
	//
	// WARUM DAS SEIN MUSS: ctx.enableScissor wirkt nur auf Minecrafts
	// EIGENE Zeichenaufrufe. Die Flaechen und Buchstaben dieser Mod gehen
	// als eigene Elemente in die Sammelliste, und jedes davon bringt sein
	// Schnittfenster SELBST mit. Stand dort null, wurde gar nicht
	// geschnitten - dann laeuft eine gerollte Liste ueber den Fensterrand
	// hinaus und zeichnet ueber die Ueberschrift.
	//
	// Ein einzelner Wert statt eines Stapels reicht: Es gibt im Hub
	// nie zwei verschachtelte Fenster.
	// ------------------------------------------------------------------

	private static ScreenRect clip;

	/** Setzt das Schnittfenster fuer alle folgenden Aufrufe, null hebt es auf. */
	public static void clip(ScreenRect rect) {
		clip = rect;
	}

	/** Das aktuelle Schnittfenster, oder null. */
	public static ScreenRect clip() {
		return clip;
	}

	/**
	 * Das Schnittfenster in Minecrafts GUI-Pixeln.
	 *
	 * clip() wird in den Koordinaten gesetzt, in denen gezeichnet wird. Liegt
	 * eine Skalierung darueber (ScaledScreen), muessen die beiden nicht
	 * mehr uebereinstimmen - Minecrafts eigenes enableScissor rechnet die
	 * Lage auch um. Ohne Skalierung ist das Ergebnis dasselbe wie vorher.
	 */
	static ScreenRect clipFor(org.joml.Matrix3x2fc pose) {
		ScreenRect c = clip;
		return c == null ? null : c.transform(pose);
	}

	/** True, wenn der Shaderweg benutzbar ist. */
	public static boolean available() {
		return pipeline(Style.PANEL) != null;
	}

	/**
	 * Zeichnet ein Rechteck mit runden Ecken samt Kante, Fase und
	 * Innenschatten - alles aus einer Abstandsfunktion abgeleitet.
	 *
	 * @param radius 0 bis 31 Pixel, hoehere Werte werden gekappt
	 * @return false, wenn der Shaderweg nicht benutzbar ist - dann muss
	 *         der Aufrufer selbst zeichnen
	 */
	/**
	 * Glasflaeche mit Brechung.
	 *
	 * Setzt eine Bildschirmkopie voraus. Ohne die - oder wenn die
	 * Pipeline nicht baut - meldet die Methode false, und der Aufrufer
	 * zeichnet die gewoehnliche Flaeche. Glas ist eine Zugabe, kein
	 * Grund, dass der Hub leer bleibt.
	 *
	 * @param argb Toenung; die Deckkraft sagt, wie milchig das Glas ist
	 */
	public static boolean glass(DrawContext ctx, int x, int y, int w, int h,
	                            int radius, int argb) {
		if (w <= 0 || h <= 0) {
			return true;
		}
		if (h > RoundedRectState.MAX_HEIGHT || radius > RoundedRectState.MAX_RADIUS) {
			return false;
		}
		if (!Backdrop.ready()) {
			return false;
		}
		RenderPipeline p = glassPipeline();
		if (p == null) {
			return false;
		}
		try {
			// var, nicht Matrix3x2fc: Die Klasse ist hier nicht
			// importiert, und die anderen Zeichenwege machen es ebenso.
			var pose = ctx.getMatrices();
			ctx.state.addSimpleElement(new GlassState(p,
				TextureSetup.of(Backdrop.view(),
					RenderSystem.getSamplerCache().get(FilterMode.LINEAR)),
				// KOPIE, nicht der Stapel selbst: Gezeichnet wird erst am
				// Ende des Bildes, dann ist der Stapel laengst wieder
				// zurueckgesetzt. Ohne Skalierung fiel das nie auf - mit
				// ScaledScreen sass das Glas auf GUI 3 um 50 % daneben.
				new org.joml.Matrix3x2f(pose), x, y, w, h, radius, argb,
				clipFor(pose), new ScreenRect(x, y, w, h).transformEachVertex(pose)));
			return true;
		} catch (Throwable t) {
			LumiLib.LOGGER.warn("[LumiLib] Glass surface failed.", t);
			glassBroken = true;
			return false;
		}
	}

	private static RenderPipeline glassPipeline() {
		if (glassBroken) {
			return null;
		}
		if (glassPipeline != null) {
			return glassPipeline;
		}
		try {
			glassPipeline = RenderPipeline.builder()
				.withLocation(Identifier.of(MOD_ID, "pipeline/glass"))
				.withVertexShader(Identifier.of(MOD_ID, "core/glass"))
				.withFragmentShader(Identifier.of(MOD_ID, "core/glass"))
				.withShaderDefine("RIM_A", alpha(Theme.RIM))
				.withShaderDefine("BEVEL_L", alpha(Theme.BEVEL_LIGHT))
				.withShaderDefine("BEVEL_D", alpha(Theme.BEVEL_DARK))
				.withShaderDefine("LENS_A", alpha(Theme.LENS))
				.withShaderDefine("SPEC_A", alpha(Theme.SPEC))
				// In Bildschirmpunkten, nicht in logischen - der Shader
				// rechnet in der Aufloesung der Kopie.
				.withShaderDefine("REFRACT_PX", Theme.REFRACT)
				.withShaderDefine("THICK_PX", Theme.THICK)
				.withShaderDefine("BLUR_PX", Theme.BLUR)
				.withShaderDefine("DISPERSE", Theme.DISPERSE)
				.withShaderDefine("FRESNEL_PX", Theme.FRESNEL)
				.withShaderDefine("FRESNEL_HARD", Theme.FRESNEL_H)
				.withShaderDefine("FRESNEL_A", alpha(Theme.FRESNEL_A))
				.withShaderDefine("TONE_FLOOR", Theme.TONE_FLOOR)
				.withShaderDefine("TONE_CEIL", Theme.TONE_CEIL)
				.withShaderDefine("SATURATION", Theme.SATURATION)
				.withSampler("Sampler0")
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT,
					VertexFormat.DrawMode.QUADS)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withDepthWrite(false)
				.withCull(false)
				.build();
			return glassPipeline;
		} catch (Throwable t) {
			LumiLib.LOGGER.warn("[LumiLib] Glass pipeline not built - "
				+ "surfaces stay unrefracted.", t);
			glassBroken = true;
			return null;
		}
	}

	public static boolean roundedRect(DrawContext ctx, Style style, int x, int y,
	                                  int w, int h, int radius, int argb) {
		if (w <= 0 || h <= 0) {
			return true;                       // nichts zu tun ist kein Fehlschlag
		}
		if (h > RoundedRectState.MAX_HEIGHT) {
			return false;                      // passt nicht in die Packung
		}
		RenderPipeline p = pipeline(style);
		if (p == null) {
			return false;
		}
		// Matrix des DrawContext mitnehmen, nicht die Einheitsmatrix:
		// sonst ignoriert die Flaeche jede Verschiebung des Aufrufers.
		var pose = ctx.getMatrices();
		ctx.state.addSimpleElement(new RoundedRectState(
			p, new org.joml.Matrix3x2f(pose), x, y, w, h, radius, argb,
			clipFor(pose), new ScreenRect(x, y, w, h).transformEachVertex(pose)));
		return true;
	}

	// ------------------------------------------------------------------
	// Bilder mit runden Ecken
	//
	// Mod-Icons kommen aus den installierten Mods und bringen ihren
	// eigenen deckenden Hintergrund mit. Roh gezeichnet sind das
	// aufgeklebte Quadrate in einer Oberflaeche voller runder Flaechen.
	// Hier wird beim Zeichnen rund beschnitten - mit derselben
	// Abstandsfunktion wie die Flaechen, nur als Maske.
	// ------------------------------------------------------------------

	private static RenderPipeline texPipeline;
	private static boolean texBroken;

	private static RenderPipeline texPipeline() {
		if (texPipeline != null || texBroken) {
			return texPipeline;
		}
		try {
			texPipeline = RenderPipeline.builder()
				.withLocation(Identifier.of(MOD_ID, "pipeline/rounded_tex"))
				.withVertexShader(Identifier.of(MOD_ID, "core/rounded_tex"))
				.withFragmentShader(Identifier.of(MOD_ID, "core/rounded_tex"))
				.withSampler("Sampler0")
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT,
					VertexFormat.DrawMode.QUADS)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withDepthWrite(false)
				.withCull(false)
				.build();
		} catch (Throwable t) {
			texBroken = true;
			com.lumitier.lumilib.LumiLib.LOGGER.warn(
				"[LumiLib] Rounded image corners unavailable, drawing raw.", t);
		}
		return texPipeline;
	}

	/**
	 * Zeichnet eine Textur mit runden Ecken.
	 *
	 * @return false, wenn der Weg nicht geht - dann muss der Aufrufer die
	 *         Textur selbst zeichnen, dann eben eckig
	 */
	public static boolean roundedTexture(DrawContext ctx, Identifier texture,
	                                     int x, int y, int w, int h,
	                                     int radius, int argb) {
		if (w <= 0 || h <= 0) {
			return true;
		}
		if (h > RoundedRectState.MAX_HEIGHT) {
			return false;
		}
		RenderPipeline p = texPipeline();
		if (p == null) {
			return false;
		}
		AbstractTexture tex = MinecraftClient.getInstance()
			.getTextureManager().getTexture(texture);
		if (tex == null) {
			return false;
		}
		var pose = ctx.getMatrices();
		ctx.state.addSimpleElement(new RoundedTexState(
			p,
			TextureSetup.of(tex.getGlTextureView(),
				RenderSystem.getSamplerCache().get(FilterMode.LINEAR)),
			new org.joml.Matrix3x2f(pose), x, y, w, h, radius, argb,
			clipFor(pose), new ScreenRect(x, y, w, h).transformEachVertex(pose)));
		return true;
	}
}
