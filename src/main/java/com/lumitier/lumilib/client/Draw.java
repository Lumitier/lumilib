package com.lumitier.lumilib.client;

import com.lumitier.lumilib.client.gpu.Sdf;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Zeichenhelfer, die Vanilla nicht mitbringt.
 *
 * WIE RUNDE ECKEN HIER ENTSTEHEN - zwei Wege, in dieser Reihenfolge:
 *
 * 1. Auf der Grafikkarte, ueber Sdf mit Style.FLAT. Die Kante wird dort
 *    aus dem Abstand zur Form berechnet und ueber die Breite eines
 *    Bildpunkts ausgeblendet. Dadurch ist sie bei jeder Groesse und
 *    jeder GUI-Skalierung gleich weich.
 *
 * 2. Schlaegt das fehl - kein Shader, zu hohe Flaeche -, zeilenweise mit
 *    Fuellrechtecken, eingerueckt nach Kreisgleichung.
 *
 * WARUM NICHT GLEICH SO:
 * Weg 2 war lange der einzige. Er zeichnet die Rundung in GANZEN
 * Bildpunkten, also als Treppe. Bei einer 52 Pixel hohen Karte mit Radius
 * 10 faellt das kaum auf; bei einem 10 Pixel hohen Schalter oder einem
 * Statuspunkt mit Radius 3 ist die Treppe die Form. Genau diese kleinen
 * Elemente sahen gepixelt aus, nicht die grossen Flaechen.
 */
public final class Draw {

	private Draw() {}

	/** Rechteck mit abgerundeten Ecken. */
	public static void roundedRect(DrawContext ctx, int x, int y, int w, int h, int radius, int argb) {
		if (w <= 0 || h <= 0) {
			return;
		}
		int r = Math.min(radius, Math.min(w, h) / 2);
		if (r <= 0) {
			ctx.fill(x, y, x + w, y + h, argb);
			return;
		}
		if (Sdf.roundedRect(ctx, Sdf.Style.FLAT, x, y, w, h, r, argb)) {
			return;
		}
		scanlineRect(ctx, x, y, w, h, r, argb);
	}

	/** Weg 2: zeilenweise, in ganzen Bildpunkten. */
	private static void scanlineRect(DrawContext ctx, int x, int y, int w, int h,
	                                 int r, int argb) {
		// Mittelblock in voller Breite, oben und unten um den Radius verkuerzt.
		ctx.fill(x, y + r, x + w, y + h - r, argb);

		// Die beiden Kappen zeilenweise, Einrueckung nach Kreisgleichung.
		for (int i = 0; i < r; i++) {
			// Abstand der Zeilenmitte vom Kreismittelpunkt
			double dy = r - i - 0.5;
			int inset = r - (int) Math.floor(Math.sqrt(r * r - dy * dy) + 0.5);
			ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, argb);
			ctx.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, argb);
		}
	}

	/**
	 * Heller Umriss eines abgerundeten Rechtecks.
	 *
	 * Die sichtbare Kante ist das, was eine durchscheinende Flaeche ueberhaupt
	 * erst als Flaeche lesbar macht. Ohne sie verschwimmt helles Glas auf
	 * hellem Hintergrund zu einem Fleck.
	 */
	public static void roundedOutline(DrawContext ctx, int x, int y, int w, int h, int radius, int argb) {
		if (w <= 0 || h <= 0) {
			return;
		}
		int r = Math.min(radius, Math.min(w, h) / 2);

		// Gerade Stuecke
		ctx.fill(x + r, y, x + w - r, y + 1, argb);
		ctx.fill(x + r, y + h - 1, x + w - r, y + h, argb);
		ctx.fill(x, y + r, x + 1, y + h - r, argb);
		ctx.fill(x + w - 1, y + r, x + w, y + h - r, argb);

		// Ecken: je Zeile ein Pixel an der Rundung
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = r - (int) Math.floor(Math.sqrt(r * r - dy * dy) + 0.5);
			ctx.fill(x + inset, y + i, x + inset + 1, y + i + 1, argb);
			ctx.fill(x + w - inset - 1, y + i, x + w - inset, y + i + 1, argb);
			ctx.fill(x + inset, y + h - i - 1, x + inset + 1, y + h - i, argb);
			ctx.fill(x + w - inset - 1, y + h - i - 1, x + w - inset, y + h - i, argb);
		}
	}

	/** Waagerechte Haarlinie. */
	public static void hLine(DrawContext ctx, int x, int y, int w, int argb) {
		ctx.fill(x, y, x + w, y + 1, argb);
	}

	/** Senkrechte Haarlinie. */
	public static void vLine(DrawContext ctx, int x, int y, int h, int argb) {
		ctx.fill(x, y, x + 1, y + h, argb);
	}

	/**
	 * Kleiner gefuellter Kreis, fuer Statuspunkte.
	 *
	 * Ein Kreis ist ein Quadrat, dessen Radius seine halbe Kante ist -
	 * also dieselbe Abstandsfunktion, kein eigener Fall im Shader.
	 */
	public static void dot(DrawContext ctx, int cx, int cy, int radius, int argb) {
		if (radius <= 0) {
			return;
		}
		int d = radius * 2;
		if (Sdf.roundedRect(ctx, Sdf.Style.FLAT, cx - radius, cy - radius, d, d, radius, argb)) {
			return;
		}
		for (int dy = -radius; dy <= radius; dy++) {
			int dx = (int) Math.floor(Math.sqrt(radius * radius - dy * dy) + 0.5);
			ctx.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, argb);
		}
	}

	/**
	 * Kuerzt Text auf eine Pixelbreite und haengt ein Auslassungszeichen an.
	 *
	 * Bewusst ueber getWidth statt ueber trimToWidth: getWidth gibt es in
	 * jeder Version unveraendert, die Trimm-Methoden haben in der Vergangenheit
	 * Signatur und Rueckgabetyp gewechselt.
	 */
	public static String fit(TextRenderer tr, String text, int maxWidth) {
		if (text == null || text.isEmpty() || tr.getWidth(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "...";
		int room = maxWidth - tr.getWidth(ellipsis);
		if (room <= 0) {
			return "";
		}
		int end = text.length();
		while (end > 0 && tr.getWidth(text.substring(0, end)) > room) {
			end--;
		}
		return text.substring(0, end).stripTrailing() + ellipsis;
	}

	/** Liegt der Punkt im Rechteck? */
	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
