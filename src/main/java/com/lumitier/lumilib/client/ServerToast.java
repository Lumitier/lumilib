package com.lumitier.lumilib.client;

import com.lumitier.lumilib.client.gpu.Sdf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Hinweise oben rechts, wenn ein Server Mods ab- oder wieder einschaltet.
 * Eine Karte pro Mod.
 *
 * KEIN MINECRAFT-TOAST MEHR, UND DAS IST ABSICHT. Minecraft hat fuer Toasts
 * genau fuenf Plaetze zu je 32 Pixel. Beim Betreten eines Servers belegt
 * sein eigener Hinweis "Chat messages can't be verified" davon zwei - fuer
 * vier abgeschaltete Mods reichte es dann nicht, die vierte Karte kam erst
 * Sekunden spaeter. Deshalb zeichnet LumiLib seine Karten selbst: direkt
 * UNTER Minecrafts Toasts, ohne Platzgrenze.
 *
 * GEZEICHNET WIRD AUS ToastManager.draw HERAUS (siehe ToastManagerMixin):
 * genau dort, wo Minecraft seine Toasts malt - ueber dem Spiel UND ueber
 * jedem Menue, und mit F1 ausgeblendet wie diese.
 *
 * ALLE VERSCHWINDEN ZUSAMMEN: Es gibt eine gemeinsame Uhr. Jede neue Karte
 * schiebt sie auf "jetzt plus fuenf Sekunden". Steht fuer eine Mod schon
 * eine Karte, wird deren Text ersetzt statt eine zweite anzuhaengen.
 *
 * AUSSEHEN WIE IM HUB: dieselbe Glaskarte, dasselbe Icon, dieselbe Schrift.
 */
public final class ServerToast {

	private ServerToast() {}

	/** Wie lange die Hinweise nach der letzten Aenderung stehen. */
	private static final long DISPLAY_MS = 5000L;
	/** Rein- und Rausgleiten - wie bei Minecrafts Toasts. */
	private static final long SLIDE_MS = 350L;

	private static final int CARD_H = 40;
	private static final int CARD_GAP = 3;
	/** Hoehe eines Minecraft-Toast-Platzes. */
	private static final int SLOT = 32;

	private static final int MIN_W = 160;
	private static final int MAX_W = 240;
	private static final int PAD = 7;
	/** Rechts mehr Luft: Die Karte sitzt dort direkt am Bildschirmrand. */
	private static final int PAD_R = 11;
	private static final int ICON = 18;
	private static final int GAP = 7;

	private static final class Card {
		final String modId;
		String title;
		String line;
		boolean warn;
		int width;
		final long shownAt;

		Card(String modId, long now) {
			this.modId = modId;
			this.shownAt = now;
		}
	}

	private static final List<Card> CARDS = new ArrayList<>();

	/** Wann alle Karten anfangen zu verschwinden. */
	private static long hideAt;

	/**
	 * Wo der Stapel oben anfaengt. Gleitet weich nach, wenn Minecrafts
	 * eigene Toasts kommen oder gehen, statt zu springen.
	 */
	private static float top = -1;

	/**
	 * Meldung zeigen. Nur vom Render-Thread aufrufen.
	 *
	 * @param warn true fuer "abgeschaltet" - zweite Zeile rot, sonst gruen
	 */
	static void show(String modId, String title, Text line, boolean warn) {
		long now = Util.getMeasuringTimeMs();
		double mult = MinecraftClient.getInstance().getToastManager().getNotificationDisplayTimeMultiplier();

		// Laufen die Karten gerade aus, ist das ein neuer Durchgang: Die
		// alten nicht zuruecklocken, sondern gehen lassen und frisch anfangen.
		if (now >= hideAt) {
			CARDS.clear();
		}
		hideAt = now + (long) (DISPLAY_MS * mult);

		Card card = null;
		for (Card c : CARDS) {
			if (c.modId.equals(modId)) {
				card = c;
				break;
			}
		}
		if (card == null) {
			card = new Card(modId, now);
			CARDS.add(card);
		}

		// Text und Breite EINMAL hier, nicht in jedem Bild.
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		String l = line.getString();
		int textW = Math.max(Ui.widthBold(tr, title), Ui.width(tr, l));
		card.width = Math.max(MIN_W, Math.min(MAX_W, PAD + ICON + GAP + textW + PAD_R));
		int room = card.width - PAD - ICON - GAP - PAD_R;
		card.title = Ui.fitBold(tr, title, room);
		card.line = Ui.fit(tr, l, room);
		card.warn = warn;
	}

