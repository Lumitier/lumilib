package com.lumitier.lumilib.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Die Figur rechts neben den Einstellungen (SettingsPage.preview).
 *
 * Gedacht fuer Mods, die etwas am Aussehen aendern: Man dreht am Regler
 * und sieht es sofort, ohne den Hub zu verlassen und F5 zu druecken.
 *
 * ZEICHNEN: wie Minecrafts Inventar-Figur (InventoryScreen.drawEntity),
 * aber mit eigener Drehung statt "schaut zur Maus". Die Figur steht still
 * und dreht sich nur, wenn man sie mit der Maus zieht - eine Figur, die
 * staendig dem Zeiger nachschaut, waehrend man Regler bewegt, lenkt ab.
 *
 * WARUM DIE ECKEN DURCH DIE MATRIX GEHEN: Minecraft zeichnet Figuren in
 * der Oberflaeche ueber einen eigenen Weg, der die aktuelle Matrix NICHT
 * beachtet - er nimmt die Ecken so, wie sie uebergeben werden. Der Hub ist
 * aber skaliert (ScaledScreen) und beim Oeffnen zusaetzlich gezoomt. Ohne
 * Umrechnung stuende die Figur also daneben und waere zu gross oder zu
 * klein.
 */
final class Preview {

	private Preview() {}

	/** Leicht von schraeg vorne - so sieht man Vorder- und Seitenansicht. */
	static final float DEFAULT_YAW = -25.0F;

	/** Aktuelle Drehung in Grad. Bleibt ueber Seitenwechsel erhalten. */
	static float yaw = DEFAULT_YAW;
	/**
	 * Neigung in Grad: positiv = von oben, negativ = von unten. Begrenzt,
	 * damit die Figur nicht kopfueber steht - von ganz oben oder unten
	 * sieht man ohnehin nichts mehr.
	 */
	static float pitch = 0.0F;
	private static final float PITCH_MIN = -50.0F;
	private static final float PITCH_MAX = 70.0F;

	private static boolean dragging;
	private static int lastX;
	private static int lastY;

	/**
	 * Ruestung zum Anprobieren - damit man sieht, wie eine Mod mit jeder
	 * Ruestung aussieht, ohne sie erst im Spiel zu besorgen. Der erste
	 * Eintrag laesst alles, wie es ist (im Spiel: die echte Ruestung).
	 */
	private enum Armor {
		WORN(null, null, null, null),
		NONE(null, null, null, null),
		LEATHER(net.minecraft.item.Items.LEATHER_HELMET, net.minecraft.item.Items.LEATHER_CHESTPLATE,
			net.minecraft.item.Items.LEATHER_LEGGINGS, net.minecraft.item.Items.LEATHER_BOOTS),
		CHAINMAIL(net.minecraft.item.Items.CHAINMAIL_HELMET, net.minecraft.item.Items.CHAINMAIL_CHESTPLATE,
			net.minecraft.item.Items.CHAINMAIL_LEGGINGS, net.minecraft.item.Items.CHAINMAIL_BOOTS),
		COPPER(net.minecraft.item.Items.COPPER_HELMET, net.minecraft.item.Items.COPPER_CHESTPLATE,
			net.minecraft.item.Items.COPPER_LEGGINGS, net.minecraft.item.Items.COPPER_BOOTS),
		IRON(net.minecraft.item.Items.IRON_HELMET, net.minecraft.item.Items.IRON_CHESTPLATE,
			net.minecraft.item.Items.IRON_LEGGINGS, net.minecraft.item.Items.IRON_BOOTS),
		GOLD(net.minecraft.item.Items.GOLDEN_HELMET, net.minecraft.item.Items.GOLDEN_CHESTPLATE,
			net.minecraft.item.Items.GOLDEN_LEGGINGS, net.minecraft.item.Items.GOLDEN_BOOTS),
		DIAMOND(net.minecraft.item.Items.DIAMOND_HELMET, net.minecraft.item.Items.DIAMOND_CHESTPLATE,
			net.minecraft.item.Items.DIAMOND_LEGGINGS, net.minecraft.item.Items.DIAMOND_BOOTS),
		NETHERITE(net.minecraft.item.Items.NETHERITE_HELMET, net.minecraft.item.Items.NETHERITE_CHESTPLATE,
			net.minecraft.item.Items.NETHERITE_LEGGINGS, net.minecraft.item.Items.NETHERITE_BOOTS),
		// Den Schildkroetenpanzer gibt es nur als Helm.
		TURTLE(net.minecraft.item.Items.TURTLE_HELMET, null, null, null);

