package com.lumitier.lumilib.client;

import com.lumitier.lumilib.LumiLib;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Waehlt ein Item aus. Raster, Suche, Rollen.
 *
 * WARUM IN LumiLib UND NICHT IN JEDER MOD EINZELN:
 * Item Drop Tweaks hatte einen eigenen, und der sah aus wie Vanilla -
 * also anders als der Hub, aus dem heraus er geoeffnet wurde. Jede
 * weitere Mod haette denselben noch einmal gebraucht. Hier steht er
 * einmal und sieht aus wie der Rest.
 *
 * ZUR SPERRLISTE:
 * blocked sagt, welche Items schon vergeben sind. Die werden gezeigt,
 * aber blass und ohne Reaktion. Sie ganz wegzulassen waere schlechter:
 * Wer sucht, warum sein Item fehlt, findet so die Antwort, statt zu
 * glauben, es gaebe es nicht.
 *
 * DAS GEHALTENE ITEM OBEN:
 * Ein Knopf, der nimmt, was man gerade in der Hand haelt. Der haeufigste
 * Fall ist, dass jemand etwas in der Hand hat und genau das einstellen
 * will - dann spart es das Suchen im Raster.
 */
public final class ItemPicker extends ScaledScreen implements LumiScreen {

	/** Kantenlaenge einer Zelle. 16 Pixel Item plus vier Luft. */
	private static final int CELL = 20;
	private static final int COLUMNS = 14;
	private static final int ROWS = 9;
	private static final int ITEM = 16;

	private static final int PANEL_PAD = 16;
	private static final int FIELD_H = 22;
	private static final int GAP = 12;

	/**
	 * Alle Items, einmal gesammelt.
	 *
	 * Statisch, weil das Verzeichnis sich zur Laufzeit nicht aendert.
	 * Beim ersten Oeffnen sind es ein paar tausend Eintraege - das jedes
	 * Mal neu durchzugehen waere verschenkt.
	 */
	private static final List<Item> ALL = new ArrayList<>();

	private final Screen parent;
	private final Consumer<Item> onPick;
	private final Predicate<Item> blocked;

	private final SearchField search = new SearchField();
	private final List<Item> shown = new ArrayList<>();
	private String lastQuery = "";
	private int scroll;

	private ItemPicker(Screen parent, Consumer<Item> onPick, Predicate<Item> blocked) {
		super(Text.translatable("lumilib.picker.title"));
		this.parent = parent;
		this.onPick = onPick;
		this.blocked = blocked;
	}

	/**
	 * Oeffnet den Waehler.
	 *
	 * @param parent  wohin es nach Auswahl oder Abbruch zurueckgeht
	 * @param onPick  bekommt das gewaehlte Item
	 * @param blocked welche Items blass und unantastbar sind
	 */
	public static void open(Screen parent, Consumer<Item> onPick,
	                        Predicate<Item> blocked) {
		MinecraftClient.getInstance().setScreen(
			new ItemPicker(parent, onPick, blocked));
	}

	@Override
	protected void init() {
		super.init();
		if (ALL.isEmpty()) {
			for (Item item : Registries.ITEM) {
				if (item != Items.AIR) {
					ALL.add(item);
				}
			}
		}
		this.search.setFocused(true);
		filter();
	}

	/**
	 * Trefferliste neu bauen. Gesucht wird im Anzeigenamen UND in der
	 * Kennung - wer "minecraft:stone" tippt, meint denselben Block wie
	 * jemand, der "stein" tippt.
	 */
	private void filter() {
		this.lastQuery = this.search.text();
		String q = this.lastQuery.trim().toLowerCase(Locale.ROOT);
		shown.clear();
		for (Item item : ALL) {
			if (q.isEmpty()
				|| item.getName().getString().toLowerCase(Locale.ROOT).contains(q)
				|| Registries.ITEM.getId(item).toString().contains(q)) {
				shown.add(item);
			}
		}
		this.scroll = 0;
	}

