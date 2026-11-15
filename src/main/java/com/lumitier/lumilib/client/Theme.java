package com.lumitier.lumilib.client;

/**
 * Farben und Masse des Hubs - zwei Paletten, umschaltbar.
 *
 * Format durchgehend ARGB als int (0xAARRGGBB).
 *
 * WOHER DIE WERTE STAMMEN:
 * Aus OneConfig selbst, Thema "Polyfrosted Dark" - abgelesen im
 * Quelltext, nicht aus einem Bildschirmfoto geschaetzt:
 *   modules/internal/.../internal/ui/themes/Impls.kt  (val PolyfrostedDark)
 *   modules/internal/.../internal/ui/themes/Structs.kt (controlTrackColor)
 *   modules/internal/.../oneconfig/internal/ThemeConfig.java (accentColor)
 *
 * Deren Benennung und was hier daraus wurde:
 *   pageBackground      0xC411171C -> PANEL
 *   sidebarBackground   0xB3151C22 -> SIDEBAR
 *   chipBackground      0xB2232D32 -> CARD_HOVER, ROW_SELECTED
 *   modCardBackground   0x59232D32 -> CARD, ROW_HOVER
 *   componentBackground 0xFF1A2229 -> FIELD
 *   borderColor         0x1AFFFFFF -> RIM, DIVIDER, FIELD_EDGE
 *   textColor           0xFFD5DBFF -> TEXT, TEXT_MUTED
 *   textColorSecondary  0xFF757883 -> TEXT_FAINT, NEUTRAL
 *   controlTrackColor   0xFF74777F -> TOGGLE_OFF
 *   controlThumbColor   0xFFFFFFFF -> TOGGLE_KNOB
 *   accentColor         0xFF2B4BFF -> TOGGLE_ON
 *
 * ZWEI ABWEICHUNGEN, UND WARUM:
 *
 * 1. ACCENT bleibt das warme Gold 0xFFE8A33D. OneConfig faehrt dort
 *    0xFFFFD700, aber das ist deren Sternfarbe, nicht deren Akzent -
 *    und unser Gold steht so in jedem Mod-Logo. Eine Oberflaeche, deren
 *    Akzent nicht zu ihren eigenen Symbolen passt, sieht zusammen-
 *    gewuerfelt aus.
 *
 * 2. WARN und OK gibt es bei OneConfig in diesen Werten nicht. Beide
 *    sind so gewaehlt, dass sie auf dem kuehlen Blaugrau stehen,
 *    ohne zu leuchten.
 *
 * WAS AN DIESER PALETTE ANDERS IST ALS AN JEDER SELBSTGEBAUTEN:
 * Sie ist nicht neutral grau, sondern KUEHL blaugruen - 17,23,28 statt
 * 28,28,28. Das faellt einzeln nicht auf, aber gegen die weisse Schrift
 * (die bei denen 213,219,255 ist, also leicht blaeulich, kein Weiss)
 * ergibt es einen ruhigen Gesamteindruck statt eines Kontrastes aus
 * Schwarz und Weiss. Genau das war an unserem warmen Grau falsch.
 *
 * ZWEI PALETTEN:
 * DARK ist der Satz unten aus OneConfig, LIGHT das helle Glas, mit dem
 * LumiLib angefangen hat. Umgeschaltet wird ueber apply(Variant); was
 * dabei sonst noch passieren muss, steht dort.
 *
 * Die Masse stehen hier nur, wo sie absolut sein duerfen. Fenster- und
 * Spaltenbreiten werden im Screen aus der Bildschirmgroesse BERECHNET.
 */
public final class Theme {

	private Theme() {}


	/**
	 * Welche Palette gerade gilt.
	 *
	 * DARK  die Palette aus OneConfigs "Polyfrosted Dark"
	 * LIGHT das helle Glas, mit dem LumiLib angefangen hat
	 */
	public enum Variant { DARK, LIGHT }

	private static Variant variant = Variant.DARK;

	public static Variant variant() {
		return variant;
	}

