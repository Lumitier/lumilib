package com.lumitier.lumilib.client;

import com.lumitier.lumilib.LumiLib;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Der LumiLib-Knopf im Hauptmenue und im Pausemenue.
 *
 *  - Hauptmenue: eine breite Leiste UNTER "Options..." und "Quit Game".
 *  - Pausemenue: ein kleiner Symbol-Knopf LINKS neben "Back to Game"
 *    (ist dort schon etwas, rechts daneben). Nichts wird verschoben.
 *
 * WARUM IM PAUSEMENUE NICHTS MEHR VERSCHOBEN WIRD: Frueher rutschte alles
 * unter "Back to Game" eine Zeile nach unten, um Platz fuer eine breite
 * Leiste zu machen. Mit vielen Mods, die selbst Knoepfe ins Pausemenue
 * setzen oder es umbauen (FancyMenu, Essential, Flashback ...), landete
 * "Disconnect" dabei unter einem fremden Knopf oder unter dem Bildrand -
 * er war nicht mehr zu sehen. Ein Knopf neben "Back to Game" braucht
 * keinen Platz von anderen.
 *
 * OHNE MIXIN: Fabrics Screen-API meldet, wenn ein Bildschirm aufgebaut ist,
 * und gibt die Liste seiner Knoepfe heraus. Gesucht wird ueber die
 * Uebersetzungsschluessel (menu.quit, menu.returnToGame), nicht ueber
 * feste Positionen - so passt es auch, wenn Mod Menu oder andere Mods das
 * Menue umbauen.
 *
 * EIGENE, SPAETERE PHASE: Mod Menu fuegt seinen "Mods"-Knopf ueber genau
 * dasselbe Ereignis ein. Wer zuerst dran ist, haengt sonst von der
 * Ladereihenfolge ab - und waere LumiLib zuerst, wuerde "Mods" nicht mit
 * nach unten verschoben und laege auf einem anderen Knopf.
 *
 * Fehlt ein gesuchter Knopf (anderer Mod hat das Menue ersetzt), kommt
 * einfach kein LumiLib-Knopf - nie ein Absturz.
 */
public final class MenuButtons {

	private MenuButtons() {}

	private static final Identifier LATE = Identifier.of(LumiLib.MOD_ID, "after_other_mods");
	private static final Identifier ICON = Identifier.of(LumiLib.MOD_ID, "textures/gui/menu_button.png");
	private static final int ROW = 24;

	public static void init() {
		ScreenEvents.AFTER_INIT.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
		ScreenEvents.AFTER_INIT.register(LATE, (client, screen, w, h) -> {
			if (!LumiLib.cfg.menuButton) {
				return;
			}
			try {
				if (screen instanceof TitleScreen) {
					addToTitle(screen);
				} else if (screen instanceof GameMenuScreen) {
					addToPause(screen);
				}
			} catch (Throwable t) {
				LumiLib.LOGGER.warn("[LumiLib] Could not add the menu button.", t);
			}
		});
	}

	private static void addToTitle(Screen screen) {
		List<ClickableWidget> buttons = Screens.getButtons(screen);
		ClickableWidget options = find(buttons, "menu.options");
		ClickableWidget quit = find(buttons, "menu.quit");
		if (options == null || quit == null) {
			return;
		}
		int x = Math.min(options.getX(), quit.getX());
		int right = Math.max(options.getRight(), quit.getRight());
		int y = Math.max(options.getY(), quit.getY()) + ROW;
		// Hat ein anderer Mod dort schon etwas hingesetzt: nicht darueber.
		if (free(buttons, x, y, right - x) && y + 20 <= screen.height) {
			buttons.add(new LumiButton(x, y, right - x, screen));
		}
	}

	private static void addToPause(Screen screen) {
		List<ClickableWidget> buttons = Screens.getButtons(screen);
		ClickableWidget back = find(buttons, "menu.returnToGame");
		if (back == null) {
			return;
		}
		int y = back.getY();
		int left = back.getX() - GAP_SIDE - SQUARE;
		int right = back.getRight() + GAP_SIDE;
		// Links bevorzugt - rechts steht bei manchen Mods schon etwas
		// (Mod Menu setzt dort z. B. sein Symbol, wenn so eingestellt).
		if (left >= 0 && free(buttons, left, y, SQUARE)) {
			buttons.add(new LumiButton(left, y, SQUARE, screen));
		} else if (right + SQUARE <= screen.width && free(buttons, right, y, SQUARE)) {
			buttons.add(new LumiButton(right, y, SQUARE, screen));
		}
		// Beides belegt: lieber kein Knopf als einer, der etwas verdeckt.
		// Der Hub ist weiter ueber die Taste (L) erreichbar.
	}

