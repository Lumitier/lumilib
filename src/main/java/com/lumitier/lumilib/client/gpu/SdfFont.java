package com.lumitier.lumilib.client.gpu;

import com.google.gson.Gson;
import com.lumitier.lumilib.LumiLib;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Ein Zeichensatz als Abstandsfeld.
 *
 * Die Masse werden aus einer JSON-Datei gelesen, die der Erzeuger unter
 * tools/sdf_atlas.py geschrieben hat. Dort stehen fuer jedes Zeichen
 * Vorschub, Groesse, Versatz und die Lage im Atlas - alles in Einheiten
 * der Atlasgroesse (48 Pixel). Beim Zeichnen wird mit px/48 skaliert.
 *
 * WARUM VORGEBACKEN:
 * Den Atlas zur Laufzeit zu rechnen hiesse, ihn nicht pruefen zu koennen.
 * So wurde er vorher gemessen: Abweichung zur Originalschrift 8,8 von
 * 255, Tintenmenge auf 2 Prozent genau.
 *
 * ZUR UNTERSCHNEIDUNG:
 * Ohne sie laeuft Text 2 bis 3 Prozent zu breit, bei Paaren wie "AV"
 * acht Prozent. Im Atlas stehen nur die Paare mit spuerbarer Wirkung,
 * rund 3500 von 11881.
 */
public final class SdfFont {

	/** Ein Zeichen im Atlas. Alle Werte in Atlaseinheiten. */
	public static final class Glyph {
		public float adv;          // Vorschub bis zum naechsten Zeichen
		public int w, h;           // Groesse im Atlas
		public float bx, by;       // Versatz zur Schreiblinie
		public int x, y;           // Lage im Atlas
	}

	private static final class Data {
		String name;
		int size;
		float spread;
		int pad;                   // Rand um jedes Zeichen im Atlas
		int atlasW, atlasH;
		float ascent, descent;
		Map<String, Glyph> glyphs;
		Map<String, Map<String, Float>> kern;
	}

	public final Identifier texture;
	private final Data data;
	private final Map<Character, Glyph> byChar = new HashMap<>();
	private final float capTopOffset;

	private SdfFont(Data data, Identifier texture) {
		this.data = data;
		this.texture = texture;
		for (Map.Entry<String, Glyph> e : data.glyphs.entrySet()) {
			if (!e.getKey().isEmpty()) {
				byChar.put(e.getKey().charAt(0), e.getValue());
			}
		}
		// Oberkante eines Grossbuchstabens, gemessen am H. Damit laesst
		// sich Text auf eine SICHTBARE Oberkante setzen, statt auf eine
		// gedachte Schreiblinie - so rechnet der Rest der Oberflaeche.
		Glyph h = byChar.get('H');
		this.capTopOffset = h != null ? h.by : 0.0F;
	}

