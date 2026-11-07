package com.lumitier.lumilib.client;

import com.lumitier.lumilib.api.SettingsPage;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Der Farbwaehler: Vorschau, Flaeche, Farbton, Deckkraft.
 *
 * AUFBAU, breit statt hoch:
 *
 *     +----------+--------------------------+
 *     | Vorschau |         Flaeche          |   Saettigung waagrecht,
 *     +----------+--------------------------+   Helligkeit senkrecht
 *     |            Farbton                  |
 *     |            Deckkraft                |
 *     +-------------------------------------+
 *
 * Die Vorschau steht NEBEN der Flaeche, nicht darunter: So sieht man
 * beim Ziehen sofort, was herauskommt, ohne den Blick zu senken - und
 * darunter laege sie genau dort, wo die Hand ist.
 *
 * KEIN HEXFELD HIER: Der Hexwert steht schon in der Zeile, aus der sich
 * der Waehler oeffnet. Zweimal zeigen macht das Fenster groesser, ohne
 * dass es mehr kann.
 *
 * HSV WIRD HIER GEHALTEN, nicht aus der Farbe zurueckgerechnet:
 * Zieht man die Helligkeit auf null, ist die Farbe schwarz - und aus
 * Schwarz laesst sich kein Farbton mehr ablesen. Der Griff im
 * Farbtonbalken wuerde auf Rot springen und die Farbe waere weg.
 *
 * GEZEICHNET WIRD OHNE SHADER, aus schmalen Streifen. Bei dieser Groesse
 * ist das von einem echten Verlauf nicht zu unterscheiden, und es
 * braucht keine eigene Pipeline, kein Schnittfenster, keine
 * Rueckfallebene.
 */
final class ColourPopup {

	private ColourPopup() {}

	// ------------------------------------------------------------------
	// Masse
	// ------------------------------------------------------------------

	private static final int PAD = 6;
	private static final int PREVIEW_W = 56;
	private static final int AREA_W = 150;
	private static final int AREA_H = 76;
	private static final int BAR_H = 11;
	/**
	 * Abstaende, bemessen an den GRIFFEN, nicht an den Balken.
	 *
	 * Ein Griff steht oben und unten je 3 Pixel ueber seinen Balken
	 * hinaus. Bei 3 Pixeln Abstand zwischen zwei Balken beruehren sich
	 * die Griffe also genau dann, wenn beide in derselben Spalte stehen
	 * - und das passiert staendig, weil Farbton und Deckkraft oft
	 * zusammenfallen.
	 *
	 * Also: 8 zwischen den Balken (3 + 3 Ueberstand + 2 Luft) und 7
	 * unter der Flaeche.
	 */
	private static final int GAP = 7;
	private static final int BAR_GAP = 8;

	/** Eckradius. Klein gehalten - das Vorbild ist fast eckig. */
	private static final int R = 2;

	static final int W = PAD * 2 + PREVIEW_W + GAP + AREA_W;
	static final int H = PAD * 2 + AREA_H + GAP + BAR_H + BAR_GAP + BAR_H;

	// ------------------------------------------------------------------
	// Zustand
	// ------------------------------------------------------------------

	private static SettingsPage.Colour open;
	private static int px, py;

	/** Farbton 0-360, Saettigung und Helligkeit 0-1, Deckkraft 0-255. */
	private static float hue, sat, val;
	private static int alpha;

	/** Was gerade gezogen wird: 0 nichts, 1 Flaeche, 2 Farbton, 3 Deckkraft. */
	private static int dragPart;

	static boolean isOpen() {
		return open != null;
	}

	static void close() {
		open = null;
		dragPart = 0;
	}

	/**
	 * Oeffnet den Waehler an einer Stelle, die ins Fenster passt.
	 *
	 * Bevorzugt unter der Zeile und rechtsbuendig zu ihr. Ist unten kein
	 * Platz, klappt er nach oben - sonst steht die Haelfte ausserhalb des
	 * Bildschirms und ist nicht erreichbar.
	 */
	static void openAt(SettingsPage.Colour c, int rowRight, int rowBottom, int rowTop,
	                   int screenW, int screenH) {
		open = c;
		dragPart = 0;

		int argb = c.getter().getAsInt();
		alpha = (argb >>> 24) & 0xFF;
		float[] hsv = toHsv(argb);
		hue = hsv[0];
		sat = hsv[1];
		val = hsv[2];

		px = Math.max(4, Math.min(screenW - W - 4, rowRight - W));
		py = rowBottom + 4;
		if (py + H > screenH - 4) {
			py = Math.max(4, rowTop - H - 4);
		}
	}