	/**
	 * Aus ToastManager.draw gerufen, NACH Minecrafts eigenen Toasts.
	 *
	 * @param occupiedSlots wie viele 32-Pixel-Plaetze Minecrafts Toasts von
	 *                      oben belegen - darunter beginnt der Stapel
	 */
	public static void draw(DrawContext ctx, int occupiedSlots) {
		if (CARDS.isEmpty()) {
			top = -1;
			return;
		}
		long now = Util.getMeasuringTimeMs();

		// Fertig ausgeblendet? Dann weg damit.
		if (now >= hideAt + SLIDE_MS) {
			CARDS.clear();
			top = -1;
			return;
		}

		float target = occupiedSlots * SLOT;
		top = top < 0 ? target : top + (target - top) * 0.2F;

		// Wie Minecraft: ueber allem zeichnen, was schon auf dem Schirm ist.
		ctx.createNewRootLayer();

		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		int screenW = ctx.getScaledWindowWidth();

		ScreenRect oldClip = Sdf.clip();
		Sdf.clip(null);
		try {
			float y = top;
			for (Iterator<Card> it = CARDS.iterator(); it.hasNext(); ) {
				Card c = it.next();
				float in = ease((now - c.shownAt) / (float) SLIDE_MS);
				float out = now < hideAt ? 1F : 1F - ease((now - hideAt) / (float) SLIDE_MS);
				float portion = Math.min(in, out);
				int x = Math.round(screenW - c.width * portion);

				ctx.getMatrices().pushMatrix();
				ctx.getMatrices().translate(x, Math.round(y));
				drawCard(ctx, tr, c);
				ctx.getMatrices().popMatrix();

				y += CARD_H + CARD_GAP;
			}
		} finally {
			Sdf.clip(oldClip);
		}
	}

	/** Weich ein- und auslaufend, 0 bis 1. */
	private static float ease(float t) {
		if (t <= 0F) {
			return 0F;
		}
		if (t >= 1F) {
			return 1F;
		}
		return 1F - (1F - t) * (1F - t) * (1F - t);
	}

	private static void drawCard(DrawContext ctx, TextRenderer tr, Card c) {
		int w = c.width;
		if (!Surface.glassCard(ctx, 0, 0, w, CARD_H, Theme.PANEL)) {
			Surface.card(ctx, 0, 0, w, CARD_H, Theme.PANEL);
			Surface.rimCard(ctx, 0, 0, w, CARD_H, Theme.RIM_CARD);
		}

		drawIcon(ctx, c.modId, PAD, (CARD_H - ICON) / 2);

		int tx = PAD + ICON + GAP;
		// Beide Zeilen in normaler Schrift, der Block aus beiden steht
		// senkrecht in der Mitte der Karte - dieselben Masse wie in der
		// Fassung, die du abgenommen hast.
		int gap = Ui.advance(Ui.BODY_H, Ui.BODY_D, 3);
		int block = gap + Ui.BODY_H;
		int top = (CARD_H - block) / 2;
		Ui.drawTop(ctx, tr, Ui.bold(c.title), tx, top, Ui.TOP_BODY, Theme.TEXT);
		Ui.drawTop(ctx, tr, Ui.text(c.line), tx, top + gap, Ui.TOP_BODY,
			c.warn ? Theme.WARN : Theme.OK);
	}

	/** Icon der Mod, wie im Hub - fehlt es, bleibt die Stelle frei. */
	private static void drawIcon(DrawContext ctx, String modId, int x, int y) {
		Identifier icon = ModIcons.get(modId);
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
