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
 *  - Pausemenue: eine breite Leiste direkt UNTER "Back to Game", alles
 *    darunter rutscht eine Zeile nach unten.
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
		buttons.add(new LumiButton(x, y, right - x, screen));
	}

	private static void addToPause(Screen screen) {
		List<ClickableWidget> buttons = Screens.getButtons(screen);
		ClickableWidget back = find(buttons, "menu.returnToGame");
		if (back == null) {
			return;
		}
		// Alles unter "Back to Game" eine Zeile nach unten.
		for (ClickableWidget b : buttons) {
			if (b.getY() > back.getY()) {
				b.setY(b.getY() + ROW);
			}
		}
		buttons.add(new LumiButton(back.getX(), back.getY() + ROW, back.getWidth(), screen));
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
		}

		@Override
		protected void drawIcon(DrawContext ctx, int mouseX, int mouseY, float delta) {
			drawButton(ctx);
			TextRenderer tr = MinecraftClient.getInstance().textRenderer;
			int tw = tr.getWidth(getMessage());
			int x0 = getX() + (getWidth() - (tw + GAP + ICON_SIZE)) / 2;
			ctx.drawTextWithShadow(tr, getMessage(), x0,
				getY() + (getHeight() - 8) / 2, this.active ? 0xFFFFFFFF : 0xFFA0A0A0);
			// Quelle 16x16, gezeichnet auf 12x12.
			ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ICON, x0 + tw + GAP,
				getY() + (getHeight() - ICON_SIZE) / 2, 0.0F, 0.0F,
				ICON_SIZE, ICON_SIZE, 16, 16, 16, 16);
		}
	}
}
