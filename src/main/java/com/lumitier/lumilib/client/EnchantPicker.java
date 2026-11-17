package com.lumitier.lumilib.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Waehlt eine Verzauberung und ihre Stufe aus.
 *
 * ZWEI SCHRITTE:
 * Erst die Liste - nur Namen, sonst nichts. Dann, nach dem Klick, ein
 * Feld fuer die Stufe.
 *
 * WARUM NICHT MEHR ROEMISCHE ZAHLEN NEBEN JEDEM EINTRAG:
 * Die Knoepfe reichten nur bis zur Hoechststufe der Verzauberung. Im
 * Kreativmodus und ueber Befehle sind aber Stufen bis 255 moeglich, und
 * genau die will man einstellen koennen - eine Regel soll ja auch auf ein
 * Schwert mit Schaerfe 100 passen. Zehn Knoepfe nebeneinander waren
 * ausserdem schon eng; 255 waeren unmoeglich gewesen.
 *
 * Nachgeprueft am Spiel: ItemEnchantmentsComponent speichert die Stufe mit
 * Codec.intRange(1, 255), und sein Builder weist alles ausserhalb 0..255
 * zurueck. 255 ist also keine ausgedachte Grenze, sondern die des Spiels.
 *
 * DIE HOECHSTSTUFE steht trotzdem noch da - als Hinweis unter dem Feld,
 * nicht als Schranke. Sie sagt, was im normalen Spiel vorkommt.
 */
public final class EnchantPicker extends ScaledScreen implements LumiScreen {

	private static final int ROW_H = 24;
	private static final int ROWS = 11;
	private static final int PANEL_W = 300;
	private static final int PANEL_PAD = 16;
	private static final int FIELD_H = 22;
	private static final int GAP = 12;

	/** Hoechste Stufe, die das Spiel speichern kann. */
	public static final int LEVEL_MAX = 255;

	/**
	 * Masse des zweiten Schritts.
	 *
	 * SCHMALER ALS DIE LISTE und ein kleines Feld statt eines ueber die
	 * volle Breite: Hier wird eine dreistellige Zahl eingetippt. Ein
	 * Feld von 268 Pixeln fuer drei Ziffern sah aus, als fehle etwas
	 * darin - und tatsaechlich stand die Zahl verloren am linken Rand.
	 */
	private static final int LEVEL_PANEL_W = 210;
	private static final int LEVEL_FIELD_W = 72;
	private static final int LEVEL_FIELD_H = 30;

	private final Screen parent;
	private final BiConsumer<Identifier, Integer> onPick;
	private final Predicate<Identifier> blocked;

	private final SearchField search = new SearchField();

	/**
	 * Eine waehlbare Verzauberung.
	 *
	 * Eigener Typ statt RegistryEntry, weil es die Liste AUCH OHNE WELT
	 * geben muss - und ohne Welt gibt es keine RegistryEntry-Objekte.
	 */
	public record Choice(Identifier id, String name, int max) {}

	private final List<Choice> all = new ArrayList<>();
	private final List<Choice> shown = new ArrayList<>();
	private String lastQuery = "";
	private int scroll;

	// --- zweiter Schritt ---

	/** Die gewaehlte Verzauberung, solange die Stufe noch fehlt. */
	private Choice pending;

	/** Was im Stufenfeld steht. Text, nicht Zahl - sonst gaebe es kein Leer. */
	private final StringBuilder level = new StringBuilder();

	/** Vorgabe fuer die Stufe; beim Bearbeiten die bisherige. */
	private final int startLevel;

	private EnchantPicker(Screen parent, BiConsumer<Identifier, Integer> onPick,
	                      Predicate<Identifier> blocked, int startLevel) {
		super(Text.translatable("lumilib.enchant.title"));
		this.parent = parent;
		this.onPick = onPick;
		this.blocked = blocked;
		this.startLevel = Math.max(1, Math.min(LEVEL_MAX, startLevel));
	}

	/**
	 * Oeffnet den Waehler.
	 *
	 * @param parent  wohin es zurueckgeht
	 * @param onPick  bekommt Kennung und Stufe
	 * @param blocked welche Verzauberungen schon vergeben sind
	 */
	public static void open(Screen parent, BiConsumer<Identifier, Integer> onPick,
	                        Predicate<Identifier> blocked) {
		open(parent, onPick, blocked, 1);
	}

