package com.lumitier.lumilib.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Waehlt einen Spieler aus. Suche, Liste, Rollen - das Gegenstueck zum
 * ItemPicker, im selben Aussehen.
 *
 * WER IN DER LISTE STEHT: alle, die gerade auf dem Server sind (ohne einen
 * selbst). Wer niemanden findet, tippt den Namen einfach ein - dann steht
 * oben "Hinzufuegen: <Name>". So geht es auch im Hauptmenue, im
 * Einzelspieler und fuer jemanden, der gerade offline ist.
 *
 * ZUR SPERRLISTE: wie beim ItemPicker. Schon gewaehlte Spieler stehen
 * blass in der Liste statt zu fehlen - sonst sucht man, warum jemand
 * nicht auftaucht.
 */
public final class PlayerPicker extends ScaledScreen implements LumiScreen {

	private static final int ROW_H = 24;
	private static final int ROWS = 9;
	private static final int ITEM = 16;
	private static final int LIST_W = 280;

	private static final int PANEL_PAD = 16;
	private static final int FIELD_H = 22;
	private static final int GAP = 12;

	/** Spielerkoepfe mit Skin, je Name einmal angelegt - die Liste fragt in jedem Bild. */
	private static final Map<String, ItemStack> HEADS = new HashMap<>();

	private final Screen parent;
	private final Consumer<String> onPick;
	private final Predicate<String> blocked;

	private final SearchField search = new SearchField();
	/** Was gerade zu sehen ist. Ein Eintrag mit typed=true ist "Hinzufuegen: <Name>". */
	private final List<Row> shown = new ArrayList<>();
	private String lastQuery = "\u0000";
	private int scroll;

	private record Row(String name, boolean typed) {}

	private PlayerPicker(Screen parent, Consumer<String> onPick, Predicate<String> blocked) {
		super(Text.translatable("lumilib.players.title"));
		this.parent = parent;
		this.onPick = onPick;
		this.blocked = blocked;
	}

	/**
	 * Oeffnet den Waehler.
	 *
	 * @param parent  wohin es nach Auswahl oder Abbruch zurueckgeht
	 * @param onPick  bekommt den Namen des gewaehlten Spielers
	 * @param blocked welche Namen blass und unantastbar sind
	 */
	public static void open(Screen parent, Consumer<String> onPick, Predicate<String> blocked) {
		MinecraftClient.getInstance().setScreen(new PlayerPicker(parent, onPick, blocked));
	}

	@Override
	protected void init() {
		super.init();
		this.search.setFocused(true);
		filter();
	}