	/**
	 * Palette wechseln.
	 *
	 * WARUM DIE WERTE NICHT MEHR final SIND:
	 * Sie waren Konstanten, und als Konstanten waren sie richtig - es gab
	 * ja nur eine Palette. Mit zwei geht das nicht mehr: Der Compiler
	 * setzt den Wert einer Konstanten an jeder Verwendungsstelle direkt
	 * ein, ein Wechsel zur Laufzeit kaeme also nie an.
	 *
	 * WAS DABEI SONST NOCH PASSIEREN MUSS:
	 * Ein Teil dieser Werte geht als Konstante IN DEN SHADER, einmal beim
	 * Bauen der Pipeline. Eine Pipeline, die mit den hellen Werten gebaut
	 * wurde, bleibt hell - egal was hier steht. Deshalb wirft Sdf seine
	 * gebauten Pipelines weg, wenn die Palette wechselt. Das kostet beim
	 * Umschalten einen Moment und danach nichts mehr.
	 *
	 * @return true, wenn sich wirklich etwas geaendert hat
	 */
	public static boolean apply(Variant next) {
		if (next == null || next == variant) {
			return false;
		}
		variant = next;
		switch (next) {
			case DARK -> dark();
			case LIGHT -> light();
		}
		com.lumitier.lumilib.client.gpu.Sdf.paletteChanged();
		return true;
	}


	// --- Werte, von apply gesetzt --------------------------------------

	public static int SCRIM;
	public static int PANEL;
	public static int SIDEBAR;
	public static int CARD;
	public static int CARD_HOVER;
	public static int ROW_HOVER;
	public static int ROW_SELECTED;
	public static int RIM;
	public static int RIM_CARD;
	public static int BEVEL_LIGHT;
	public static int BEVEL_DARK;
	public static int BEVEL_LIGHT_CARD;
	public static int BEVEL_DARK_CARD;
	public static int INNER;
	public static int INNER_CARD;
	public static int GLARE;
	public static int GLARE_CARD;
	public static int SHADOW;
	public static int SHADOW_CARD;
	public static int FIELD;
	public static int FIELD_HOVER;
	public static int FIELD_FOCUS;
	public static int FIELD_EDGE;
	public static int SPEC;
	public static int SPEC_CARD;
	public static int LENS;
	public static int LENS_CARD;
	public static float REFRACT;
	public static float THICK;
	public static float BLUR;
	public static float DISPERSE;
	public static float FRESNEL;
	public static float FRESNEL_H;
	public static int FRESNEL_A;
	public static int GLASS_TINT;
	public static float TONE_FLOOR;
	public static float TONE_CEIL;
	public static float SATURATION;
	public static float DENS;
	public static float DENS_CARD;
	public static int EDGE_STRONG;
	public static int EDGE_LIGHT;
	public static int DIVIDER;
	public static int ICON_BG;
	public static int WARN;
	public static int TOGGLE_ON;
	public static int TOGGLE_OFF;
	public static int TOGGLE_KNOB;
	public static int TEXT;
	public static int TEXT_MUTED;
	public static int TEXT_FAINT;
	public static int ACCENT;
	public static int OK;
	public static int NEUTRAL;

	static {
		dark();
	}


	/** Dunkel - Werte aus OneConfigs "Polyfrosted Dark". */
	private static void dark() {
		SCRIM = 0x8A000000;
		PANEL = 0xC411171C;
		SIDEBAR = 0xB3151C22;
		CARD = 0x59232D32;
		CARD_HOVER = 0xB2232D32;
		ROW_HOVER = 0x59232D32;
		ROW_SELECTED = 0xB2232D32;
		RIM = 0x1AFFFFFF;
		RIM_CARD = 0x14FFFFFF;
		BEVEL_LIGHT = 0x1AFFFFFF;
		BEVEL_DARK = 0x42000000;
		BEVEL_LIGHT_CARD = 0x12FFFFFF;
		BEVEL_DARK_CARD = 0x2E000000;
		INNER = 0x2E000000;
		INNER_CARD = 0x1A000000;
		GLARE = 0x00FFFFFF;
		GLARE_CARD = 0x00FFFFFF;
		SHADOW = 0x73000000;
		SHADOW_CARD = 0x3D000000;
		FIELD = 0xFF1A2229;
		FIELD_HOVER = 0xFF232D32;
		FIELD_FOCUS = 0xFF2B3740;
		FIELD_EDGE = 0x1AFFFFFF;
		SPEC = 0x24FFFFFF;
		SPEC_CARD = 0x14FFFFFF;
		LENS = 0x12FFFFFF;
		LENS_CARD = 0x0AFFFFFF;
		REFRACT = 44.0F;
		THICK = 40.0F;
		BLUR = 11.0F;
		DISPERSE = 0.05F;
		FRESNEL = 16.0F;
		FRESNEL_H = 4.0F;
		FRESNEL_A = 0x2EFFFFFF;
		GLASS_TINT = 0xA511171C;
		TONE_FLOOR = 0.03F;
		TONE_CEIL = 0.22F;
		SATURATION = 0.30F;
		DENS = 0.30F;
		DENS_CARD = 0.15F;
		EDGE_STRONG = 0x66000000;
		EDGE_LIGHT = 0x1AFFFFFF;
		DIVIDER = 0x1AFFFFFF;
		ICON_BG = 0xFF232D32;
		WARN = 0xFFFF6B61;
		TOGGLE_ON = 0xFF2B4BFF;
		TOGGLE_OFF = 0xFF74777F;
		TOGGLE_KNOB = 0xFFFFFFFF;
		TEXT = 0xFFD5DBFF;
		TEXT_MUTED = 0xFFD5DBFF;
		TEXT_FAINT = 0xFF757883;
		ACCENT = 0xFFE8A33D;
		OK = 0xFF4ED98A;
		NEUTRAL = 0xFF757883;
	}