	/**
	 * Oeffnet den Waehler mit einer vorgegebenen Stufe.
	 *
	 * WOFUER: Eine schon gesetzte Verzauberung zu aendern. Dann steht ihre
	 * bisherige Stufe gleich im Feld und man tippt nur drueber.
	 */
	public static void open(Screen parent, BiConsumer<Identifier, Integer> onPick,
	                        Predicate<Identifier> blocked, int startLevel) {
		MinecraftClient.getInstance().setScreen(
			new EnchantPicker(parent, onPick,
				blocked == null ? id -> false : blocked, startLevel));
	}

	/**
	 * Oeffnet direkt die Stufenabfrage einer bestimmten Verzauberung.
	 *
	 * WOFUER: Die Verzauberung steht schon fest, nur die Stufe soll
	 * geaendert werden. Die Liste dazwischen waere ein Umweg.
	 */
	public static void openLevel(Screen parent, Identifier id, int startLevel,
	                             BiConsumer<Identifier, Integer> onPick) {
		EnchantPicker p = new EnchantPicker(parent, onPick, x -> false, startLevel);
		p.pending = new Choice(id, nameOf(id), maxOf(id));
		MinecraftClient.getInstance().setScreen(p);
	}

	@Override
	protected void init() {
		super.init();
		all.clear();
		Optional<Registry<Enchantment>> reg = registry();
		if (reg.isPresent()) {
			// MIT WELT: die echten Daten. Enthaelt auch Verzauberungen
			// aus Mods und Datenpaketen, und die Hoechststufen stimmen.
			for (RegistryEntry<Enchantment> e : reg.get().getIndexedEntries()) {
				Identifier id = e.getKey().map(k -> k.getValue()).orElse(null);
				if (id != null) {
					all.add(new Choice(id, e.value().description().getString(),
						e.value().getMaxLevel()));
				}
			}
		} else {
			all.addAll(vanilla());
		}
		all.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));

		if (this.pending != null) {
			startLevelEntry(this.pending);
		} else {
			this.search.setFocused(true);
		}
		filter();
	}

	/** Das Verzeichnis der Verzauberungen - leer ohne geladene Welt. */
	private static Optional<Registry<Enchantment>> registry() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return Optional.empty();
		}
		return client.world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT);
	}

	/**
	 * Die Vanilla-Verzauberungen, ohne Welt.
	 *
	 * WARUM ES DAS BRAUCHT: Verzauberungen sind seit 1.21 datengesteuert
	 * und stehen erst bereit, wenn eine Welt geladen ist. Regeln will man
	 * aber auch im Hauptmenue einstellen koennen - vorher stand dort nur
	 * ein Hinweis, und das war unbrauchbar.
	 *
	 * DIE KENNUNGEN kommen ueber Reflexion aus der Klasse Enchantments.
	 * Dort steht jede Vanilla-Verzauberung als RegistryKey-Konstante, und
	 * die braucht keine Daten. Reflexion statt einer getippten Liste,
	 * damit eine neue Verzauberung in einer kuenftigen Spielversion von
	 * selbst auftaucht.
	 *
	 * DIE NAMEN entstehen aus dem Uebersetzungsschluessel, den Minecraft
	 * fuer Verzauberungen nach festem Muster bildet. Auch dafuer braucht
	 * es keine Daten.
	 *
	 * DIE HOECHSTSTUFEN stehen dagegen wirklich nur in den Daten, also
	 * hier als Tabelle. Sie gilt NUR im Hauptmenue - sobald eine Welt
	 * geladen ist, kommen die echten Werte von oben. Steht eine
	 * Verzauberung nicht in der Tabelle, werden 5 angenommen.
	 */
	public static List<Choice> vanilla() {
		List<Choice> out = new ArrayList<>();
		for (java.lang.reflect.Field f : Enchantments.class.getFields()) {
			if (!RegistryKey.class.isAssignableFrom(f.getType())) {
				continue;
			}
			try {
				Object v = f.get(null);
				if (!(v instanceof RegistryKey<?> key)) {
					continue;
				}
				Identifier id = key.getValue();
				out.add(new Choice(id, vanillaName(id),
					VANILLA_MAX.getOrDefault(id.getPath(), 5)));
			} catch (ReflectiveOperationException ignored) {
				// Eine Konstante, die sich nicht lesen laesst, wird
				// uebersprungen - lieber eine fehlt, als dass das
				// Fenster gar nicht aufgeht.
			}
		}
		return out;
	}

	/** Uebersetzungsschluessel einer Verzauberung, nach Minecrafts Muster. */
	public static String vanillaName(Identifier id) {
		return Text.translatable(
			"enchantment." + id.getNamespace() + "." + id.getPath()).getString();
	}

	/** Name einer Verzauberung - mit Welt aus den Daten, sonst uebersetzt. */
	public static String nameOf(Identifier id) {
		Optional<Registry<Enchantment>> reg = registry();
		if (reg.isPresent()) {
			Optional<Enchantment> e = reg.get().getOptionalValue(id);
			if (e.isPresent()) {
				return e.get().description().getString();
			}
		}
		return vanillaName(id);
	}

	/** Hoechststufe im normalen Spiel - mit Welt aus den Daten, sonst Tabelle. */
	public static int maxOf(Identifier id) {
		Optional<Registry<Enchantment>> reg = registry();
		if (reg.isPresent()) {
			Optional<Enchantment> e = reg.get().getOptionalValue(id);
			if (e.isPresent()) {
				return e.get().getMaxLevel();
			}
		}
		return VANILLA_MAX.getOrDefault(id.getPath(), 5);
	}

	/** Hoechststufen der Vanilla-Verzauberungen. Nur als Ersatz, siehe vanilla(). */
	public static final java.util.Map<String, Integer> VANILLA_MAX =
		java.util.Map.ofEntries(
			java.util.Map.entry("protection", 4),
			java.util.Map.entry("fire_protection", 4),
			java.util.Map.entry("feather_falling", 4),
			java.util.Map.entry("blast_protection", 4),
			java.util.Map.entry("projectile_protection", 4),
			java.util.Map.entry("respiration", 3),
			java.util.Map.entry("aqua_affinity", 1),
			java.util.Map.entry("thorns", 3),
			java.util.Map.entry("depth_strider", 3),
			java.util.Map.entry("frost_walker", 2),
			java.util.Map.entry("binding_curse", 1),
			java.util.Map.entry("soul_speed", 3),
			java.util.Map.entry("swift_sneak", 3),
			java.util.Map.entry("knockback", 2),
			java.util.Map.entry("fire_aspect", 2),
			java.util.Map.entry("looting", 3),
			java.util.Map.entry("sweeping_edge", 3),
			java.util.Map.entry("silk_touch", 1),
			java.util.Map.entry("unbreaking", 3),
			java.util.Map.entry("fortune", 3),
			java.util.Map.entry("punch", 2),
			java.util.Map.entry("flame", 1),
			java.util.Map.entry("infinity", 1),
			java.util.Map.entry("luck_of_the_sea", 3),
			java.util.Map.entry("lure", 3),
			java.util.Map.entry("loyalty", 3),
			java.util.Map.entry("riptide", 3),
			java.util.Map.entry("channeling", 1),
			java.util.Map.entry("multishot", 1),
			java.util.Map.entry("quick_charge", 3),
			java.util.Map.entry("piercing", 4),
			java.util.Map.entry("breach", 4),
			java.util.Map.entry("wind_burst", 3),
			java.util.Map.entry("mending", 1),
			java.util.Map.entry("vanishing_curse", 1));

	private void filter() {
		this.lastQuery = this.search.text();
		String q = this.lastQuery.trim().toLowerCase(Locale.ROOT);
		shown.clear();
		for (Choice c : all) {
			if (q.isEmpty()
				|| c.name().toLowerCase(Locale.ROOT).contains(q)
				|| c.id().toString().contains(q)) {
				shown.add(c);
			}
		}
		this.scroll = 0;
	}

	// ------------------------------------------------------------------
	// Masse
	// ------------------------------------------------------------------

	private int panelH() {
		if (pending != null) {
			return PANEL_PAD * 2 + Ui.HEAD_H + GAP + LEVEL_FIELD_H;
		}
		return PANEL_PAD * 2 + Ui.HEAD_H + GAP + FIELD_H + GAP + ROWS * ROW_H;
	}

	private int panelW() {
		return pending != null ? LEVEL_PANEL_W : PANEL_W;
	}

	private int panelX() {
		return (ScaledScreen.DESIGN_W - panelW()) / 2;
	}

	private int panelY() {
		return (ScaledScreen.DESIGN_H - panelH()) / 2;
	}

	private int fieldY() {
		return panelY() + PANEL_PAD + Ui.HEAD_H + GAP;
	}

	private int listY() {
		return fieldY() + FIELD_H + GAP;
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
		int pw = PANEL_W;

		if (!Surface.glassPanel(ctx, px, py, pw, panelH(), Theme.PANEL)) {
			Surface.panel(ctx, px, py, pw, panelH(), Theme.PANEL);
			Surface.rimPanel(ctx, px, py, pw, panelH(), Theme.RIM);
		}

		if (pending != null) {
			renderLevel(ctx, px, py, pw, mouseX, mouseY);
			return;
		}

		Ui.drawTop(ctx, this.textRenderer, Ui.head(getTitle().getString()),
			px + PANEL_PAD, py + PANEL_PAD, Ui.TOP_HEAD, Theme.TEXT);

		this.search.render(ctx, this.textRenderer,
			px + PANEL_PAD, fieldY(), pw - PANEL_PAD * 2, FIELD_H, mouseX, mouseY);

		if (!this.search.text().equals(this.lastQuery)) {
			filter();
		}

		int left = px + PANEL_PAD;
		int width = pw - PANEL_PAD * 2;
		int top = listY();

		for (int i = 0; i < ROWS; i++) {
			int index = scroll + i;
			if (index >= shown.size()) {
				break;
			}
			Choice c = shown.get(index);
			int y = top + i * ROW_H;
			boolean used = blocked.test(c.id());

			if (!used && Draw.inside(mouseX, mouseY, left, y, width, ROW_H)) {
				Draw.roundedRect(ctx, left, y, width, ROW_H, 6, Theme.ROW_HOVER);
			}

			Ui.drawCenteredIn(ctx, this.textRenderer,
				Ui.text(Ui.fit(this.textRenderer, c.name(), width - 12)),
				left + 6, y, ROW_H, Ui.BODY_H, Ui.BODY_D, Ui.TOP_BODY,
				used ? Theme.TEXT_FAINT : Theme.TEXT);
		}
	}

	/**
	 * Der zweite Schritt: die Stufe.
	 *
	 * Die Hoechststufe steht als Hinweis dabei, sperrt aber nichts. Wer
	 * eine Regel fuer ein Schwert mit Schaerfe 100 braucht, soll sie
	 * anlegen koennen.
	 */
	private void renderLevel(DrawContext ctx, int px, int py, int pw,
	                         int mouseX, int mouseY) {
		int mid = px + pw / 2;

		// WAAGERECHT SELBST MITTEN: Ui.drawCentered mittet nur senkrecht,
		// sein x ist die linke Kante. Gemessen wird in GENAU der Staffel,
		// in der auch gezeichnet wird - sonst sitzt die Mitte daneben.
		String head = Ui.fit(this.textRenderer, pending.name(),
			pw - PANEL_PAD * 2, 15.0F, true);
		Ui.drawCentered(ctx, this.textRenderer, Ui.head(head),
			mid - Ui.widthHead(this.textRenderer, head) / 2,
			py + PANEL_PAD + Ui.HEAD_H / 2, Ui.HEAD_H, Ui.TOP_HEAD, Theme.TEXT);

		int fx = mid - LEVEL_FIELD_W / 2;
		int fy = fieldY();

		if (!Surface.glassCard(ctx, fx, fy, LEVEL_FIELD_W, LEVEL_FIELD_H,
			Theme.FIELD_FOCUS)) {
			Surface.row(ctx, fx, fy, LEVEL_FIELD_W, LEVEL_FIELD_H, Theme.FIELD_FOCUS);
		}

		// DIE ZAHL MITTIG IM FELD, und der Strich haengt rechts daneben
		// statt mitzuwandern: Blinkt er innerhalb der Zentrierung, springt
		// die Zahl jede halbe Sekunde um eine halbe Strichbreite hin und
		// her. Das faellt auf und sieht nach Fehler aus.
		String value = level.toString();
		int vw = Ui.widthTitle(this.textRenderer, value);
		int vx = mid - vw / 2;
		Ui.drawCenteredIn(ctx, this.textRenderer, Ui.title(value),
			vx, fy, LEVEL_FIELD_H, Ui.TITLE_H, Ui.TITLE_D, Ui.TOP_TITLE, Theme.TEXT);

		if ((System.currentTimeMillis() / 500) % 2 == 0) {
			int cy = fy + LEVEL_FIELD_H / 2 + Ui.TITLE_H / 2 + 2;
			Draw.hLine(ctx, vx + vw + 2, cy, 7, Theme.TEXT_MUTED);
		}

		// KEINE HINWEISZEILEN. Hier standen Grenzen und Tastenbelegung -
		// zwei Zeilen Text unter einem Feld, in dem drei Ziffern Platz
		// haben. Enter und Esc erklaeren sich von selbst, und die Grenze
		// merkt man, sobald eine 999 als 255 stehen bleibt.
	}

	// ------------------------------------------------------------------
	// Eingabe
	// ------------------------------------------------------------------

	/** Von der Liste in die Stufenabfrage. */
	private void startLevelEntry(Choice c) {
		this.pending = c;
		this.search.setFocused(false);
		this.level.setLength(0);
		this.level.append(this.startLevel);
	}

	/** Stufe uebernehmen und schliessen. Leer oder 0 gilt als 1. */
	private void confirmLevel() {
		int lv = 1;
		if (!level.isEmpty()) {
			try {
				lv = Integer.parseInt(level.toString());
			} catch (NumberFormatException ignored) {
				// Kann nicht vorkommen - es kommen nur Ziffern rein, und
				// mehr als drei nimmt charTyped nicht an. Trotzdem
				// abgefangen, statt im Fenster abzustuerzen.
				lv = 1;
			}
		}
		lv = Math.max(1, Math.min(LEVEL_MAX, lv));
		onPick.accept(pending.id(), lv);
		close();
	}

	@Override
	protected boolean scaledMouseClicked(Click click, boolean doubled) {
		double mx = click.x();
		double my = click.y();

		if (pending != null) {
			// Nur zwei Flaechen: das Feld (tut nichts, es hat immer den
			// Fokus) und alles ausserhalb des Fensters (schliesst).
			if (!Draw.inside(mx, my, panelX(), panelY(), PANEL_W, panelH())) {
				close();
			}
			return true;
		}

		if (this.search.mouseClicked(mx, my)) {
			return true;
		}
		this.search.setFocused(false);

		int left = panelX() + PANEL_PAD;
		int width = PANEL_W - PANEL_PAD * 2;
		int top = listY();

		if (Draw.inside(mx, my, left, top, width, ROWS * ROW_H)) {
			int row = (int) ((my - top) / ROW_H);
			int index = scroll + row;
			if (index >= 0 && index < shown.size()) {
				Choice c = shown.get(index);
				if (!blocked.test(c.id())) {
					startLevelEntry(c);
				}
			}
			return true;
		}

		if (!Draw.inside(mx, my, panelX(), panelY(), PANEL_W, panelH())) {
			close();
		}
		return true;
	}

	@Override
	protected boolean scaledMouseScrolled(double mouseX, double mouseY,
	                                      double horizontal, double vertical) {
		if (pending != null) {
			return true;
		}
		this.scroll = Math.max(0,
			Math.min(maxScroll(), this.scroll - (int) Math.signum(vertical)));
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int key = input.getKeycode();

		if (pending != null) {
			switch (key) {
				case GLFW.GLFW_KEY_ESCAPE -> {
					// Zurueck zur Liste, nicht gleich ganz zu. Wer sich
					// vertippt hat, will die Verzauberung neu waehlen, nicht
					// das Fenster verlieren.
					this.pending = null;
					this.search.setFocused(true);
					return true;
				}
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
					confirmLevel();
					return true;
				}
				case GLFW.GLFW_KEY_BACKSPACE -> {
					if (!level.isEmpty()) {
						level.setLength(level.length() - 1);
					}
					return true;
				}
				case GLFW.GLFW_KEY_UP -> {
					step(1);
					return true;
				}
				case GLFW.GLFW_KEY_DOWN -> {
					step(-1);
					return true;
				}
				default -> {
					return true;
				}
			}
		}

		if (key == GLFW.GLFW_KEY_ESCAPE) {
			close();
			return true;
		}
		if (this.search.keyPressed(input)) {
			return true;
		}
		return super.keyPressed(input);
	}

	/** Pfeiltasten: eine Stufe hoch oder runter, innerhalb der Grenzen. */
	private void step(int by) {
		int v = 1;
		if (!level.isEmpty()) {
			try {
				v = Integer.parseInt(level.toString());
			} catch (NumberFormatException ignored) {
				v = 1;
			}
		}
		v = Math.max(1, Math.min(LEVEL_MAX, v + by));
		level.setLength(0);
		level.append(v);
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (pending != null) {
			// Nur Ziffern, hoechstens drei - 255 ist die Grenze des Spiels.
			String s = input.isValidChar() ? input.asString() : null;
			if (s != null && s.length() == 1) {
				char c = s.charAt(0);
				if (c >= '0' && c <= '9' && level.length() < 3) {
					level.append(c);
				}
			}
			return true;
		}
		return this.search.charTyped(input) || super.charTyped(input);
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

	@Override
	public boolean shouldPause() {
		return false;
	}
}
