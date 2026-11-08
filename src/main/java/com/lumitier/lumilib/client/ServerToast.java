package com.lumitier.lumilib.client;

import com.lumitier.lumilib.client.gpu.Sdf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Hinweis oben rechts, wenn ein Server eine Mod ab- oder wieder einschaltet.
 *
 * WARUM MINECRAFTS TOAST-SYSTEM: Es stapelt Hinweise, schiebt sie ein und
 * aus und kommt sich nicht mit Erfolgen oder "Messages cannot be reported"
 * in die Quere. Ein eigenes Overlay muesste das alles nachbauen - und
 * wuerde trotzdem ueber Minecrafts eigene Hinweise malen.
 *
 * AUSSEHEN WIE IM HUB: dieselbe Glaskarte, dasselbe Icon, dieselbe Schrift.
 * Nur die Fuellung ist kraeftiger als bei den Hub-Karten - die liegen dort
 * auf einem hellen Panel, hier liegt die Karte direkt ueber der Welt und
 * waere mit der Hub-Fuellung kaum lesbar.
 *
 * ECHTE LICHTBRECHUNG GIBT ES HIER NICHT: Die braucht eine Kopie des
 * Bildschirms, die nur der Hub anfertigt. Die Karte hat Fase, Lichtkante
 * und Durchsicht wie im Hub, aber keine Verzerrung des Hintergrunds.
 */
final class ServerToast implements Toast {

	/** Wie lange der Hinweis steht - so lange wie Minecrafts eigene. */
	private static final long DISPLAY_MS = 5000L;

	/**
	 * Hoeher als Minecrafts 32: Zwei Zeilen in normaler Schrift brauchen
	 * mehr Platz, sonst klebt die untere am Rand. Minecraft stapelt
	 * darunter folgende Hinweise entsprechend tiefer.
	 */
	private static final int HEIGHT = 40;
	private static final int MIN_W = 160;
	private static final int MAX_W = 240;
	private static final int PAD = 7;
	/** Rechts mehr Luft: Die Karte sitzt dort direkt am Bildschirmrand. */
	private static final int PAD_R = 11;
	private static final int ICON = 18;
	private static final int GAP = 7;

	private final String modId;
	private final String title;
	private final String line;
	private final boolean warn;
	private final int width;

	private Toast.Visibility visibility = Toast.Visibility.SHOW;

	/**
	 * @param warn true fuer "abgeschaltet" - zweite Zeile rot; false fuer "wieder an" - gruen
	 */
	ServerToast(String modId, String title, Text line, boolean warn) {
		this.modId = modId;
		this.warn = warn;
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;

		// Breite EINMAL hier, nicht in jedem Bild. Der Toast wird bis zu
		// 5 Sekunden lang in jedem Bild gezeichnet.
		String l = line.getString();
		int textW = Math.max(Ui.widthBold(tr, title), Ui.width(tr, l));
		this.width = Math.max(MIN_W, Math.min(MAX_W, PAD + ICON + GAP + textW + PAD_R));
		int room = this.width - PAD - ICON - GAP - PAD_R;
		this.title = Ui.fitBold(tr, title, room);
		this.line = Ui.fit(tr, l, room);
	}

	@Override
	public Toast.Visibility getVisibility() {
		return this.visibility;
	}

	@Override
	public void update(ToastManager manager, long time) {
		double shown = DISPLAY_MS * manager.getNotificationDisplayTimeMultiplier();
		this.visibility = time >= shown ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
	}

	@Override
	public int getWidth() {
		return this.width;
	}

	@Override
	public int getHeight() {
		return HEIGHT;
	}

	@Override
	public void draw(DrawContext ctx, TextRenderer tr, long startTime) {
		int w = this.width;

		// Ein Beschnitt aus dem Hub darf hier nicht greifen - der gilt fuer
		// dessen Listen, nicht fuer einen Hinweis ueber dem Spiel.
		ScreenRect oldClip = Sdf.clip();
		Sdf.clip(null);
		try {
			if (!Surface.glassCard(ctx, 0, 0, w, HEIGHT, Theme.PANEL)) {
				Surface.card(ctx, 0, 0, w, HEIGHT, Theme.PANEL);
				Surface.rimCard(ctx, 0, 0, w, HEIGHT, Theme.RIM_CARD);
			}

			int iy = (HEIGHT - ICON) / 2;
			drawIcon(ctx, PAD, iy);

			int tx = PAD + ICON + GAP;
			// Beide Zeilen in normaler Schrift, der Block aus beiden steht
			// senkrecht in der Mitte der Karte.
			int gap = Ui.advance(Ui.BODY_H, Ui.BODY_D, 3);
			int block = gap + Ui.BODY_H;
			int top = (HEIGHT - block) / 2;
			Ui.drawTop(ctx, tr, Ui.bold(this.title), tx, top, Ui.TOP_BODY, Theme.TEXT);
			Ui.drawTop(ctx, tr, Ui.text(this.line), tx,
				top + gap, Ui.TOP_BODY,
				this.warn ? Theme.WARN : Theme.OK);
		} finally {
			Sdf.clip(oldClip);
		}
	}

	/** Icon der Mod, wie im Hub - fehlt es, bleibt die Stelle frei. */
	private void drawIcon(DrawContext ctx, int x, int y) {
		Identifier icon = ModIcons.get(this.modId);
		if (icon == null) {
			return;
		}
		int radius = Math.max(2, ICON / 4);
		if (!Sdf.roundedTexture(ctx, icon, x, y, ICON, ICON, radius, -1)) {
			ctx.drawTexture(RenderPipelines.GUI_TEXTURED, icon,
				x, y, 0.0F, 0.0F, ICON, ICON, ICON, ICON);
		}
	}
}