	// ------------------------------------------------------------------
	// Masse
	// ------------------------------------------------------------------

	private int gridW() {
		return COLUMNS * CELL;
	}

	private int panelW() {
		return gridW() + PANEL_PAD * 2;
	}

	private int panelH() {
		return PANEL_PAD * 2 + Ui.HEAD_H + GAP + FIELD_H + GAP
			+ ROWS * CELL + GAP + CELL;
	}

	private int panelX() {
		return (ScaledScreen.DESIGN_W - panelW()) / 2;
	}

	private int panelY() {
		return (ScaledScreen.DESIGN_H - panelH()) / 2;
	}

	private int gridY() {
		return panelY() + PANEL_PAD + Ui.HEAD_H + GAP + FIELD_H + GAP;
	}

	/** Oberkante der Zeile mit dem gehaltenen Item. */
	private int heldY() {
		return gridY() + ROWS * CELL + GAP;
	}

	/** Wieviele Zeilen koennen weggerollt werden? */
	private int maxScroll() {
		int rows = (shown.size() + COLUMNS - 1) / COLUMNS;
		return Math.max(0, rows - ROWS);
	}

	// ------------------------------------------------------------------
	// Zeichnen
	// ------------------------------------------------------------------

	@Override
	protected void renderScaled(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int px = panelX();
		int py = panelY();

		if (!Surface.glassPanel(ctx, px, py, panelW(), panelH(), Theme.PANEL)) {
			Surface.panel(ctx, px, py, panelW(), panelH(), Theme.PANEL);
			Surface.rimPanel(ctx, px, py, panelW(), panelH(), Theme.RIM);
		}

		Ui.drawTop(ctx, this.textRenderer, Ui.head(getTitle().getString()),
			px + PANEL_PAD, py + PANEL_PAD, Ui.TOP_HEAD, Theme.TEXT);

		this.search.render(ctx, this.textRenderer,
			px + PANEL_PAD, py + PANEL_PAD + Ui.HEAD_H + GAP,
			gridW(), FIELD_H, mouseX, mouseY);

		// Die Suche hat kein Ereignis beim Tippen - also wird der Text
		// je Bild verglichen. Billiger als es klingt: ein String-Vergleich
		// gegen den Stand von eben, und neu gefiltert wird nur bei einer
		// echten Aenderung.
		if (!this.search.text().equals(this.lastQuery)) {
			filter();
		}

		drawGrid(ctx, px + PANEL_PAD, gridY(), mouseX, mouseY);
		drawHeld(ctx, px + PANEL_PAD, heldY(), mouseX, mouseY);
	}

	private void drawGrid(DrawContext ctx, int left, int top, int mouseX, int mouseY) {
		int first = scroll * COLUMNS;
		for (int i = 0; i < COLUMNS * ROWS; i++) {
			int index = first + i;
			if (index >= shown.size()) {
				break;
			}
			Item item = shown.get(index);
			int x = left + (i % COLUMNS) * CELL;
			int y = top + (i / COLUMNS) * CELL;

			boolean used = blocked.test(item);
			boolean hover = !used && Draw.inside(mouseX, mouseY, x, y, CELL, CELL);
			if (hover) {
				Draw.roundedRect(ctx, x, y, CELL, CELL, 5, Theme.ROW_HOVER);
			}

			int ix = x + (CELL - ITEM) / 2;
			int iy = y + (CELL - ITEM) / 2;
			ctx.drawItem(item.getDefaultStack(), ix, iy);
			if (used) {
				// Ueber das Bild statt es wegzulassen: Das Item bleibt
				// erkennbar, sieht aber eindeutig nicht waehlbar aus.
				Draw.roundedRect(ctx, x, y, CELL, CELL, 5, Theme.SCRIM);
			}
		}

		if (shown.isEmpty()) {
			Ui.drawCentered(ctx, this.textRenderer,
				Ui.text(Text.translatable("lumilib.picker.empty").getString()),
				left + gridW() / 2, top + ROWS * CELL / 2,
				Ui.BODY_H, Ui.TOP_BODY, Theme.TEXT_MUTED);
		}
	}

