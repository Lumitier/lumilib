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

		// Der Block aus beiden Zeilen, SICHTBAR gemittet.
		//
		// Gemessen wird von der Oberkante der Versalien der ersten Zeile
		// bis zur Schreiblinie der zweiten. Die Unterlaenge haengt
		// absichtlich in die untere Luft - zaehlte sie mit, saesse eine
		// Karte mit "Enabled again" anders als eine mit "Enabled".
		//
		// Und der Rand des Abstandsfeldes wird abgezogen: drawTop
		// bekommt die Kastenkante, nicht die sichtbare Oberkante. Ohne
		// das lag der Block rund zwei Pixel zu tief, und oben klaffte
		// mehr Luft als unten.
		int gap = Ui.advance(Ui.BODY_H, Ui.BODY_D, 3);
		float capH = Ui.capHeightOf(Ui.TOP_BODY, false);
		float visible = gap + capH;
		float visTop = (CARD_H - visible) / 2.0F;
		float topF = visTop - Ui.inkTopOffsetOf(Ui.TOP_BODY, false);
		int top = (int) Math.floor(topF);

		// Symbol auf die Mitte desselben Blocks. Bei richtig gemitteltem
		// Text ist das zugleich die Mitte der Karte - aber gerechnet
		// bleibt es richtig, auch wenn sich an den Zeilenmassen etwas
		// aendert.
		float iconYf = visTop + visible / 2.0F - ICON / 2.0F;
		drawIcon(ctx, c.modId, PAD, iconYf);

		// Der halbe Pixel wandert in die Matrix statt in die Rundung.
		// Bei Schrift aus einem Abstandsfeld kostet das keine Schaerfe:
		// Die Kante wird ohnehin in der Zielgroesse gerechnet.
		int tx = PAD + ICON + GAP;
		var textPose = ctx.getMatrices();
		textPose.pushMatrix();
		textPose.translate(0.0F, topF - top);
		Ui.drawTop(ctx, tr, Ui.bold(c.title), tx, top, Ui.TOP_BODY, Theme.TEXT);
		Ui.drawTop(ctx, tr, Ui.text(c.line), tx, top + gap, Ui.TOP_BODY,
			c.warn ? Theme.WARN : Theme.OK);
		textPose.popMatrix();
	}

	/**
	 * Icon der Mod - erst das MITGELIEFERTE, dann das aus dem Jar.
	 *
	 * WARUM IN DIESER REIHENFOLGE:
	 * Das Icon im Jar ist das der INSTALLIERTEN Fassung. Wer eine Mod
	 * noch in einer aelteren Version laufen hat, bekam deshalb deren
	 * altes Bild - und in einer Reihe von vier Karten untereinander
	 * standen dann drei alte Bilder neben einem neuen. Die mitgelieferten
	 * Icons sind dagegen bei allen eigenen Mods derselbe Satz.
	 *
	 * Fuer fremde Mods gibt es keines, da greift weiter das aus dem Jar.
	 *
	 * Die Hoehe kommt als Fliesskomma: Die richtige Stelle liegt selten
	 * auf einem ganzen Pixel, und der Rest wird ueber die Matrix
	 * geschoben statt weggerundet.
	 */
	private static void drawIcon(DrawContext ctx, String modId, int x, float yf) {
		Identifier icon = CatalogIcons.get(modId);
		if (icon == null) {
			icon = ModIcons.get(modId);
		}
		if (icon == null) {
			return;
		}
		int y = (int) Math.floor(yf);
		int radius = Math.max(2, ICON / 4);
		var pose = ctx.getMatrices();
		pose.pushMatrix();
		pose.translate(0.0F, yf - y);
		if (!Sdf.roundedTexture(ctx, icon, x, y, ICON, ICON, radius, -1)) {
			ctx.drawTexture(RenderPipelines.GUI_TEXTURED, icon,
				x, y, 0.0F, 0.0F, ICON, ICON, ICON, ICON);
		}
		pose.popMatrix();
	}
}
