package com.lumitier.lumilib.client;

import com.lumitier.lumilib.LumiLib;

/**
 * Die waehlbaren Akzentfarben.
 *
 * Nur Vorgaben, kein freier Farbwaehler: Auf allen davon bleiben der
 * weisse Schalterknopf und die Schrift lesbar - in beiden Paletten.
 * Platz 0 ist der Akzent der Palette und folgt dem Wechsel hell/dunkel.
 */
public final class Accent {

	private Accent() {}

	private static final String[] KEYS = {
		"standard", "violet", "pink", "red", "orange", "gold", "green", "teal"
	};
	private static final int[] RGB = {
		0, 0x7C4DFF, 0xE0457B, 0xE5484D, 0xF07F2E, 0xE8A33D, 0x2FB46E, 0x12A8B8
	};

	/** Schluessel fuer die Auswahlzeile: lumilib.option.accent.&lt;key&gt;. */
	public static String[] keys() {
		return KEYS.clone();
	}

	/** Setzt die gespeicherte Wahl in Theme um. */
	public static void apply() {
		int i = Math.max(0, Math.min(RGB.length - 1, LumiLib.cfg.accentPreset));
		Theme.setAccent(RGB[i]);
	}
}