		final net.minecraft.item.Item head, chest, legs, feet;
		private net.minecraft.item.ItemStack[] stacks;

		Armor(net.minecraft.item.Item head, net.minecraft.item.Item chest,
		      net.minecraft.item.Item legs, net.minecraft.item.Item feet) {
			this.head = head;
			this.chest = chest;
			this.legs = legs;
			this.feet = feet;
		}

		/** Kopf, Brust, Beine, Fuesse - einmal angelegt und dann wiederverwendet. */
		net.minecraft.item.ItemStack[] stacks() {
			if (stacks == null) {
				stacks = new net.minecraft.item.ItemStack[] {
					stack(head), stack(chest), stack(legs), stack(feet)};
			}
			return stacks;
		}

		private static net.minecraft.item.ItemStack stack(net.minecraft.item.Item item) {
			return item == null ? net.minecraft.item.ItemStack.EMPTY : new net.minecraft.item.ItemStack(item);
		}

		String key() {
			return "lumilib.preview.armor." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private static Armor armor = Armor.WORN;
	private static int armorX, armorY, armorW;

	/**
	 * Laufen: spielt die Lauf-Animation ab, damit man sieht, wie Mods sich
	 * in Bewegung verhalten (z. B. Physik) - auch im Hauptmenue, wo die
	 * Figur sonst nur still dasteht.
	 */
	private static boolean running;
	private static long runStart;
	/** Sprung: Startzeit, oder -1, wenn gerade keiner laeuft. */
	private static long jumpStart = -1L;
	private static final long JUMP_MS = 600L;
	/** Hoehe des Sprungs in Bloecken - wie ein echter Sprung. */
	private static final float JUMP_HEIGHT = 1.25F;
	/** Wie hoch die Figur dabei sichtbar hochgeht. Weniger als echt, sonst passt sie nicht in die Karte. */
	private static final float JUMP_SHOWN = 0.35F;

	/** Knoepfe unter der Figur - Lage aus dem letzten Bild, fuer den Klick. */
	private static int runX, runY, runW, jumpX, jumpY, jumpW, buttonH;

	/** Breite der Spalte fuer eine Seite der Breite w, oder 0, wenn kein Platz ist. */
	static int columnWidth(int w) {
		int pw = Math.max(120, Math.min(180, (int) (w * 0.3F)));
		// Die Einstellungen brauchen ihren Platz mehr als die Figur.
		return w - pw - Metrics.CARD_GAP >= 300 ? pw : 0;
	}

	/**
	 * Klick in die Vorschau: Knopf getroffen? Sonst beginnt das Drehen -
	 * oder, bei einem Doppelklick, stellt sich die Figur zurueck.
	 */
	static void click(int mouseX, int mouseY, boolean doubled) {
		if (buttonH > 0 && Draw.inside(mouseX, mouseY, runX, runY, runW, buttonH)) {
			running = !running;
			runStart = System.currentTimeMillis();
			return;
		}
		if (buttonH > 0 && Draw.inside(mouseX, mouseY, jumpX, jumpY, jumpW, buttonH)) {
			if (jumpStart < 0L) {
				jumpStart = System.currentTimeMillis();
			}
			return;
		}
		if (buttonH > 0 && Draw.inside(mouseX, mouseY, armorX, armorY, armorW, buttonH)) {
			Armor[] all = Armor.values();
			armor = all[(armor.ordinal() + 1) % all.length];
			return;
		}
		if (doubled) {
			reset();
			return;
		}
		startDrag(mouseX, mouseY);
	}

	static void startDrag(int mouseX, int mouseY) {
		dragging = true;
		lastX = mouseX;
		lastY = mouseY;
	}

	static void reset() {
		yaw = DEFAULT_YAW;
		pitch = 0.0F;
	}

	/**
	 * In jedem Bild: Ziehen weiterfuehren oder beenden. Seitwaerts dreht
	 * die Figur, hoch und runter neigt sie - so sieht man sie von allen
	 * Seiten, auch von oben und unten.
	 */
	static void updateDrag(int mouseX, int mouseY, boolean mouseDown) {
		if (!dragging) {
			return;
		}
		if (!mouseDown) {
			dragging = false;
			return;
		}
		yaw += (mouseX - lastX) * 1.2F;
		pitch = Math.max(PITCH_MIN, Math.min(PITCH_MAX, pitch + (mouseY - lastY) * 0.8F));
		lastX = mouseX;
		lastY = mouseY;
		if (yaw > 180.0F) {
			yaw -= 360.0F;
		} else if (yaw < -180.0F) {
			yaw += 360.0F;
		}
	}

	static void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h,
	                   LivingEntity entity, boolean player, String playerName,
	                   int mouseX, int mouseY) {
		buttonH = 0;
		if (!Surface.glassCard(ctx, x, y, w, h, Theme.CARD)) {
			Surface.card(ctx, x, y, w, h, Theme.CARD);
			Surface.rimCard(ctx, x, y, w, h, Theme.RIM_CARD);
		}

		MinecraftClient client = MinecraftClient.getInstance();
		EntityRenderState state;
		if (entity != null) {
			try {
				state = client.getEntityRenderDispatcher().getAndUpdateRenderState(entity, 1.0F);
			} catch (Throwable t) {
				hint(ctx, tr, x, y, w, h, "lumilib.preview.not_near");
				return;
			}
		} else if (player) {
			// Kein Spieler geladen (Hauptmenue, offline, zu weit weg): eine
			// Figur mit seinem Skin.
			state = MenuPlayer.state(playerName);
		} else {
			hint(ctx, tr, x, y, w, h,
				client.world == null ? "lumilib.preview.no_world" : "lumilib.preview.not_near");
			return;
		}

		// Wie in Minecrafts Inventar: volles Licht, kein Schatten, kein
		// Umriss - und kein Namensschild ueber dem Kopf.
		state.light = 15728880;
		state.shadowPieces.clear();
		state.outlineColor = EntityRenderState.NO_OUTLINE;
		state.displayName = null;
		state.nameLabelPos = null;
		// Die Figur steht direkt vor einem - Mods, die nach Entfernung
		// ausblenden (Sichtweite), sollen sie trotzdem zeigen.
		state.squaredDistanceToCamera = 0.0;
		long now = System.currentTimeMillis();
		if (armor != Armor.WORN
				&& state instanceof net.minecraft.client.render.entity.state.BipedEntityRenderState biped) {
			net.minecraft.item.ItemStack[] set = armor.stacks();
			biped.equippedHeadStack = set[0];
			biped.equippedChestStack = set[1];
			biped.equippedLegsStack = set[2];
			biped.equippedFeetStack = set[3];
		}
		if (state instanceof LivingEntityRenderState living) {
			living.bodyYaw = 180.0F + yaw;
			living.relativeHeadYaw = 0.0F;
			living.pitch = 0.0F;
			living.width /= living.baseScale;
			living.height /= living.baseScale;
			living.baseScale = 1.0F;
			if (running) {
				// Wie beim Sprinten: volle Schrittweite, gut ein Schritt
				// pro Tick. Arme und Beine schwingen daraus von selbst.
				living.limbSwingAnimationProgress = (now - runStart) / 50.0F * 0.9F;
				living.limbSwingAmplitude = 1.0F;
			}
		}

		// Sprung: eine Parabel. Die echte Hoehe geht in den Zustand - Mods,
		// die auf Bewegung reagieren (Physik), sehen einen echten Sprung.
		// Sichtbar geht die Figur nur ein Stueck hoch, damit sie in der
		// Karte bleibt.
		float jump = 0.0F;
		if (jumpStart >= 0L) {
			float p = (now - jumpStart) / (float) JUMP_MS;
			if (p >= 1.0F) {
				jumpStart = -1L;
			} else {
				jump = 4.0F * p * (1.0F - p);
				state.y += jump * JUMP_HEIGHT;
			}
		}

		// Oben der Hinweis, unten die Knoepfe.
		int hintH = Ui.SMALL_H + 12;
		int buttonsH = 18 * 2 + 6 + 16;
		int areaTop = y + hintH + 4;
		int areaBottom = y + h - buttonsH;
		float tall = Math.max(0.5F, state.height + JUMP_SHOWN + 0.1F);
		float wide = Math.max(0.5F, state.width * 2.0F);
		float size = Math.min((areaBottom - areaTop) / tall, (w - 16) / wide);

		// Ecken und Groesse in echte Bildschirmkoordinaten umrechnen -
		// siehe Klassenkommentar.
		Matrix3x2f pose = ctx.getMatrices();
		Vector2f a = pose.transformPosition(x, areaTop, new Vector2f());
		Vector2f b = pose.transformPosition(x + w, areaBottom, new Vector2f());
		float k = (b.x - a.x) / Math.max(1.0F, w);

		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf camera = new Quaternionf().rotateX((8.0F + pitch) * ((float) Math.PI / 180.0F));
		rotation.mul(camera);
		// Die Figur steht etwas tiefer als mittig, damit oben Platz fuer
		// den Sprung bleibt. Positives y schiebt sie nach unten.
		Vector3f offset = new Vector3f(0.0F,
			state.height / 2.0F + 0.0625F - JUMP_SHOWN / 2.0F - jump * JUMP_SHOWN, 0.0F);

		ctx.addEntity(state, size * k, offset, rotation, camera,
			Math.round(a.x), Math.round(a.y), Math.round(b.x), Math.round(b.y));

		String drag = Ui.fitSmall(tr, Text.translatable("lumilib.preview.drag").getString(), w - 12);
		int tw = Ui.widthSmall(tr, drag);
		Ui.drawTop(ctx, tr, Ui.small(drag), x + (w - tw) / 2, y + 8,
			Ui.TOP_SMALL, Theme.TEXT_FAINT);

		// Zwei Knoepfe nebeneinander: Laufen (an/aus) und Springen.
		buttonH = 18;
		int gap = 6;
		int bw = (w - 16 - gap) / 2;
		int by = y + h - buttonH * 2 - gap - 8;
		runX = x + 8;
		runY = by;
		runW = bw;
		jumpX = runX + bw + gap;
		jumpY = by;
		jumpW = bw;
		button(ctx, tr, runX, runY, runW, buttonH, mouseX, mouseY,
			running ? "lumilib.preview.stop" : "lumilib.preview.run", running);
		button(ctx, tr, jumpX, jumpY, jumpW, buttonH, mouseX, mouseY,
			"lumilib.preview.jump", jumpStart >= 0L);

		// Darunter, ueber die ganze Breite: Ruestung durchschalten.
		armorX = runX;
		armorY = by + buttonH + gap;
		armorW = w - 16;
		String armorLabel = Text.translatable("lumilib.preview.armor",
			Text.translatable(armor.key())).getString();
		buttonText(ctx, tr, armorX, armorY, armorW, buttonH, mouseX, mouseY, armorLabel,
			armor != Armor.WORN);
	}