	private void drawHeld(DrawContext ctx, int left, int top, int mouseX, int mouseY) {
		ItemStack held = heldStack();
		if (held.isEmpty()) {
			return;
		}
		boolean used = blocked.test(held.getItem());
		boolean hover = !used && Draw.inside(mouseX, mouseY, left, top, gridW(), CELL);
		Draw.roundedRect(ctx, left, top, gridW(), CELL, 6,
			hover ? Theme.ROW_HOVER : Theme.CARD);

		ctx.drawItem(held, left + 4, top + (CELL - ITEM) / 2);
		String label = Text.translatable("lumilib.picker.held",
			held.getName().getString()).getString();
		Ui.drawCenteredIn(ctx, this.textRenderer, Ui.text(label),
			left + 4 + ITEM + 7, top, CELL,
			Ui.BODY_H, Ui.BODY_D, Ui.TOP_BODY,
			used ? Theme.TEXT_FAINT : Theme.TEXT);
	}

	/** Was der Spieler in der Hand haelt - leer, wenn es keinen gibt. */
	private ItemStack heldStack() {
		return this.client == null || this.client.player == null
			? ItemStack.EMPTY
			: this.client.player.getMainHandStack();
	}

	// ------------------------------------------------------------------
	// Eingabe
	// ------------------------------------------------------------------

	@Override
	protected boolean scaledMouseClicked(Click click, boolean doubled) {
		double mx = click.x();
		double my = click.y();

		if (this.search.mouseClicked(mx, my)) {
			return true;
		}
		this.search.setFocused(false);

		int left = panelX() + PANEL_PAD;
		int top = gridY();
		if (Draw.inside(mx, my, left, top, gridW(), ROWS * CELL)) {
			int col = (int) ((mx - left) / CELL);
			int row = (int) ((my - top) / CELL);
			int index = (scroll + row) * COLUMNS + col;
			if (col >= 0 && col < COLUMNS && index >= 0 && index < shown.size()) {
				pick(shown.get(index));
			}
			return true;
		}

		ItemStack held = heldStack();
		if (!held.isEmpty()
			&& Draw.inside(mx, my, left, heldY(), gridW(), CELL)) {
			pick(held.getItem());
			return true;
		}

		// Klick neben die Tafel schliesst - wie im Hub.
		if (!Draw.inside(mx, my, panelX(), panelY(), panelW(), panelH())) {
			close();
			return true;
		}
		return true;
	}

	/** Nimmt das Item an, falls es nicht gesperrt ist, und geht zurueck. */
	private void pick(Item item) {
		if (blocked.test(item)) {
			return;
		}
		onPick.accept(item);
		close();
	}

	@Override
	protected boolean scaledMouseScrolled(double mouseX, double mouseY,
	                                      double horizontal, double vertical) {
		this.scroll = Math.max(0,
			Math.min(maxScroll(), this.scroll - (int) Math.signum(vertical)));
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.getKeycode() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			close();
			return true;
		}
		if (this.search.keyPressed(input)) {
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (this.search.charTyped(input)) {
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public void close() {
		// Mit Bewegung zurueck - siehe ScaledScreen.closeAnimated.
		closeAnimated(() -> {
			if (this.client != null) {
				this.client.setScreen(this.parent);
			}
		});
	}

	/** Ohne das waere das Spiel im Hintergrund pausiert. */
	@Override
	public boolean shouldPause() {
		return false;
	}

	static {
		// Nur damit die Kennung im Protokoll auftaucht, falls jemand
		// wissen will, woher der Waehler kommt.
		LumiLib.LOGGER.debug("[LumiLib] ItemPicker bereit.");
	}
}