	/** Ist das ein moeglicher Minecraft-Name? 3 bis 16 Zeichen, Buchstaben, Ziffern, Unterstrich. */
	private static boolean validName(String s) {
		if (s.length() < 3 || s.length() > 16) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')) {
				return false;
			}
		}
		return true;
	}

	private List<String> online() {
		List<String> out = new ArrayList<>();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getNetworkHandler() == null) {
			return out;
		}
		String self = client.getSession().getUsername();
		for (PlayerListEntry p : client.getNetworkHandler().getPlayerList()) {
			String name = p.getProfile().name();
			if (name != null && !name.equalsIgnoreCase(self)) {
				out.add(name);
			}
		}
		out.sort(String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	private void filter() {
		this.lastQuery = this.search.text();
		String typed = this.lastQuery.trim();
		String q = typed.toLowerCase(Locale.ROOT);
		shown.clear();

		boolean exact = false;
		for (String name : online()) {
			if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q)) {
				shown.add(new Row(name, false));
				exact |= name.equalsIgnoreCase(typed);
			}
		}
		// Der getippte Name ganz oben - auch fuer jemanden, der nicht
		// online ist. Nicht, wenn er genauso schon in der Liste steht.
		if (!exact && validName(typed)) {
			shown.add(0, new Row(typed, true));
		}
		this.scroll = 0;
	}

	// ------------------------------------------------------------------
	// Masse
	// ------------------------------------------------------------------

	private int panelW() {
		return LIST_W + PANEL_PAD * 2;
	}

	private int panelH() {
		return PANEL_PAD * 2 + Ui.HEAD_H + GAP + FIELD_H + GAP + ROWS * ROW_H;
	}

	private int panelX() {
		return (ScaledScreen.DESIGN_W - panelW()) / 2;
	}

	private int panelY() {
		return (ScaledScreen.DESIGN_H - panelH()) / 2;
	}

	private int listY() {
		return panelY() + PANEL_PAD + Ui.HEAD_H + GAP + FIELD_H + GAP;
	}

	private int maxScroll() {
		return Math.max(0, shown.size() - ROWS);
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
			LIST_W, FIELD_H, mouseX, mouseY);

		// Wie beim ItemPicker: je Bild vergleichen, nur bei Aenderung filtern.
		if (!this.search.text().equals(this.lastQuery)) {
			filter();
		}

		drawList(ctx, px + PANEL_PAD, listY(), mouseX, mouseY);
	}

	private void drawList(DrawContext ctx, int left, int top, int mouseX, int mouseY) {
		if (shown.isEmpty()) {
			List<String> lines = Ui.wrap(this.textRenderer,
				Text.translatable("lumilib.players.empty").getString(), LIST_W - 40);
			int lineH = Ui.advance(Ui.BODY_H, Ui.BODY_D, 3);
			int ty = top + (ROWS * ROW_H - lines.size() * lineH) / 2;
			for (String line : lines) {
				int tw = Ui.width(this.textRenderer, line);
				Ui.drawTop(ctx, this.textRenderer, Ui.text(line), left + (LIST_W - tw) / 2, ty,
					Ui.TOP_BODY, Theme.TEXT_MUTED);
				ty += lineH;
			}
			return;
		}

		for (int i = 0; i < ROWS; i++) {
			int index = scroll + i;
			if (index >= shown.size()) {
				break;
			}
			Row row = shown.get(index);
			int y = top + i * ROW_H;
			boolean used = blocked.test(row.name());
			boolean hover = !used && Draw.inside(mouseX, mouseY, left, y, LIST_W, ROW_H);
			if (hover) {
				Draw.roundedRect(ctx, left, y, LIST_W, ROW_H, 6, Theme.ROW_HOVER);
			}

			int ix = left + 6;
			ctx.drawItem(headOf(row.name()), ix, y + (ROW_H - ITEM) / 2);

			String label = row.typed()
				? Text.translatable("lumilib.players.add", row.name()).getString()
				: row.name();
			label = Ui.fit(this.textRenderer, label, LIST_W - ITEM - 24);
			Ui.drawCenteredIn(ctx, this.textRenderer, row.typed() ? Ui.bold(label) : Ui.text(label),
				ix + ITEM + 9, y, ROW_H, Ui.BODY_H, Ui.BODY_D, Ui.TOP_BODY,
				used ? Theme.TEXT_FAINT : Theme.TEXT);
		}
	}

	static ItemStack headOf(String name) {
		return HEADS.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> {
			ItemStack head = new ItemStack(Items.PLAYER_HEAD);
			head.set(DataComponentTypes.PROFILE, ProfileComponent.ofDynamic(name));
			return head;
		});
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

		int left = panelX() + PANEL_PAD;
		int top = listY();
		if (Draw.inside(mx, my, left, top, LIST_W, ROWS * ROW_H)) {
			int index = scroll + (int) ((my - top) / ROW_H);
			if (index >= 0 && index < shown.size()) {
				pick(shown.get(index).name());
			}
			return true;
		}

		// Klick neben die Tafel schliesst - wie im Hub.
		if (!Draw.inside(mx, my, panelX(), panelY(), panelW(), panelH())) {
			close();
		}
		return true;
	}

	private void pick(String name) {
		if (blocked.test(name)) {
			return;
		}
		onPick.accept(name);
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
		int key = input.getKeycode();
		if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			close();
			return true;
		}
		// Enter nimmt den obersten Treffer - meist der getippte Name.
		if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
			if (!shown.isEmpty()) {
				pick(shown.get(0).name());
			}
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
		closeAnimated(() -> {
			if (this.client != null) {
				this.client.setScreen(this.parent);
			}
		});
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
