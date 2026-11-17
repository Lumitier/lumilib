package com.lumitier.lumilib.client;

import com.lumitier.lumilib.LumiLib;
import net.minecraft.util.Util;

/**
 * Zeit und Kurven fuer alles, was sich im Hub bewegt.
 *
 * ZWEI ARTEN VON BEWEGUNG, und beide stehen hier:
 *
 * 1. Mit Anfang und Ende - Oeffnen, Schliessen, Seitenwechsel. Dafuer
 *    progress(): Startzeit merken, jedes Bild ausrechnen, wie weit es ist.
 *
 * 2. Ohne festes Ende - Hover, Schalter. Die Maus kann jederzeit wieder
 *    weg sein, also gibt es kein "fertig". Dafuer approach(): ein Wert
 *    laeuft jedes Bild ein Stueck auf sein Ziel zu, gleich schnell bei 30
 *    wie bei 240 Bildern pro Sekunde.
 *
 * KEINE OBJEKTE PRO BILD: Alles hier sind Rechnungen auf float und long.
 * Wer Zustand braucht, haelt ihn selbst in einem Feld oder einem Array.
 *
 * AUS ist AUS: Steht die Einstellung auf aus, liefert progress() sofort 1
 * und approach() sofort das Ziel. Die Aufrufer brauchen keine eigene
 * Abfrage - sie zeichnen einfach den Endzustand.
 */
public final class Anim {

	/** Hub oeffnen. Lang genug, um es zu sehen, kurz genug, um nicht zu warten. */
	public static final int OPEN_MS = 220;
	/** Schliessen ist kuerzer als Oeffnen: Wer gehen will, will gehen. */
	public static final int CLOSE_MS = 150;
	/** Seitenwechsel. */
	public static final int PAGE_MS = 180;
	/** Zeitkonstante fuer Hover: nach 70 ms zu zwei Dritteln da. */
	public static final float HOVER_MS = 70.0F;
	/** Zeitkonstante fuer Schalter und Zeilen, die Platz machen. */
	public static final float SLIDE_MS = 55.0F;

	private Anim() {}

	public static boolean enabled() {
		return LumiLib.cfg.animations;
	}

	/** Millisekunden, monoton - springt nicht, wenn jemand die Uhr stellt. */
	public static long now() {
		return Util.getMeasuringTimeMs();
	}

	/** 0 bis 1, wie weit eine Bewegung seit {@code start} ist. */
	public static float progress(long start, int durationMs) {
		if (!enabled() || durationMs <= 0) {
			return 1.0F;
		}
		float t = (now() - start) / (float) durationMs;
		return t <= 0.0F ? 0.0F : (t >= 1.0F ? 1.0F : t);
	}

	/** Schnell los, sanft ankommen. Fuer alles, was erscheint. */
	public static float easeOut(float t) {
		float u = 1.0F - t;
		return 1.0F - u * u * u;
	}

	/** Sanft los, schnell weg. Fuer alles, was verschwindet. */
	public static float easeIn(float t) {
		return t * t * t;
	}

	/**
	 * Laeuft von {@code current} auf {@code target} zu.
	 *
	 * Exponentiell und nicht linear: Ein Wert, der unterwegs ein neues Ziel
	 * bekommt, biegt einfach ab, ohne Ruck. Bei linearer Bewegung muesste
	 * man sich merken, wann sie angefangen hat.
	 */
	public static float approach(float current, float target, float dtMs, float timeMs) {
		if (!enabled()) {
			return target;
		}
		float k = 1.0F - (float) Math.exp(-dtMs / timeMs);
		float next = current + (target - current) * k;
		// Die letzten Tausendstel einrasten, sonst zeichnet man ewig an
		// einem Wert, den kein Auge mehr unterscheidet.
		return Math.abs(target - next) < 0.002F ? target : next;
	}

	/** Deckkraft einer Farbe mit {@code f} malnehmen. */
	public static int alpha(int argb, float f) {
		if (f >= 1.0F) {
			return argb;
		}
		int a = (int) ((argb >>> 24) * Math.max(0.0F, f));
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	/** Zwischen zwei Farben mischen, jeder Kanal fuer sich. */
	public static int mix(int from, int to, float t) {
		if (t <= 0.0F) {
			return from;
		}
		if (t >= 1.0F) {
			return to;
		}
		int a = lerp(from >>> 24, to >>> 24, t);
		int r = lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
		int g = lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
		int b = lerp(from & 0xFF, to & 0xFF, t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int lerp(int a, int b, float t) {
		return a + Math.round((b - a) * t);
	}
}
