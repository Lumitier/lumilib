package com.lumitier.lumilib.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;

/**
 * Ein Bildschirm, der auf jeder GUI-Stufe gleich gross aussieht.
 *
 * WARUM: Die Oberflaeche ist fuer GUI 2 auf 1920x1080 entworfen - 960x540
 * logische Pixel. Auf GUI 3 bleiben nur 640x360, die Sidebar laeuft ueber
 * und Namen werden abgeschnitten. Auf GUI 1 sind es 1920x1080 und alles
 * ist winzig.
 *
 * WIE: Der Bildschirm rechnet sich eine eigene Groesse aus, so dass er im
 * Verhaeltnis zum Fenster immer so gross ist wie GUI 2 auf 1080p. Gezeichnet
 * wird mit einer Skalierung um alles herum, die Mauskoordinaten werden
 * umgerechnet. Unterklassen merken davon nichts: width, height und alle
 * Mauswerte sind bereits in der eigenen Groesse.
 *
 * AUF GUI 2 MIT 1080p IST DIE SKALIERUNG GENAU 1 - dort aendert sich nichts.
 *
 * FUER UNTERKLASSEN:
 *  - init() mit super.init() beginnen.
 *  - renderScaled() statt render(), KEIN super.render() darin.
 *  - scaledMouseClicked/-Released/-Dragged/-Scrolled statt der normalen
 *    Methoden. Die normalen sind final, damit niemand versehentlich mit
 *    unumgerechneten Koordinaten arbeitet.
 */
public abstract class ScaledScreen extends Screen {

	/** Entwurfsgroesse: GUI 2 auf 1920x1080. */
	public static final int DESIGN_W = 960;
	public static final int DESIGN_H = 540;

	private float scale = 1.0F;
	private int realWidth;
	private int realHeight;

	/**
	 * OEFFNEN UND SCHLIESSEN, fuer jeden Bildschirm, der hiervon erbt.
	 *
	 * Steht hier und nicht in jedem Bildschirm einzeln, damit sich alle
	 * Menues von LumiLib und den Mods gleich bewegen - auch solche, die es
	 * noch nicht gibt. Wer sich selbst bewegt (der Hub), meldet das ueber
	 * animatesItself() ab.
	 */
	private long openedAt;
	private long closingAt = -1L;
	private Runnable afterClose;

	protected ScaledScreen(Text title) {
		super(title);
	}

	/** Faktor von eigenen auf Minecrafts GUI-Pixel. 1 heisst: wie GUI 2 auf 1080p. */
	public float uiScale() {
		return scale;
	}

	// ------------------------------------------------------------------
	// Groesse
	// ------------------------------------------------------------------

	/**
	 * Rechnet die eigene Groesse aus. Liest das FENSTER, nicht this.width:
	 * clearAndInit() ruft init() ohne width neu zu setzen - mit this.width
	 * wuerde zweimal skaliert.
	 */
	@Override
	protected void init() {
		if (this.client == null) {
			return;
		}
		Window win = this.client.getWindow();
		this.realWidth = win.getScaledWidth();
		this.realHeight = win.getScaledHeight();

		// Wie viele Bildschirmpixel ein eigener Pixel bekommt. 1080p -> 2,
		// 1440p -> 2,67, 4K -> 4. Nie unter 1, sonst wird es unscharf
		// kleiner als Minecraft selbst zeichnen kann.
		float target = Math.min(win.getFramebufferWidth() / (float) DESIGN_W,
			win.getFramebufferHeight() / (float) DESIGN_H);
		// Knapp daneben zaehlt als ganze Zahl: Ein Fenster mit Titelleiste
		// hat 1017 statt 1080 Pixel Hoehe - dafuer soll nicht alles um 6 %
		// schrumpfen und unscharf werden.
		float rounded = Math.round(target);
		if (rounded >= 1.0F && Math.abs(target - rounded) < 0.15F) {
			target = rounded;
		}
		target = Math.max(1.0F, target);

		int gui = Math.max(1, win.getScaleFactor());
		this.scale = target / gui;
		this.width = (int) Math.ceil(realWidth / scale);
		this.height = (int) Math.ceil(realHeight / scale);
	}

	// ------------------------------------------------------------------
	// Zeichnen
	// ------------------------------------------------------------------