	/**
	 * Hell - das Glas, mit dem LumiLib angefangen hat.
	 *
	 * Die Werte sind die ORIGINALEN, nicht nachgebaute: gezogen aus
	 * lumilib-1.5.3-sources.jar, also dem letzten Stand vor dem Umbau auf
	 * dunkel. Sie waren seinerzeit aus einem Referenzbild pixelweise
	 * gemessen - Sidebar 173 unter Fenster 188 unter Karte 203, Durchsicht
	 * 18 Prozent, das Tonband 0,30 bis 0,80 ueber fuenf Szenen von Hoehle
	 * bis Schnee nachgerechnet. Nichts davon haette sich aus dem dunklen
	 * Satz zurueckrechnen lassen.
	 */
	private static void light() {
		SCRIM = 0x6F000000;
		PANEL = 0xAFF3EEEB;
		SIDEBAR = 0x3332261E;
		CARD = 0x5CFFFFFF;
		CARD_HOVER = 0x8CFFFFFF;
		ROW_HOVER = 0x3DFFFFFF;
		ROW_SELECTED = 0x7AFFFFFF;
		RIM = 0x94FFFFFF;
		RIM_CARD = 0x66FFFFFF;
		BEVEL_LIGHT = 0x80FFFFFF;
		BEVEL_DARK = 0x38000000;
		BEVEL_LIGHT_CARD = 0x4DFFFFFF;
		BEVEL_DARK_CARD = 0x24000000;
		INNER = 0x14000000;
		INNER_CARD = 0x0D000000;
		GLARE = 0x00FFFFFF;
		GLARE_CARD = 0x00FFFFFF;
		SHADOW = 0x47000000;
		SHADOW_CARD = 0x24000000;
		FIELD = 0x3DFFFFFF;
		FIELD_HOVER = 0x52FFFFFF;
		FIELD_FOCUS = 0x70FFFFFF;
		FIELD_EDGE = 0x1FFFFFFF;
		SPEC = 0xD9FFFFFF;
		SPEC_CARD = 0x8AFFFFFF;
		LENS = 0x4DFFFFFF;
		LENS_CARD = 0x2EFFFFFF;
		REFRACT = 44.0F;
		THICK = 40.0F;
		BLUR = 11.0F;
		DISPERSE = 0.05F;
		FRESNEL = 16.0F;
		FRESNEL_H = 4.0F;
		FRESNEL_A = 0x59FFFFFF;
		GLASS_TINT = 0x8AF3EEEB;
		TONE_FLOOR = 0.30F;
		TONE_CEIL = 0.80F;
		SATURATION = 0.42F;
		DENS = 0.45F;
		DENS_CARD = 0.22F;
		EDGE_STRONG = 0x3D000000;
		EDGE_LIGHT = 0x52FFFFFF;
		DIVIDER = 0x2E3C3C43;
		ICON_BG = 0x2E000000;
		WARN = 0xFFB3261E;
		TOGGLE_ON = 0xFF0084E8;
		TOGGLE_OFF = 0x33000000;
		TOGGLE_KNOB = 0xFFFFFFFF;
		TEXT = 0xFF14141A;
		TEXT_MUTED = 0xC4000000;
		TEXT_FAINT = 0x99000000;
		ACCENT = 0xFFE8A33D;
		OK = 0xE62E8B4F;
		NEUTRAL = 0xD98E8E93;
	}


	// --- Masse, von der Palette unabhaengig ----------------------------

	public static final int ROW_H = 17;
	public static final int CARD_H = 52;
	public static final int CARD_GAP = 7;
	public static final int CARD_MIN_W = 150;
	public static final int SHADOW_SPREAD = 8;
	public static final int SHADOW_OFFSET = 3;
}
