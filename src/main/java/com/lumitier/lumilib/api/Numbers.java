package com.lumitier.lumilib.api;

/**
 * Begrenzungshelfer.
 *
 * Stand bisher wortgleich in Perpetual Snow, Experience Orb Tweaks, Item Drop
 * Tweaks und Shadow Tweaks. Hier einmal.
 *
 * ACHTUNG - OEFFENTLICHE API: nur hinzufuegen, nie entfernen oder aendern.
 */
public final class Numbers {

	private Numbers() {}

	/**
	 * Begrenzt v auf [min, max]. NaN ergibt min - ein kaputter Wert aus einer
	 * beschaedigten Config soll die Mod nicht umbringen, sondern auf das
	 * harmloseste Ende fallen.
	 */
	public static float clamp(float v, float min, float max) {
		if (Float.isNaN(v)) {
			return min;
		}
		return v < min ? min : (v > max ? max : v);
	}

	/** Begrenzt v auf [min, max]. */
	public static int clampInt(int v, int min, int max) {
		return v < min ? min : (v > max ? max : v);
	}

	/** Begrenzt v auf [min, max]. NaN ergibt min. */
	public static double clampDouble(double v, double min, double max) {
		if (Double.isNaN(v)) {
			return min;
		}
		return v < min ? min : (v > max ? max : v);
	}

	/**
	 * Logarithmischer Anteil von value im Bereich [1, max], Ergebnis 0..1.
	 *
	 * Gebraucht ueberall dort, wo Mengen exponentiell wachsen - Stapelgroessen,
	 * XP-Werte. Linear waere die Skala schon bei mittleren Werten am Anschlag.
	 */
	public static float logFraction(int value, int max) {
		if (value <= 1 || max <= 1) {
			return 0.0F;
		}
		float f = (float) (Math.log(value) / Math.log(max));
		return clamp(f, 0.0F, 1.0F);
	}
}
