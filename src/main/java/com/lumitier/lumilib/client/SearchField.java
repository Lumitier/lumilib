package com.lumitier.lumilib.client;

import com.lumitier.lumilib.client.gpu.SdfText;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import static com.lumitier.lumilib.LumiLib.MOD_ID;

/**
 * Eigenes Suchfeld.
 *
 * WARUM NICHT MINECRAFTS TEXTFELD:
 * TextFieldWidget zeichnet seinen Text mit Minecrafts eigenem
 * Schriftrenderer und seiner eigenen senkrechten Konvention. Dadurch sass
 * die Beschriftung "Search" sichtbar hoeher als alles andere im Fenster -
 * und am Widget herumzuschieben waere Herumraten an fremdem Code
 * gewesen. Hier wird derselbe Weg benutzt wie ueberall sonst: das
 * Abstandsfeld ueber Ui, mit derselben Mittenregel.
 *
 * BEWUSST SCHLICHT:
 * Keine Auswahl, kein Zwischenspeicher, kein Bildlauf im Feld. Ein
 * Suchfeld fuer eine Modliste braucht das nicht. Was es braucht:
 * schreiben, loeschen, Pfeiltasten, Anfang und Ende, Escape zum Leeren.
 */
public final class SearchField {

	private final StringBuilder text = new StringBuilder();
	private int cursor;
	private boolean focused;

	private int x, y, w, h;

	public String text() {
		return text.toString();
	}

	public boolean focused() {
		return focused;
	}

	public void setFocused(boolean value) {
		this.focused = value;
	}

	public void clear() {
		text.setLength(0);
		cursor = 0;
	}

	/**
	 * Zeichnet das Feld und merkt sich seine Lage fuer Klicks.
	 *
	 * Die Lage wird hier gesetzt und nicht getrennt gepflegt - eine
	 * Quelle, kein Nachziehen an zwei Stellen.
	 */
	public void render(DrawContext ctx, TextRenderer tr,
	                   int x, int y, int w, int h, int mouseX, int mouseY) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;

		boolean hover = Draw.inside(mouseX, mouseY, x, y, w, h);
		int fill = focused ? Theme.FIELD_FOCUS : (hover ? Theme.FIELD_HOVER : Theme.FIELD);
		if (!Surface.glassCard(ctx, x, y, w, h, fill)) {
			Surface.row(ctx, x, y, w, h, fill);
		}

		int icon = Metrics.SEARCH_ICON;
		int mid = y + h / 2;
		// IMMER SCHWARZ, nicht gedaempft.
		//
		// Die Lupe war vorher nur im Fokus dunkel. Als graues Zeichen neben
		// grauem Platzhaltertext verschwand sie - man sah ein Feld, aber
		// nicht, dass es ein Suchfeld ist. Bei Walksy ist sie durchgehend
		// kraeftig, und das ist der Grund dafuer.
		Surface.icon(ctx, Identifier.of(MOD_ID, "search"),
			x + 7, mid - icon / 2, icon, Theme.TEXT);

		int tx = x + 7 + icon + 5;
		int room = x + w - 7 - tx;
		String shown = text.toString();

		if (shown.isEmpty() && !focused) {
			Ui.drawCenteredLine(ctx, tr,
				Ui.of(Text.translatable("lumilib.search")),
				tx, mid, Ui.BODY_H, Ui.BODY_D, Ui.TOP_BODY, Theme.TEXT_FAINT);
			return;
		}

		Ui.drawCenteredLine(ctx, tr, Ui.text(Ui.fit(tr, shown, room)),
			tx, mid, Ui.BODY_H, Ui.BODY_D, Ui.TOP_BODY, Theme.TEXT);

		// Schreibmarke: blinkt im Sekundentakt, sitzt hinter dem Text bis
		// zur Marke. Gemessen wird ueber dieselbe Quelle wie gezeichnet.
		if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = tx + Ui.width(tr, shown.substring(0, Math.min(cursor, shown.length())));
			Draw.vLine(ctx, Math.min(cx, x + w - 8),
				mid - (Ui.BODY_H + Ui.BODY_D) / 2, Ui.BODY_H + Ui.BODY_D, Theme.TEXT);
		}
	}

	/** @return true, wenn der Klick das Feld getroffen hat. */
	public boolean mouseClicked(double mx, double my) {
		boolean hit = Draw.inside(mx, my, x, y, w, h);
		focused = hit;
		if (hit) {
			cursor = text.length();
		}
		return hit;
	}

	public boolean keyPressed(KeyInput input) {
		if (!focused) {
			return false;
		}
		// Strg+V: einfuegen an der Schreibmarke. Zeilenumbrueche und
		// Steuerzeichen werden zu Leerzeichen, gekuerzt auf die Hoechstlaenge.
		if (input.isPaste()) {
			String clip = net.minecraft.client.MinecraftClient.getInstance().keyboard.getClipboard();
			if (clip != null) {
				String clean = clip.replaceAll("\\p{Cntrl}+", " ");
				int room = 48 - text.length();
				if (room > 0 && !clean.isEmpty()) {
					String add = clean.substring(0, Math.min(room, clean.length()));
					text.insert(cursor, add);
					cursor += add.length();
				}
			}
			return true;
		}
		int key = input.getKeycode();
		switch (key) {
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (cursor > 0) {
					text.deleteCharAt(cursor - 1);
					cursor--;
				}
				return true;
			}
			case GLFW.GLFW_KEY_DELETE -> {
				if (cursor < text.length()) {
					text.deleteCharAt(cursor);
				}
				return true;
			}
			case GLFW.GLFW_KEY_LEFT -> {
				cursor = Math.max(0, cursor - 1);
				return true;
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				cursor = Math.min(text.length(), cursor + 1);
				return true;
			}
			case GLFW.GLFW_KEY_HOME -> {
				cursor = 0;
				return true;
			}
			case GLFW.GLFW_KEY_END -> {
				cursor = text.length();
				return true;
			}
			case GLFW.GLFW_KEY_ESCAPE -> {
				// Erst leeren, dann den Fokus abgeben. Zweimal Escape
				// schliesst dann wie gewohnt den Bildschirm.
				if (text.length() > 0) {
					clear();
				} else {
					focused = false;
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	public boolean charTyped(CharInput input) {
		if (!focused || !input.isValidChar()) {
			return false;
		}
		String s = input.asString();
		if (s == null || s.isEmpty() || text.length() + s.length() > 48) {
			return false;
		}
		text.insert(cursor, s);
		cursor += s.length();
		return true;
	}
}