	@Override
	public final void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		if (this.openedAt == 0L) {
			this.openedAt = Anim.now();
		}
		// Schliessen durch? Dann jetzt wirklich weg, ohne noch ein Bild.
		if (this.closingAt >= 0L && Anim.progress(this.closingAt, Anim.CLOSE_MS) >= 1.0F) {
			finishClose();
			return;
		}
		int mx = (int) (mouseX / scale);
		int my = (int) (mouseY / scale);
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().scale(scale, scale);
		try {
			if (!animatesItself()) {
				// Dieselbe Bewegung wie beim Hub: aus 96 % wachsen und 8
				// Pixel von unten kommen, um die Mitte des Bildschirms.
				float in = Anim.easeOut(Anim.progress(this.openedAt, Anim.OPEN_MS));
				float out = this.closingAt >= 0L
					? Anim.easeIn(Anim.progress(this.closingAt, Anim.CLOSE_MS)) : 0.0F;
				float shown = in * (1.0F - out);
				if (shown < 1.0F) {
					float k = 0.96F + 0.04F * shown;
					float cx = this.width / 2.0F;
					float cy = this.height / 2.0F;
					ctx.getMatrices().translate(cx, cy + (1.0F - shown) * 8.0F);
					ctx.getMatrices().scale(k, k);
					ctx.getMatrices().translate(-cx, -cy);
				}
			}
			renderScaled(ctx, mx, my, delta);
			super.render(ctx, mx, my, delta);
		} finally {
			ctx.getMatrices().popMatrix();
		}
	}

	/** true, wenn der Bildschirm seine Bewegung selbst zeichnet. */
	protected boolean animatesItself() {
		return false;
	}

	/**
	 * Schliesst mit Bewegung und fuehrt danach {@code done} aus - meist
	 * {@code client.setScreen(parent)}. Ohne Animationen sofort.
	 *
	 * Ein zweiter Aufruf waehrend des Schliessens beendet sofort: Wer
	 * zweimal Escape drueckt, will nicht warten.
	 */
	protected final void closeAnimated(Runnable done) {
		if (this.closingAt >= 0L || !Anim.enabled()) {
			this.afterClose = done;
			finishClose();
			return;
		}
		this.afterClose = done;
		this.closingAt = Anim.now();
	}

	/** Gerade dabei zu schliessen? Dann nimmt der Bildschirm nichts mehr an. */
	protected final boolean closing() {
		return this.closingAt >= 0L;
	}

	private void finishClose() {
		this.closingAt = -1L;
		Runnable r = this.afterClose;
		this.afterClose = null;
		if (r != null) {
			r.run();
		}
	}

	/** Wie render(), aber in der eigenen Groesse. Kein super.render() aufrufen. */
	protected abstract void renderScaled(DrawContext ctx, int mouseX, int mouseY, float delta);

	/**
	 * Minecrafts Hintergrund laeuft AUSSERHALB der Skalierung (renderWithTooltip
	 * ruft ihn vor render auf). Er braucht also die echte Groesse - sonst
	 * deckt er auf GUI 1 nur ein Viertel des Fensters ab.
	 */
	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int w = this.width;
		int h = this.height;
		this.width = realWidth > 0 ? realWidth : w;
		this.height = realHeight > 0 ? realHeight : h;
		try {
			super.renderBackground(ctx, mouseX, mouseY, delta);
		} finally {
			this.width = w;
			this.height = h;
		}
	}

	// ------------------------------------------------------------------
	// Maus - umrechnen, dann weiterreichen
	// ------------------------------------------------------------------

	private Click scaled(Click click) {
		if (scale == 1.0F) {
			return click;
		}
		return new Click(click.x() / scale, click.y() / scale, click.buttonInfo());
	}

	@Override
	public final boolean mouseClicked(Click click, boolean doubled) {
		if (closing()) {
			return true;
		}
		return scaledMouseClicked(scaled(click), doubled);
	}

	@Override
	public final boolean mouseReleased(Click click) {
		return scaledMouseReleased(scaled(click));
	}

	@Override
	public final boolean mouseDragged(Click click, double dx, double dy) {
		return scaledMouseDragged(scaled(click), dx / scale, dy / scale);
	}

	@Override
	public final boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		return scaledMouseScrolled(mouseX / scale, mouseY / scale, horizontal, vertical);
	}

	@Override
	public final void mouseMoved(double mouseX, double mouseY) {
		scaledMouseMoved(mouseX / scale, mouseY / scale);
	}

	protected boolean scaledMouseClicked(Click click, boolean doubled) {
		return super.mouseClicked(click, doubled);
	}

	protected boolean scaledMouseReleased(Click click) {
		return super.mouseReleased(click);
	}

	protected boolean scaledMouseDragged(Click click, double dx, double dy) {
		return super.mouseDragged(click, dx, dy);
	}

	protected boolean scaledMouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	protected void scaledMouseMoved(double mouseX, double mouseY) {
		super.mouseMoved(mouseX, mouseY);
	}
}