	private static void button(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h,
	                           int mouseX, int mouseY, String key, boolean active) {
		buttonText(ctx, tr, x, y, w, h, mouseX, mouseY, Text.translatable(key).getString(), active);
	}

	private static void buttonText(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h,
	                               int mouseX, int mouseY, String text, boolean active) {
		boolean hover = Draw.inside(mouseX, mouseY, x, y, w, h);
		int fill = hover || active ? Theme.CARD_HOVER : Theme.CARD;
		if (!Surface.glassCard(ctx, x, y, w, h, fill)) {
			Surface.card(ctx, x, y, w, h, fill);
		}
		String label = Ui.fitSmall(tr, text, w - 8);
		int lw = Ui.widthSmall(tr, label);
		Ui.drawCenteredLine(ctx, tr, Ui.small(label), x + (w - lw) / 2, y + h / 2,
			Ui.SMALL_H, Ui.SMALL_D, Ui.TOP_SMALL,
			hover || active ? Theme.TEXT : Theme.TEXT_MUTED);
	}

	private static void hint(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String key) {
		java.util.List<String> lines = Ui.wrap(tr, Text.translatable(key).getString(), w - 20);
		int lineH = Ui.advance(Ui.BODY_H, Ui.BODY_D, 3);
		int ty = y + (h - lines.size() * lineH) / 2;
		for (String line : lines) {
			int tw = Ui.width(tr, line);
			Ui.drawTop(ctx, tr, Ui.text(line), x + (w - tw) / 2, ty, Ui.TOP_BODY, Theme.TEXT_FAINT);
			ty += lineH;
		}
	}
}