	private static final int SQUARE = 20;
	private static final int GAP_SIDE = 4;

	/** Liegt an dieser Stelle (w breit, 20 hoch) noch kein anderer Knopf? */
	private static boolean free(List<ClickableWidget> buttons, int x, int y, int w) {
		for (ClickableWidget b : buttons) {
			if (!b.visible) {
				continue;
			}
			boolean overlapX = x < b.getRight() && x + w > b.getX();
			boolean overlapY = y < b.getBottom() && y + SQUARE > b.getY();
			if (overlapX && overlapY) {
				return false;
			}
		}
		return true;
	}

	private static ClickableWidget find(List<ClickableWidget> buttons, String key) {
		for (ClickableWidget b : buttons) {
			if (b.getMessage().getContent() instanceof TranslatableTextContent t && key.equals(t.getKey())) {
				return b;
			}
		}
		return null;
	}

	/**
	 * Vanilla-Knopf: "LumiLib", rechts daneben das Symbol, beides zusammen
	 * mittig.
	 *
	 * SYMBOL 12 PIXEL bei 20 Pixeln Knopfhoehe: 4 Pixel Luft oben und unten,
	 * wie bei den Symbolen auf Vanillas eigenen Knoepfen. 16 fuellte den
	 * Knopf fast ganz aus.
	 *
	 * net.minecraft.text.Text VOLL AUSGESCHRIEBEN: In einer Unterklasse von
	 * ButtonWidget meint "Text" sonst ButtonWidget.Text, eine innere Klasse.
	 */
	private static final class LumiButton extends ButtonWidget {

		private static final int ICON_SIZE = 12;
		private static final int GAP = 5;

		LumiButton(int x, int y, int width, Screen parent) {
			super(x, y, width, 20, net.minecraft.text.Text.literal("LumiLib"),
				b -> MinecraftClient.getInstance().setScreen(new LumiHubScreen(parent)),
				DEFAULT_NARRATION_SUPPLIER);
			if (iconOnly()) {
				// Ohne Beschriftung sagt der Hinweis beim Draufzeigen, was es ist.
				setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(net.minecraft.text.Text.literal("LumiLib")));
			}
		}

		/** Zu schmal fuer Schrift: nur das Symbol, mittig. */
		private boolean iconOnly() {
			return getWidth() < 40;
		}

		@Override
		protected void drawIcon(DrawContext ctx, int mouseX, int mouseY, float delta) {
			// DECKKRAFT DES BILDSCHIRMS MITNEHMEN.
			//
			// Der Titelbildschirm blendet beim Start alles langsam ein: Er
			// ruft setWidgetAlpha, und jedes Bedienelement zeichnet sich mit
			// diesem Wert. Vanillas Knoepfe tun das von selbst, eine eigene
			// drawIcon NICHT - die zeichnet, was dort steht.
			//
			// Stand hier eine feste Deckkraft, war die Beschriftung samt
			// Symbol von der ersten Zehntelsekunde an voll sichtbar,
			// waehrend Minecrafts eigene Knoepfe noch durchsichtig waren.
			// Die Leiste war also da, bevor es die anderen Knoepfe gab.
			//
			// drawButton ist Vanillas eigener Aufruf und nimmt die
			// Deckkraft schon mit; nur was danach kommt, muss es selbst tun.
			int a = Math.round(this.alpha * 255.0F);
			if (a <= 0) {
				return;
			}
			a = Math.min(255, a);
			drawButton(ctx);

			if (iconOnly()) {
				ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ICON, getX() + (getWidth() - ICON_SIZE) / 2,
					getY() + (getHeight() - ICON_SIZE) / 2, 0.0F, 0.0F,
					ICON_SIZE, ICON_SIZE, 16, 16, 16, 16,
					(a << 24) | 0xFFFFFF);
				return;
			}
			TextRenderer tr = MinecraftClient.getInstance().textRenderer;
			int tw = tr.getWidth(getMessage());
			int x0 = getX() + (getWidth() - (tw + GAP + ICON_SIZE)) / 2;
			int rgb = this.active ? 0xFFFFFF : 0xA0A0A0;
			ctx.drawTextWithShadow(tr, getMessage(), x0,
				getY() + (getHeight() - 8) / 2, (a << 24) | rgb);
			// Quelle 16x16, gezeichnet auf 12x12.
			ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ICON, x0 + tw + GAP,
				getY() + (getHeight() - ICON_SIZE) / 2, 0.0F, 0.0F,
				ICON_SIZE, ICON_SIZE, 16, 16, 16, 16,
				(a << 24) | 0xFFFFFF);
		}
	}
}