	// ------------------------------------------------------------------
	// Zeichnen
	// ------------------------------------------------------------------

	static void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
		if (open == null) {
			return;
		}
		// Schatten zuerst: Der Waehler schwebt ueber der Seite, und ohne
		// Schatten klebt er darauf wie aufgemalt.
		Surface.shadowPanel(ctx, px, py, W, H,
			Theme.SHADOW_SPREAD, Theme.SHADOW_OFFSET, Theme.SHADOW);
		if (!Surface.glassPanel(ctx, px, py, W, H, Theme.PANEL)) {
			Surface.panel(ctx, px, py, W, H, Theme.PANEL);
		}
		Surface.rimPanel(ctx, px, py, W, H, Theme.RIM);

		int x = px + PAD;
		int y = py + PAD;

		drawPreview(ctx, x, y);
		drawArea(ctx, x + PREVIEW_W + GAP, y);

		y += AREA_H + GAP;
		drawHue(ctx, x, y);
		y += BAR_H + BAR_GAP;
		drawAlpha(ctx, x, y);
	}

	/** Die Vorschau - die gewaehlte Farbe ueber dem Karomuster. */
	private static void drawPreview(DrawContext ctx, int x, int y) {
		checker(ctx, x, y, PREVIEW_W, AREA_H);
		Draw.roundedRect(ctx, x, y, PREVIEW_W, AREA_H, R, current());
		edge(ctx, x, y, PREVIEW_W, AREA_H, R);
	}

	/**
	 * Die Flaeche: waagrecht Saettigung, senkrecht Helligkeit.
	 *
	 * Zwei Lagen, wie ueberall ueblich: erst Weiss nach reinem Farbton in
	 * senkrechten Streifen, darueber durchsichtig nach Schwarz in
	 * waagrechten. Beides aus einfachen Fuellungen, damit nichts von
	 * einer Minecraft-Signatur abhaengt, die sich aendern kann.
	 */
	private static void drawArea(DrawContext ctx, int x, int y) {
		int pure = hsvToRgb(hue, 1.0F, 1.0F);
		for (int i = 0; i < AREA_W; i++) {
			float t = i / (float) (AREA_W - 1);
			int d = inset(i, AREA_W, AREA_H, R);
			ctx.fill(x + i, y + d, x + i + 1, y + AREA_H - d,
				0xFF000000 | lerp(0xFFFFFF, pure, t));
		}
		for (int j = 0; j < AREA_H; j++) {
			int a = Math.round(j / (float) (AREA_H - 1) * 255.0F);
			// Der dunkle Verlauf laeuft waagrecht, muss also an den Enden
			// eingezogen werden - sonst stehen dunkle Zipfel in den Ecken.
			int d = inset(j, AREA_H, AREA_W, R);
			ctx.fill(x + d, y + j, x + AREA_W - d, y + j + 1, a << 24);
		}
		edge(ctx, x, y, AREA_W, AREA_H, R);

		int cx = x + Math.round(sat * (AREA_W - 1));
		int cy = y + Math.round((1.0F - val) * (AREA_H - 1));
		ring(ctx, cx, cy);
	}

	/** Der Farbtonbalken. */
	private static void drawHue(DrawContext ctx, int x, int y) {
		int w = barWidth();
		for (int i = 0; i < w; i++) {
			float h = i / (float) (w - 1) * 360.0F;
			int d = inset(i, w, BAR_H, R);
			ctx.fill(x + i, y + d, x + i + 1, y + BAR_H - d,
				0xFF000000 | hsvToRgb(h, 1.0F, 1.0F));
		}
		edge(ctx, x, y, w, BAR_H, R);
		handle(ctx, x + Math.round(hue / 360.0F * (w - 1)), y);
	}

	/**
	 * Die Deckkraft, ueber dem Karomuster.
	 *
	 * Ohne das Karo waere das linke Ende nicht von einem hellen Grau zu
	 * unterscheiden - und genau dort steht "durchsichtig".
	 */
	private static void drawAlpha(DrawContext ctx, int x, int y) {
		int w = barWidth();
		int rgb = hsvToRgb(hue, sat, val);
		checker(ctx, x, y, w, BAR_H);
		for (int i = 0; i < w; i++) {
			int a = Math.round(i / (float) (w - 1) * 255.0F);
			int d = inset(i, w, BAR_H, R);
			ctx.fill(x + i, y + d, x + i + 1, y + BAR_H - d, (a << 24) | rgb);
		}
		edge(ctx, x, y, w, BAR_H, R);
		handle(ctx, x + Math.round(alpha / 255.0F * (w - 1)), y);
	}

	private static int barWidth() {
		return PREVIEW_W + GAP + AREA_W;
	}

	// ------------------------------------------------------------------
	// Kleinteile
	// ------------------------------------------------------------------

	/**
	 * Die Kante: dunkel innen, hell aussen.
	 *
	 * Eine einzige Farbe reicht nicht. Hell verschwindet auf einem hellen
	 * Verlauf, dunkel auf einem dunklen - zwei Linien sind auf jedem
	 * Grund sichtbar.
	 */
	private static void edge(DrawContext ctx, int x, int y, int w, int h, int r) {
		Draw.roundedOutline(ctx, x, y, w, h, r, Theme.EDGE_STRONG);
		Draw.roundedOutline(ctx, x - 1, y - 1, w + 2, h + 2, r + 1, Theme.EDGE_LIGHT);
	}

	/**
	 * Senkrechter Einzug einer Spalte, damit Streifen runde Ecken ergeben.
	 *
	 * DER TRICK, mit dem Verlaeufe rund werden: Ein Verlauf entsteht hier
	 * aus schmalen senkrechten Fuellungen. Kuerzt man die am Rand nach der
	 * Kreisgleichung, hat die ganze Flaeche runde Ecken - ohne Maske,
	 * ohne Shader, ohne zweite Textur.
	 *
	 * @param i Spalte von links, 0 bis w-1
	 */
	private static int inset(int i, int w, int h, int r) {
		int rr = Math.min(r, Math.min(w, h) / 2);
		if (rr <= 0) {
			return 0;
		}
		float dx;
		if (i < rr) {
			dx = rr - 0.5F - i;
		} else if (i >= w - rr) {
			dx = i - (w - rr) + 0.5F;
		} else {
			return 0;
		}
		return Math.round(rr - (float) Math.sqrt(Math.max(0.0F, rr * rr - dx * dx)));
	}

	/**
	 * Der Zeiger in der Flaeche: ein duenner Ring mit der Farbe darin.
	 *
	 * Aussen dunkel, dann weiss, innen die gewaehlte Farbe. So bleibt er
	 * ueber Weiss wie ueber Schwarz sichtbar, und man sieht am Zeiger
	 * selbst, was man gerade hat.
	 */
	private static void ring(DrawContext ctx, int cx, int cy) {
		Draw.dot(ctx, cx, cy, 5, 0x66000000);
		Draw.dot(ctx, cx, cy, 4, 0xFFFFFFFF);
		Draw.dot(ctx, cx, cy, 2, 0xFF000000 | hsvToRgb(hue, sat, val));
	}

	/**
	 * Der Griff auf einem Balken.
	 *
	 * Schmal, senkrecht, oben und unten ueberstehend - wie im Vorbild.
	 * Ein Punkt verschwindet im Verlauf; der Ueberstand macht ihn
	 * greifbar, ohne die Farbe darunter zu verdecken.
	 */
	private static void handle(DrawContext ctx, int cx, int y) {
		int w = 4;
		int h = BAR_H + 6;
		int hx = cx - w / 2;
		int hy = y - 3;
		Draw.roundedRect(ctx, hx - 1, hy - 1, w + 2, h + 2, 2, 0x59000000);
		Draw.roundedRect(ctx, hx, hy, w, h, 1, 0xFFFFFFFF);
	}

	private static void checker(DrawContext ctx, int x, int y, int w, int h) {
		for (int row = 0; row * 4 < h; row++) {
			for (int col = 0; col * 4 < w; col++) {
				int cx = x + col * 4;
				int cy = y + row * 4;
				ctx.fill(cx, cy, Math.min(cx + 4, x + w), Math.min(cy + 4, y + h),
					(row + col) % 2 == 0 ? 0xFFFFFFFF : 0xFFBDBDBD);
			}
		}
	}

	// ------------------------------------------------------------------
	// Bedienen
	// ------------------------------------------------------------------

	/** @return true, wenn der Klick im Waehler lag */
	static boolean click(double mx, double my) {
		if (open == null) {
			return false;
		}
		int x = px + PAD;
		int y = py + PAD;
		int bw = barWidth();

		if (Draw.inside(mx, my, x + PREVIEW_W + GAP, y, AREA_W, AREA_H)) {
			dragPart = 1;
			applyDrag(mx, my);
			return true;
		}
		y += AREA_H + GAP;
		// Trefferflaeche hoeher als der Balken: Elf Pixel trifft man mit
		// der Maus nicht zuverlaessig.
		if (Draw.inside(mx, my, x, y - 3, bw, BAR_H + 6)) {
			dragPart = 2;
			applyDrag(mx, my);
			return true;
		}
		y += BAR_H + BAR_GAP;
		if (Draw.inside(mx, my, x, y - 3, bw, BAR_H + 6)) {
			dragPart = 3;
			applyDrag(mx, my);
			return true;
		}
		// Sonst im Fenster: Klick schlucken, aber nichts tun.
		return Draw.inside(mx, my, px, py, W, H);
	}

	static boolean dragging() {
		return dragPart != 0;
	}

	/** Nachfuehren beim Ziehen - ohne zu speichern. */
	static void drag(double mx, double my) {
		if (open != null && dragPart != 0) {
			applyDrag(mx, my);
		}
	}

	static void endDrag() {
		dragPart = 0;
	}

	private static void applyDrag(double mx, double my) {
		int x = px + PAD;
		int y = py + PAD;
		int bw = barWidth();
		if (dragPart == 1) {
			sat = clamp01((float) (mx - (x + PREVIEW_W + GAP)) / (AREA_W - 1));
			val = 1.0F - clamp01((float) (my - y) / (AREA_H - 1));
		} else if (dragPart == 2) {
			hue = clamp01((float) (mx - x) / (bw - 1)) * 360.0F;
		} else if (dragPart == 3) {
			alpha = Math.round(clamp01((float) (mx - x) / (bw - 1)) * 255.0F);
		}
		push();
	}

	private static int current() {
		return (alpha << 24) | hsvToRgb(hue, sat, val);
	}

	private static void push() {
		open.setter().accept(current());
	}

	// ------------------------------------------------------------------
	// Farbrechnung
	// ------------------------------------------------------------------

	private static float clamp01(float f) {
		return Math.max(0.0F, Math.min(1.0F, f));
	}

	private static int lerp(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	/** @return Farbton 0-360, Saettigung 0-1, Helligkeit 0-1 */
	private static float[] toHsv(int argb) {
		float r = ((argb >> 16) & 0xFF) / 255.0F;
		float g = ((argb >> 8) & 0xFF) / 255.0F;
		float b = (argb & 0xFF) / 255.0F;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;

		float h = 0.0F;
		if (d > 0.0F) {
			if (max == r) {
				h = 60.0F * (((g - b) / d) % 6.0F);
			} else if (max == g) {
				h = 60.0F * ((b - r) / d + 2.0F);
			} else {
				h = 60.0F * ((r - g) / d + 4.0F);
			}
		}
		if (h < 0.0F) {
			h += 360.0F;
		}
		return new float[] { h, max == 0.0F ? 0.0F : d / max, max };
	}

	/** @return 0xRRGGBB, ohne Deckkraft */
	private static int hsvToRgb(float h, float s, float v) {
		float c = v * s;
		float x = c * (1.0F - Math.abs((h / 60.0F) % 2.0F - 1.0F));
		float m = v - c;
		float r;
		float g;
		float b;
		int seg = (int) Math.floor(h / 60.0F) % 6;
		switch (seg < 0 ? seg + 6 : seg) {
			case 0 -> { r = c; g = x; b = 0.0F; }
			case 1 -> { r = x; g = c; b = 0.0F; }
			case 2 -> { r = 0.0F; g = c; b = x; }
			case 3 -> { r = 0.0F; g = x; b = c; }
			case 4 -> { r = x; g = 0.0F; b = c; }
			default -> { r = c; g = 0.0F; b = x; }
		}
		return (Math.round((r + m) * 255.0F) << 16)
			| (Math.round((g + m) * 255.0F) << 8)
			| Math.round((b + m) * 255.0F);
	}
}