	/** Laedt einen Schnitt, oder null wenn etwas fehlt. */
	public static SdfFont load(String name) {
		Identifier meta = Identifier.of(LumiLib.MOD_ID, "sdf/" + name + ".json");
		try (InputStream in = MinecraftClient.getInstance()
				.getResourceManager().open(meta)) {
			Data d = new Gson().fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8), Data.class);
			if (d == null || d.glyphs == null || d.glyphs.isEmpty()) {
				return null;
			}
			// VOLLER PFAD samt textures/ und .png - so erwartet es
			// TextureManager.getTexture. Mit einem kurzen Namen findet
			// Minecraft nichts und liefert eine DECKENDE Ersatztextur:
			// der Shader liest dann ueberall "ganz innen" und malt
			// schwarze Kloetze statt Buchstaben.
			Identifier tex = Identifier.of(LumiLib.MOD_ID,
				"textures/sdf/" + name + ".png");
			if (MinecraftClient.getInstance().getResourceManager()
					.getResource(tex).isEmpty()) {
				LumiLib.LOGGER.warn("[LumiLib] Atlas image missing: " + tex);
				return null;
			}
			return new SdfFont(d, tex);
		} catch (Throwable t) {
			LumiLib.LOGGER.warn("[LumiLib] Font " + name + " could not be loaded.", t);
			return null;
		}
	}

	public int atlasW() { return data.atlasW; }
	public int atlasH() { return data.atlasH; }

	/** Massstab von Atlaseinheiten auf Bildschirmpixel. */
	public float scale(float px) { return px / data.size; }

	/** Versatz von der sichtbaren Oberkante zur Oberkante des Zeichensatzes. */
	public float capTopOffset(float px) { return capTopOffset * scale(px); }

	/**
	 * Rand des Abstandsfeldes, in Bildschirmpixeln.
	 *
	 * WOZU MAN DAS BRAUCHT:
	 * Jedes Zeichen liegt im Atlas mit einem Rand um die Tinte - ohne den
	 * waere das Abstandsfeld an der Kante abgeschnitten. Dadurch ist die
	 * Oberkante des KASTENS nicht die Oberkante des Buchstabens, sondern
	 * liegt um diesen Rand darueber. Bei 12,5 Pixeln sind es 2,1.
	 *
	 * Wer senkrecht mitten zentrieren will, muss das abziehen, sonst sitzt
	 * der Text genau um diesen Betrag zu hoch.
	 *
	 * Der Wert steht in der JSON. Aeltere Atlanten ohne das Feld fallen auf
	 * die Rechnung des Erzeugers zurueck: PAD = SPREAD + 2, siehe
	 * tools/sdf_atlas.py.
	 */
	public float inkTopOffset(float px) {
		float p = data.pad > 0 ? data.pad : data.spread + 2.0F;
		return p * scale(px);
	}

	/**
	 * Wie weit DIESE Zeichenkette unter die Schreiblinie reicht, in Pixeln.
	 *
	 * WARUM GEMESSEN UND NICHT AUFGEZAEHLT:
	 * Eine Liste wie "gjpqy" sieht vollstaendig aus, ist es aber nicht. In
	 * Geist reichen auch Q (0,8 Pixel bei Groesse 12,5), Klammern,
	 * Schraegstrich und Unterstrich nach unten, Umlautpunkte dagegen nicht.
	 * Gemessen wird am Atlas: Unterkante des Zeichens gegen die Unterkante
	 * des H, das auf der Schreiblinie steht. Damit stimmt es auch fuer
	 * Zeichen, an die ich nicht gedacht habe.
	 */
	public float descentOf(String s, float px) {
		Glyph h = byChar.get('H');
		if (h == null || s == null) {
			return 0.0F;
		}
		float baseline = h.by + h.h;
		float deepest = 0.0F;
		for (int i = 0; i < s.length(); i++) {
			Glyph g = byChar.get(s.charAt(i));
			if (g != null) {
				deepest = Math.max(deepest, g.by + g.h - baseline);
			}
		}
		return deepest * scale(px);
	}

	/** Versalhoehe bei dieser Groesse - Oberkante bis Schreiblinie. */
	public float capHeight(float px) {
		return (data.ascent - capTopOffset) * scale(px);
	}

	/** Unterlaenge bei dieser Groesse. */
	public float descent(float px) { return data.descent * scale(px); }

	public Glyph glyph(char c) { return byChar.get(c); }

	/** Zusaetzlicher Vorschub zwischen zwei Zeichen, in Atlaseinheiten. */
	public float kern(char a, char b) {
		if (data.kern == null) {
			return 0.0F;
		}
		Map<String, Float> row = data.kern.get(String.valueOf(a));
		if (row == null) {
			return 0.0F;
		}
		Float v = row.get(String.valueOf(b));
		return v == null ? 0.0F : v;
	}

	/** Breite einer Zeichenkette in Bildschirmpixeln. */
	public float width(String s, float px) {
		float sc = scale(px);
		float pen = 0.0F;
		char prev = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			Glyph g = byChar.get(c);
			if (g == null) {
				continue;
			}
			if (prev != 0) {
				pen += kern(prev, c) * sc;
			}
			pen += g.adv * sc;
			prev = c;
		}
		return pen;
	}
}
