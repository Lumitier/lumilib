package com.lumitier.lumilib.client.gpu;

import com.google.gson.Gson;
import com.lumitier.lumilib.LumiLib;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kleine Zeichen - aus dem Abstandsfeld statt aus einem Pixelbild.
 *
 * DAS PROBLEM, DAS DAS LOEST:
 * Die Zeichen lagen als 32 Pixel grosse PNGs im Sprite-Atlas. Gezeichnet
 * werden sie mit 9 bis 16 Pixeln, bei GUI-Skalierung 3 oder 4 sind das
 * 27 bis 64 echte. Ein festes Bild muss dabei gedehnt oder gestaucht
 * werden, und genau das sah man: stufige Striche in den Knoepfen,
 * waehrend die Flaechen daneben schon weiche Kanten hatten.
 *
 * Hier liegt im Atlas der Abstand zum Rand der Form, und die Kante
 * entsteht in der Groesse, in der wirklich gezeichnet wird. Derselbe
 * Weg wie bei der Schrift - es ist sogar dieselbe Pipeline.
 *
 * ZUR GROESSE:
 * Eine Zelle im Atlas ist breiter als das Zeichen darin: ringsum liegt
 * Rand, damit das Abstandsfeld nicht an der Zellkante abreisst. Die
 * angefragte Groesse meint das ZEICHEN, nicht die Zelle - deshalb wird
 * das Viereck um cell/inner groesser gezeichnet und zentriert. Ohne das
 * waere jedes Zeichen rund ein Fuenftel zu klein, und zwar genau so
 * viel kleiner als die alten PNGs, die den Rand nicht hatten.
 *
 * ZUM RUECKFALL:
 * Fehlt die Datei oder der Shader, liefert draw false. Der Aufrufer
 * zeichnet dann wie bisher das Sprite. Beides bleibt deshalb im Jar.
 */
public final class SdfIcons {

	private SdfIcons() {}

	private static final class Cell {
		int x, y, w, h;
	}

	private static final class Data {
		int cell;
		int inner;
		int atlasW, atlasH;
		Map<String, Cell> icons;
	}

	private static final Identifier TEXTURE =
		Identifier.of(LumiLib.MOD_ID, "textures/sdf/icons.png");

	private static Data data;
	private static boolean tried;

	private static Data data() {
		if (tried) {
			return data;
		}
		tried = true;
		Identifier meta = Identifier.of(LumiLib.MOD_ID, "sdf/icons.json");
		try (InputStream in = MinecraftClient.getInstance()
				.getResourceManager().open(meta)) {
			Data d = new Gson().fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8), Data.class);
			if (d == null || d.icons == null || d.icons.isEmpty()
				|| d.cell <= 0 || d.inner <= 0) {
				return null;
			}
			// VOLLER PFAD samt textures/ und .png: mit einem kurzen Namen
			// liefert Minecraft eine DECKENDE Ersatztextur, und der Shader
			// liest darin ueberall "ganz innen" - das waeren schwarze
			// Kloetze statt Zeichen.
			if (MinecraftClient.getInstance().getResourceManager()
					.getResource(TEXTURE).isEmpty()) {
				LumiLib.LOGGER.warn("[LumiLib] Icon atlas missing: " + TEXTURE);
				return null;
			}
			data = d;
		} catch (Throwable t) {
			LumiLib.LOGGER.warn("[LumiLib] Icon atlas could not be loaded.", t);
		}
		return data;
	}

	/** Kennt der Atlas dieses Zeichen? */
	public static boolean has(String name) {
		Data d = data();
		return d != null && d.icons.containsKey(name);
	}

	/**
	 * Zeichnet ein Zeichen, mittig im Quadrat (x, y, size, size).
	 *
	 * @param name Name wie in tools/sdf_icons.py, etwa "search"
	 * @return false, wenn der Weg nicht verfuegbar ist - dann muss der
	 *         Aufrufer das Sprite zeichnen
	 */
	public static boolean draw(DrawContext ctx, String name,
	                           float x, float y, float size, int argb) {
		if (size <= 0.0F) {
			return true;
		}
		Data d = data();
		if (d == null) {
			return false;
		}
		Cell c = d.icons.get(name);
		if (c == null) {
			return false;
		}
		RenderPipeline p = SdfText.sharedPipeline();
		if (p == null) {
			return false;
		}
		AbstractTexture tex = MinecraftClient.getInstance()
			.getTextureManager().getTexture(TEXTURE);
		if (tex == null) {
			return false;
		}

		// Zelle ist groesser als das Zeichen - siehe Klassenkommentar.
		float box = size * (float) d.cell / (float) d.inner;
		float off = (box - size) / 2.0F;
		float x0 = x - off;
		float y0 = y - off;
		float x1 = x0 + box;
		float y1 = y0 + box;

		float u0 = (float) c.x / d.atlasW;
		float v0 = (float) c.y / d.atlasH;
		float u1 = (float) (c.x + c.w) / d.atlasW;
		float v1 = (float) (c.y + c.h) / d.atlasH;

		float[] verts = {
			x0, y0, u0, v0,
			x0, y1, u0, v1,
			x1, y1, u1, v1,
			x1, y0, u1, v0,
		};

		var pose = ctx.getMatrices();
		ScreenRect bounds = new ScreenRect(
			(int) Math.floor(x0), (int) Math.floor(y0),
			(int) Math.ceil(box) + 1, (int) Math.ceil(box) + 1);

		ctx.state.addSimpleElement(new GlyphRunState(
			p,
			TextureSetup.of(tex.getGlTextureView(),
				RenderSystem.getSamplerCache().get(FilterMode.LINEAR)),
			new org.joml.Matrix3x2f(pose),
			verts, argb, Sdf.clipFor(pose), bounds.transformEachVertex(pose)));
		return true;
	}
}
