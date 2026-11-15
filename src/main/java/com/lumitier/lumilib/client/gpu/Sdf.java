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

	/**
	 * Flaechenart - bestimmt die Staerken der Kanten.
	 *
	 * FLAT hat keine davon: keine Lichtkante, keine Fase, keinen
	 * Innenschatten, keinen Glanz. Uebrig bleibt eine gefuellte Flaeche
	 * mit runden Ecken und weicher Kante - genau das, was ein Schalter,
	 * ein Reglergriff oder ein Statuspunkt braucht. Glas gehoert dort
	 * nicht hin: eine Fase auf einem acht Pixel grossen Knopf ist kein
	 * Material, sondern Dreck.
	 */
	public enum Style { PANEL, CARD, FLAT }

	/**
	 * Die Staerken der Kantenbaender einer Flaechenart.
	 *
	 * Sie gehen als Konstanten in den Shader und gelten damit fuer die
	 * ganze Pipeline. Deshalb gibt es je Art eine Pipeline und hier
	 * einen Satz Werte dazu, statt sieben Abfragen im Aufbau.
	 */
	private record Bands(int rim, int bevelLight, int bevelDark, int inner,
	                     int spec, int lens, float gain) {}

	private static Bands bands(Style style) {
		return switch (style) {
			case PANEL -> new Bands(Theme.RIM, Theme.BEVEL_LIGHT, Theme.BEVEL_DARK,
				Theme.INNER, Theme.SPEC, Theme.LENS, Theme.DENS);
			case CARD -> new Bands(Theme.RIM_CARD, Theme.BEVEL_LIGHT_CARD, Theme.BEVEL_DARK_CARD,
				Theme.INNER_CARD, Theme.SPEC_CARD, Theme.LENS_CARD, Theme.DENS_CARD);
			case FLAT -> new Bands(0, 0, 0, 0, 0, 0, 0.0F);
		};
	}

	/** Die Glaspipeline - getrennt, weil sie als einzige einen Sampler hat. */
	private static RenderPipeline glassPipeline;
	private static boolean glassBroken;

	private static final Map<Style, RenderPipeline> PIPELINES = new EnumMap<>(Style.class);
	private static boolean broken;

	/**
	 * Die Palette hat gewechselt - gebaute Pipelines wegwerfen.
	 *
	 * NOETIG, WEIL DIE FARBEN IM SHADER STEHEN:
	 * Staerke der Lichtkante, der Fase, des Glanzes und das Tonband gehen
	 * als Konstanten in den Shader, einmal beim Bauen. Eine Pipeline, die
	 * mit den hellen Werten gebaut wurde, bleibt hell - sie liest Theme
	 * nie wieder. Ohne dieses Wegwerfen waere der Umschalter ein Wechsel,
	 * bei dem Schrift und Flaechen die Farbe tauschen und die Kanten nicht.
	 *
	 * WARUM WEGWERFEN UND NICHT ZWEI SAETZE VORHALTEN:
	 * Weil ein Wechsel selten ist und ein zweiter Satz dauerhaft Speicher
	 * auf der Grafikkarte kostet. Der naechste Aufruf baut neu, das merkt
	 * man einmal und danach nicht mehr.
	 *
	 * Die Marken broken und glassBroken werden MITZURUECKGESETZT: Scheitert
	 * ein Bau, soll die andere Palette es noch einmal versuchen duerfen -
	 * es kann ja gerade an den Werten gelegen haben.
	 */
	public static void paletteChanged() {
		PIPELINES.clear();
		glassPipeline = null;
		broken = false;
		glassBroken = false;
	}

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
			Bands b = bands(style);
			RenderPipeline built = RenderPipeline.builder()
				.withLocation(Identifier.of(MOD_ID,
					"pipeline/rounded_" + style.name().toLowerCase(java.util.Locale.ROOT)))
				.withVertexShader(Identifier.of(MOD_ID, "core/rounded"))
				.withFragmentShader(Identifier.of(MOD_ID, "core/rounded"))
				// Staerken der Baender als Konstanten in den Shader.
				.withShaderDefine("RIM_A", alpha(b.rim()))
				.withShaderDefine("BEVEL_L", alpha(b.bevelLight()))
				.withShaderDefine("BEVEL_D", alpha(b.bevelDark()))
				.withShaderDefine("INNER_A", alpha(b.inner()))
				// Glaskante - siehe Theme. SPEC und LENS sind Deckkraefte,
				// EDGE_GAIN ist ein Faktor und kommt schon als Zahl.
				.withShaderDefine("SPEC_A", alpha(b.spec()))
				.withShaderDefine("LENS_A", alpha(b.lens()))
				.withShaderDefine("EDGE_GAIN", b.gain())
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
