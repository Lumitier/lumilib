package com.lumitier.lumilib.client;

import com.lumitier.lumilib.LumiLib;
import com.lumitier.lumilib.api.LumiLibApi;
import com.lumitier.lumilib.api.SettingsPage;
import com.lumitier.lumilib.api.LumiMod;
import net.fabricmc.loader.api.FabricLoader;
import com.lumitier.lumilib.client.gpu.Backdrop;
import com.lumitier.lumilib.client.gpu.Sdf;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Der LumiLib-Hub: Uebersicht aller angemeldeten Mods.
 *
 * Masse stehen in {@link Metrics} und sind aus einer Referenzoberflaeche
 * abgemessen, nicht geschaetzt. Schriftgroessen in {@link Ui}.
 *
 * GELERNTE FALLEN, alle hier beruecksichtigt:
 *
 * 1. Screen.render zeichnet den Hintergrund NICHT - renderWithTooltip ruft
 *    vorher renderBackground auf, und das blurrt in der Welt nicht. Wer dort
 *    Milchglas will, ruft ctx.applyBlur() selbst auf.
 *
 * 2. Nur EINMAL pro Bild blurren. GuiRenderState wirft sonst hart.
 *
 * 3. Keine festen Pixelmasse fuer die Fenstergroesse - die logische
 *    Bildschirmbreite haengt an der GUI-Skalierung und liegt meist um 960.
 *
 * 4. Nine-Slice KACHELT die Raender, es streckt sie nicht. Texturen mit
 *    einem Verlauf ueber die Kante erzeugen dadurch Streifen.
 *
 * 5. Eingabesignaturen in 1.21.11: keyPressed(KeyInput),
 *    mouseClicked(Click, boolean).
 */
public class LumiHubScreen extends Screen {

	/** Wuerfel fuer Mods ohne eigenes Icon - GUI-Sprite, also ohne Pfad. */
	private static final Identifier ICON_MOD_DEFAULT =
		Identifier.of(LumiLib.MOD_ID, "mod_default");

	private final Screen parent;

	/** Fenster. */
	private int px, py, pw, ph;
	/** Sidebar-Karte. */
	private int sx, sy, sw, sh;
	/** Inhaltsbereich. */
	private int cx, cy, cw, ch;
	/** Kartenraster. */
	private int cols, colW, listTop, listBottom;

	/**
	 * Welche Seite rechts offen ist.
	 *
	 * null bedeutet die Uebersicht mit allen Karten. Sonst die Kennung
	 * der Mod, deren Seite gezeigt wird. Bewusst die KENNUNG und nicht
	 * das Objekt: Mods koennen sich waehrend der Sitzung anmelden, und
	 * ein gehaltenes Objekt wuerde dann veralten.
	 */
	private String selectedId;

	/** Lage des Knopfs auf der Mod-Seite, fuer den Klick. */
	private int settingsBtnX, settingsBtnY, settingsBtnW, settingsBtnH;
	private boolean settingsBtnShown;

	/** Lage der Update-Zeile - eine Quelle fuer Zeichnen und Klicken. */
	private int updatesRowY;
	private boolean updatesClickable;

	private int scroll;
	private int contentHeight;

	private final SearchField search = new SearchField();
	private List<HubEntry> visible = List.of();

	public LumiHubScreen(Screen parent) {
		super(Text.translatable("lumilib.hub.title"));
		this.parent = parent;
	}

	// ------------------------------------------------------------------
	// Aufbau
	// ------------------------------------------------------------------

	@Override
	protected void init() {
		this.pw = Math.max(280, (int) (this.width * Metrics.PANEL_W));
		this.ph = Math.max(180, (int) (this.height * Metrics.PANEL_H));
		this.px = (this.width - this.pw) / 2;
		this.py = (this.height - this.ph) / 2;

		int pad = Metrics.PAD;
		this.sw = Math.max(110, (int) (this.pw * Metrics.SIDEBAR_W));
		this.sx = this.px + pad;
		this.sy = this.py + pad;
		this.sh = this.ph - pad * 2;

		this.cx = this.sx + this.sw + pad;
		this.cy = this.py + pad;
		this.cw = this.px + this.pw - pad - this.cx;
		this.ch = this.sh;

		// Spaltenzahl: moeglichst drei, bei zu schmalem Fenster weniger.
		this.cols = Metrics.CARD_COLS;
		while (cols > 1
			&& (cw - Metrics.CARD_GAP * (cols - 1)) / cols < Metrics.CARD_MIN_W) {
			cols--;
		}
		this.colW = (cw - Metrics.CARD_GAP * (cols - 1)) / cols;


		this.listTop = cy + Metrics.TITLE_H + 6;
		this.listBottom = cy + ch;
		this.scroll = 0;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}

	private List<HubEntry> filtered() {
		List<HubEntry> all = HubEntry.all();
		String q = this.search.text().trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return all;
		}
		List<HubEntry> out = new ArrayList<>();
		for (HubEntry e : all) {
			if (e.name().getString().toLowerCase(Locale.ROOT).contains(q)
				|| e.id().toLowerCase(Locale.ROOT).contains(q)) {
				out.add(e);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------
	// Zeichnen
	// ------------------------------------------------------------------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		// ZUERST, vor allem anderen: Der Bildschirm wird kopiert, solange
		// nur die Welt darauf ist. Danach zeichnet der Hub darueber, und
		// die Glasflaeche liest in dieser Kopie nach, was hinter ihr
		// liegt. Spaeter kopiert waere der Hub mit drauf - Glas, das
		// sich selbst bricht.
		Backdrop.capture();

		this.visible = filtered();
		this.lastMouseY = mouseY;
		updateDrag(mouseX);

		maybeBlur(ctx);
		ctx.fill(0, 0, this.width, this.height, Theme.SCRIM);

		Surface.shadowPanel(ctx, px, py, pw, ph,
			Theme.SHADOW_SPREAD, Theme.SHADOW_OFFSET, Theme.SHADOW);
		// Ein Aufruf statt vier. Schlaegt der Shaderweg fehl, bleibt es
		// bei den vier Sprites wie bisher - gleiches Bild, nur unschaerfer
		// an den Ecken.
		// Erst Glas mit Brechung, dann Glas ohne, dann die Sprites.
		// Jede Stufe faengt die vorige auf - der Hub bleibt sichtbar,
		// auch wenn die Grafikkarte beim Kopieren nicht mitspielt.
		if (!Surface.refractPanel(ctx, px, py, pw, ph, Theme.PANEL)
			&& !Surface.glassPanel(ctx, px, py, pw, ph, Theme.PANEL)) {
			Surface.panel(ctx, px, py, pw, ph, Theme.PANEL);
			Surface.innerPanel(ctx, px, py, pw, ph, Theme.INNER);
			Surface.bevelPanel(ctx, px, py, pw, ph, Theme.BEVEL_LIGHT, Theme.BEVEL_DARK);
			Surface.rimPanel(ctx, px, py, pw, ph, Theme.RIM);
		}

		if (!Surface.glassCard(ctx, sx, sy, sw, sh, Theme.SIDEBAR)) {
			Surface.card(ctx, sx, sy, sw, sh, Theme.SIDEBAR);
		}

		renderSidebar(ctx, mouseX, mouseY);
		renderContent(ctx, mouseX, mouseY);

		super.render(ctx, mouseX, mouseY, delta);
	}

	/**
	 * Milchglas, hoechstens einmal pro Bild.
	 *
	 * Im Hauptmenue hat Vanilla in renderBackground bereits geblurrt; in der
	 * Welt nicht. Das try/catch faengt den Fall, dass eine fremde Mod im
	 * selben Bild schneller war - Unschaerfe ist Optik und darf niemandem
	 * das Spiel abschiessen.
	 */
	private void maybeBlur(DrawContext ctx) {
		if (this.client == null || this.client.world == null) {
			return;
		}
		try {
			ctx.applyBlur();
		} catch (IllegalStateException ignored) {
		}
	}

	private int sessionBlockHeight() {
		// ACCOUNT_H, nicht HEAD. Gezeichnet wird die Kontozeile mit
		// ACCOUNT_H; stuende hier weiter HEAD, laegen alle Klickbereiche
		// der Mod-Zeilen acht Pixel ueber dem, was man sieht.
		return LumiLib.cfg.showSession ? (Metrics.ACCOUNT_H + 10 + 9) : 0;
	}

	/**
	 * Zahl der Mods mit Update, oder 0 wenn nicht feststellbar.
	 *
	 * Die Abfrage von Mod Menu liegt in ModMenuUpdates - einer eigenen
	 * Klasse, die nur angefasst wird, wenn Mod Menu geladen ist. Stuende
	 * der Verweis hier direkt, koennte dieser Bildschirm ohne Mod Menu
	 * nicht einmal geoeffnet werden.
	 *
	 * Der Fang um Throwable ist Absicht: Mod Menu ist fremder Code mit
	 * eigenem Versionsrhythmus. Aendert sich dort etwas, soll der Hub eine
	 * Zeile weniger anzeigen und nicht abstuerzen.
	 */
	private int updateCount() {
		if (!FabricLoader.getInstance().isModLoaded("modmenu")) {
			return 0;
		}
		try {
			return ModMenuUpdates.count();
		} catch (Throwable t) {
			return 0;
		}
	}

	/** Hoehe des Fussblocks - wie der Kontoblock oben. */
	private int footerHeight() {
		return Math.max(Metrics.ACCOUNT_H,
			Math.max(Metrics.HEAD, Ui.advance(Ui.BODY_H, Ui.BODY_D, 2) + Ui.SMALL_H));
	}

	/**
	 * Unterkante der Mod-Liste - EINE Quelle fuer Zeichnen und Klicken.
	 *
	 * Muss den Fussblock mitrechnen, sonst laufen Mod-Zeilen darunter
	 * durch und man klickt auf etwas, das verdeckt ist.
	 */
	private int sidebarListBottom() {
		return sy + sh - Metrics.PAD - footerHeight() - 14;
	}

	/** Y des All-Mods-Knopfs. */
	private int allModsRowY() {
		return sidebarModsTop();
	}

	/** Y der ersten Mod-Zeile, unterhalb des All-Mods-Knopfs. */
	/** Y des Trennstrichs unter dem All-Mods-Knopf. */
	private int allModsDividerY() {
		return allModsRowY() + Metrics.ROW_H + 4;
	}

	private int modRowsTop() {
		// Trennstrich dazwischen, nicht nur Abstand.
		//
		// Mehr Luft allein reicht nicht - der Knopf sah weiter wie die
		// erste Mod der Liste aus, nur mit groesserem Abstand. Die
		// Sidebar trennt ihre Bloecke ohnehin ueberall mit einer Linie:
		// Kontoblock, Updates, Fussblock. Der gleiche Strich macht hier
		// sofort klar, dass "All Mods" UEBER der Liste steht.
		return allModsDividerY() + 9;
	}

	/**
	 * Eine Zeile der Navigation - Symbol, Beschriftung, Zustand.
	 *
	 * EINE Stelle fuer alle Zeilen, damit Auswahl und Mausberuehrung
	 * ueberall gleich aussehen. Die Klickbereiche kommen aus denselben
	 * Y-Werten, nicht aus einer zweiten Rechnung.
	 */
	private void drawNavRow(DrawContext ctx, int x, int y, int w,
	                        int mouseX, int mouseY, boolean selected,
	                        Identifier icon, HubEntry entry, String label,
	                        boolean boldLabel, boolean dimmed, boolean header) {
		boolean hover = Draw.inside(mouseX, mouseY, x - 4, y, w + 8, Metrics.ROW_H);
		if (selected) {
			Surface.row(ctx, x - 4, y, w + 8, Metrics.ROW_H, Theme.ROW_SELECTED);
		} else if (hover) {
			Surface.row(ctx, x - 4, y, w + 8, Metrics.ROW_H, Theme.ROW_HOVER);
		}
		// Symbol UND Beschriftung auf dieselbe Mittellinie der Zeile.
		int mid = y + Metrics.ROW_H / 2;
		int isz = Metrics.ROW_ICON;
		if (entry != null) {
			drawModIcon(ctx, entry, x, mid - isz / 2, isz);
		} else if (icon != null) {
			Surface.icon(ctx, icon, x, mid - isz / 2, isz,
				selected ? Theme.TEXT : Theme.TEXT_MUTED);
		}
		// Fehlende Mods ausgegraut - sie sind anklickbar, aber sichtbar
		// nicht dasselbe wie die laufenden.
		int textColour = dimmed
			? Theme.TEXT_FAINT
			: (selected ? Theme.TEXT : Theme.TEXT_MUTED);

		// Nur Mod-Zeilen lassen sich anpinnen. "All Mods" und der
		// Aktualisierungs-Knopf sind Navigation, keine Mods.
		boolean pinnable = entry != null;
		boolean pinned = pinnable && LumiLib.cfg.pinned.contains(entry.id());

		int room = w - isz - Metrics.ROW_ICON_GAP
			- (pinnable ? Metrics.PIN_SLOT : 0);
		String shown = boldLabel
			? Ui.fitBold(this.textRenderer, label, room)
			: Ui.fit(this.textRenderer, label, room);
		Text drawn = boldLabel ? Ui.bold(shown) : Ui.text(shown);
		int tx = x + isz + Metrics.ROW_ICON_GAP;

		// ZWEI REGELN, UND DAS IST ABSICHT:
		//
		// Die Mod-Liste bekommt eine gemeinsame Schreiblinie. Wuerde dort
		// jede Zeile nach ihrer eigenen Tinte gemittet, saesse "Perpetual
		// Snow" wegen des p knapp zwei Pixel tiefer als "LumiLib" - in
		// einer Liste untereinander sieht man das sofort.
		//
		// "All Mods" steht allein zwischen zwei Trennstrichen und muss mit
		// nichts fluchten. Dort zaehlt nur, dass oben und unten gleich
		// viel Luft bleibt - gemessen sass es sonst 0,98 Pixel zu hoch.
		if (header) {
			Ui.drawCenteredIn(ctx, this.textRenderer, drawn, tx,
				y, Metrics.ROW_H, Ui.BODY_H, Ui.BODY_D, Ui.TOP_BODY, textColour);
		} else {
			Ui.drawCenteredLine(ctx, this.textRenderer, drawn, tx, mid,
				Ui.BODY_H, Ui.BODY_D, Ui.TOP_BODY, textColour);
		}

		// STECKNADEL.
		//
		// Angepinnt: immer sichtbar, sonst waere nicht zu erkennen, warum
		// eine Mod oben steht. Nicht angepinnt: nur unter der Maus oder bei
		// Auswahl - sonst haette jede Zeile ein Zeichen, das nichts sagt.
		if (pinnable && (pinned || hover || selected)) {
			int pxi = pinX(x, w);
			boolean overPin = Draw.inside(mouseX, mouseY,
				pxi - 3, y, Metrics.PIN + 6, Metrics.ROW_H);
			// Auf einer ausgegrauten Zeile darf die Nadel nicht dunkler sein
			// als der Mod-Name daneben - sonst zieht das Symbol den Blick
			// auf eine Mod, die gar nicht installiert ist.
			int pinColour = dimmed
				? (overPin ? Theme.TEXT_MUTED : Theme.TEXT_FAINT)
				: pinned
					? (overPin ? Theme.TEXT_MUTED : Theme.TEXT)
					: (overPin ? Theme.TEXT : Theme.TEXT_FAINT);
			Surface.icon(ctx, Identifier.of(LumiLib.MOD_ID, "pin"),
				pxi, mid - Metrics.PIN / 2, Metrics.PIN, pinColour);
		}
	}

	/**
	 * X der Stecknadel - EINE Quelle fuer Zeichnen und Klicken.
	 *
	 * Steht hier und nicht zweimal im Code, weil genau dort frueher schon
	 * Zeichnung und Klickbereich auseinandergelaufen sind.
	 */
	private static int pinX(int x, int w) {
		return x + w - Metrics.PIN;
	}

	/** Anpinnen oder loesen, sofort gespeichert. */
	private void togglePin(String modId) {
		if (!LumiLib.cfg.pinned.remove(modId)) {
			LumiLib.cfg.pinned.add(modId);
		}
		LumiLib.save();
	}

	/** Y der ersten Mod-Zeile - EINE Quelle fuer Zeichnen und Klicken. */
	private int sidebarModsTop() {
		// OHNE SECTION_H. Die zwanzig Pixel hielten frueher Platz fuer die
		// Beschriftung "MODS" ueber der Liste frei. Die ist jetzt ein
		// Knopf, also stand der Platz unbegruendet als Luecke herum - in
		// der Vorschau sofort zu sehen gewesen.
		return sy + Metrics.PAD
			+ sessionBlockHeight()
			+ Metrics.ROW_H + 9;
	}

	private void renderSidebar(DrawContext ctx, int mouseX, int mouseY) {
		int x = sx + Metrics.PAD;
		int w = sw - Metrics.PAD * 2;
		int y = sy + Metrics.PAD;

		if (LumiLib.cfg.showSession) {
			int h = Metrics.HEAD;
			int tx0 = x + h + 8;

			// GEMEINSAME MITTELLINIE fuer Kopf und Textblock. Feste Versaetze
			// gehen nie auf, weil jede Schriftgroesse eine andere Zeilenhoehe
			// hat - das war die Ursache fast aller Fehlstellungen im ersten
			// Entwurf.
			// Zeilenabstand MIT Unterlaenge - siehe Ui.BODY_D. Ohne sie lief
			// die Statuszeile in das y von "Player314".
			int blockH = Ui.advance(Ui.BODY_H, Ui.BODY_D, 2) + Ui.SMALL_H;
			int rowH = Math.max(Metrics.ACCOUNT_H, Math.max(h, blockH));
			int rowMid = y + rowH / 2;

			PlayerSkinDrawer.draw(ctx, sessionSkin(), x, rowMid - h / 2, h);

			// EINE Zeile, auf dieselbe Mittellinie wie der Kopf.
			//
			// Die Statuszeile ist raus. Sie hat nichts gezeigt, was man
			// nicht ohnehin weiss - und zwei Zeilen neben einem 22 Pixel
			// hohen Kopf wirken gedraengt. Mittig zu zentrieren geht nur
			// bei einer Zeile sauber auf.
			Ui.drawCenteredLine(ctx, this.textRenderer,
				Ui.bold(Ui.fitBold(this.textRenderer, LumiLib.sessionName(), w - h - 8)),
				tx0, rowMid, Ui.BODY_H, Ui.BODY_D, Ui.TOP_BODY, Theme.TEXT);

			y += rowH + 10;
			Draw.hLine(ctx, x, y, w, Theme.DIVIDER);
			y += 9;
		}

		// UPDATE-ZEILE
		//
		// Vorher stand hier fest "Updates via Mod Menu" - eine Beschriftung
		// ohne Funktion. Jetzt wird wirklich gezaehlt. Die Zahl kommt von
		// Mod Menus eigener Pruefung, die beim Start ohnehin laeuft; eine
		// zweite eigene Abfrage waere nur unnoetiger Netzverkehr.
		int updates = updateCount();
		this.updatesRowY = y;
		this.updatesClickable = updates > 0;

		boolean updHover = this.updatesClickable
			&& mouseX >= x && mouseX < x + w
			&& mouseY >= y && mouseY < y + Metrics.ROW_H;

		Text label;
		int colour;
		if (updates > 0) {
			label = Text.translatable(updates == 1
				? "lumilib.updates.one" : "lumilib.updates.many", updates);
			colour = updHover ? Theme.TEXT : Theme.TEXT_MUTED;
		} else {
			label = Text.translatable("lumilib.updates.none");
			colour = Theme.TEXT_FAINT;
		}
		Ui.drawTop(ctx, this.textRenderer, Ui.of(label),
			x, y + 3, Ui.TOP_BODY, colour);

		if (updates > 0) {
			// Punkt rechts aussen statt eines Pfeils - ein Pfeil sieht nach
			// Aufklappen aus, hier wird aber ein anderer Bildschirm geoeffnet.
			int dot = 4;
			Surface.icon(ctx, Identifier.of(LumiLib.MOD_ID, "dot_fill"),
				x + w - dot, y + 3 + Ui.BODY_H / 2 - dot / 2, dot, Theme.OK);
		}

		y += Metrics.ROW_H + 4;
		Draw.hLine(ctx, x, y, w, Theme.DIVIDER);

		// ALL-MODS-KNOPF statt der frueheren Beschriftung "MODS".
		//
		// Vorher stand dort ein totes Woertchen. Jetzt ist es der Weg
		// zurueck zur Uebersicht - und die Sidebar wird damit zu dem,
		// was sie ohnehin aussah: eine Navigation.
		y = allModsRowY();
		int isz = Metrics.ROW_ICON;
		drawNavRow(ctx, x, y, w, mouseX, mouseY, selectedId == null,
			Identifier.of(LumiLib.MOD_ID, "loader"), null,
			Text.translatable("lumilib.content.all_mods").getString(),
			true, false, true);
		Draw.hLine(ctx, x, allModsDividerY(), w, Theme.DIVIDER);

		y = modRowsTop();
		int limit = sidebarListBottom();
		for (HubEntry e : visible) {
			if (y + Metrics.ROW_H > limit) {
				break;
			}
			drawNavRow(ctx, x, y, w, mouseX, mouseY,
				e.id().equals(selectedId), null, e, e.name().getString(), false,
				e.state() == HubEntry.State.MISSING, false);
			y += Metrics.ROW_H;
		}

		if (visible.isEmpty()) {
			Ui.drawTop(ctx, this.textRenderer,
				Ui.text(Ui.fit(this.textRenderer,
					Text.translatable("lumilib.sidebar.empty").getString(), w)),
				x, y + 4, Ui.TOP_BODY, Theme.TEXT_FAINT);
		}

		// FUSSBLOCK - gleicher Aufbau wie der Kontoblock oben: Symbol links,
		// zwei Zeilen rechts. Dadurch rahmen beide Bloecke die Mod-Liste
		// symmetrisch ein, statt dass unten eine einsame Zeile steht.
		int blockH = Ui.advance(Ui.BODY_H, Ui.BODY_D, 2) + Ui.SMALL_H + Ui.SMALL_D;
		int rowH = footerHeight();
		int fy = sy + sh - Metrics.PAD - rowH;
		// ZEHN, nicht neun. Der Kontoblock oben setzt seinen Trennstrich
		// mit zehn Abstand; mit neun stand das Fussymbol 13 Pixel unter
		// dem Strich, aber 14 ueber der Sidebar-Unterkante. Ein Pixel
		// Unterschied - sichtbar genug, um zu stoeren.
		Draw.hLine(ctx, x, fy - 10, w, Theme.DIVIDER);

		int mid = fy + rowH / 2;
		int iy = mid - Metrics.HEAD / 2;
		Identifier loaderTex = LoaderIcon.get();
		if (loaderTex != null) {
			// Echtes Laderlogo, in Originalfarbe - nicht eingefaerbt,
			// aber mit runden Ecken wie die Mod-Icons.
			if (!Sdf.roundedTexture(ctx, loaderTex, x, iy,
					Metrics.HEAD, Metrics.HEAD, 6, -1)) {
				ctx.drawTexture(RenderPipelines.GUI_TEXTURED, loaderTex,
					x, iy, 0.0F, 0.0F, Metrics.HEAD, Metrics.HEAD,
					Metrics.HEAD, Metrics.HEAD);
			}
		} else {
			Surface.icon(ctx, Identifier.of(LumiLib.MOD_ID, "loader"),
				x, iy, Metrics.HEAD, Theme.TEXT_MUTED);
		}

		int tx = x + Metrics.HEAD + 8;
		int tTop = mid - blockH / 2;
		Ui.drawTop(ctx, this.textRenderer,
			Ui.bold("Fabric " + LumiLib.minecraftVersion()),
			tx, tTop, Ui.TOP_BODY, Theme.TEXT);
		Ui.drawTop(ctx, this.textRenderer,
			Ui.small("LumiLib " + LumiLib.version()),
			tx, tTop + Ui.advance(Ui.BODY_H, Ui.BODY_D, 2),
			Ui.TOP_SMALL, Theme.TEXT_FAINT);
	}

	private void renderContent(DrawContext ctx, int mouseX, int mouseY) {
		this.settingsBtnShown = false;
		HubEntry open = selected();
		// Auf einer Mod-Seite gibt es kein Suchfeld - dann auch keinen
		// Fokus, sonst frisst es weiter Tastendruecke.
		if (open != null) {
			this.search.setFocused(false);
		} else {
			// Umgekehrt genauso: Das Suchfeld der Mod-Seite darf auf der
			// Uebersicht keine Tasten mehr abfangen.
			this.pageSearch.setFocused(false);
		}
		if (open != null) {
			renderModPage(ctx, open, mouseX, mouseY);
			return;
		}
		renderOverview(ctx, mouseX, mouseY);
	}

	/** Die ausgewaehlte Mod, oder null fuer die Uebersicht. */
	private HubEntry selected() {
		if (selectedId == null) {
			return null;
		}
		for (HubEntry e : HubEntry.all()) {
			if (e.id().equals(selectedId)) {
				return e;
			}
		}
		// Kennung zeigt ins Leere - zurueck auf die Uebersicht statt
		// eine leere Seite zu zeigen.
		selectedId = null;
		return null;
	}

	private void renderOverview(DrawContext ctx, int mouseX, int mouseY) {
		// Ueberschrift in der GROSSEN Staffel - ohne Groessenunterschied
		// liest sich nichts als Hierarchie.
		Text heading = Text.translatable("lumilib.content.all_mods");
		// cy+10, nicht cy+4: im Entwurf liegt die Tinte der Ueberschrift bei
		// 63,5 (logisch), der alte Wert ergab 57. Die Suchpille bleibt bei
		// cy+4 - im Entwurf gemessen 57,5, die sitzt also bereits richtig.
		// Ueberschrift und Pille sind dort bewusst NICHT auf einer Linie.
		int headY = cy + 10;
		Ui.drawTop(ctx, this.textRenderer, Ui.titleOf(heading), cx, headY,
			Ui.TOP_TITLE, Theme.TEXT);


		// Suchfeld, buendig an der rechten Inhaltskante. Zeichnet sich
		// selbst - Lupe, Beschriftung und Schreibmarke - und merkt sich
		// dabei seine Lage fuer Klicks.
		int fieldW = Math.min(Metrics.SEARCH_W, Math.max(80, cw / 3));
		this.search.render(ctx, this.textRenderer,
			cx + cw - fieldW,
			cy + (Metrics.TITLE_H - Metrics.SEARCH_H) / 2,
			fieldW, Metrics.SEARCH_H, mouseX, mouseY);

		int rows = (visible.size() + cols - 1) / cols;
		this.contentHeight = rows * (Metrics.CARD_H + Metrics.CARD_GAP);
		int listH = listBottom - listTop;
		int maxScroll = Math.max(0, contentHeight - listH);
		if (scroll > maxScroll) {
			scroll = maxScroll;
		}

		ctx.enableScissor(cx, listTop, cx + cw, listBottom);
		Sdf.clip(new ScreenRect(cx, listTop, cw, listBottom - listTop));
		for (int i = 0; i < visible.size(); i++) {
			int bx = cx + (i % cols) * (colW + Metrics.CARD_GAP);
			int by = listTop + (i / cols) * (Metrics.CARD_H + Metrics.CARD_GAP) - scroll;
			if (by + Metrics.CARD_H < listTop || by > listBottom) {
				continue;
			}
			boolean hover = Draw.inside(mouseX, mouseY, bx, by, colW, Metrics.CARD_H)
				&& mouseY >= listTop && mouseY < listBottom;
			drawCard(ctx, visible.get(i), bx, by, colW, hover);
		}
		Sdf.clip(null);
		ctx.disableScissor();
	}

	/**
	 * Die Seite EINER Mod.
	 *
	 * Aufbau wie die Uebersicht: Ueberschrift auf derselben Zeile, darunter
	 * der Inhalt. Dadurch springt beim Wechseln nichts.
	 *
	 * ZUM KNOPF:
	 * Er oeffnet vorerst den EIGENEN Einstellungsbildschirm der Mod - also
	 * YACL ueber Mod Menu. Das ist bewusst eine Zwischenloesung und steht
	 * deshalb als Knopf da und nicht als eingebaute Liste: eigene
	 * Bedienelemente - Schalter, Schieber, Farbwaehler - sind ein eigenes
	 * Stueck Arbeit. Bis dahin funktioniert die Navigation schon
	 * vollstaendig.
	 */
	private void renderModPage(DrawContext ctx, HubEntry mod, int mouseX, int mouseY) {
		int headY = cy + 10;
		Ui.drawTop(ctx, this.textRenderer, Ui.titleOf(mod.name()), cx, headY,
			Ui.TOP_TITLE, Theme.TEXT);

		// Kopfzeile rechts: Zuruecksetzen ganz aussen, Suchfeld davor.
		//
		// NUR BEI EINER SEITE, DIE DER HUB SELBST ZEICHNET. Bei einer Mod
		// mit fremdem Screen gibt es hier nichts zu durchsuchen und nichts
		// zurueckzusetzen - ein Knopf, der nichts tut, ist schlimmer als
		// keiner.
		SettingsPage page = settingsPage(mod);
		this.pageResetShown = false;
		this.pageSearchShown = false;
		if (page != null) {
			int top = cy + (Metrics.TITLE_H - Metrics.SEARCH_H) / 2;
			int right = cx + cw;

			if (page.canResetAny()) {
				this.pageResetX = right - Metrics.RESET_ALL;
				this.pageResetY = top + (Metrics.SEARCH_H - Metrics.RESET_ALL) / 2;
				this.pageResetShown = true;
				boolean all = !anyOffDefault(page);
				boolean over = Draw.inside(mouseX, mouseY,
					this.pageResetX - 5, top, Metrics.RESET_ALL + 10, Metrics.SEARCH_H);
				Surface.icon(ctx, Identifier.of(LumiLib.MOD_ID, "undo"),
					this.pageResetX, this.pageResetY, Metrics.RESET_ALL,
					all ? Theme.TEXT_FAINT : (over ? Theme.TEXT : Theme.TEXT_MUTED));
				right -= Metrics.RESET_ALL + 12;
			}

			int fieldW = Math.min(Metrics.SEARCH_W, Math.max(80, cw / 3));
			// Nur anbieten, wenn es ueberhaupt etwas zu sieben gibt. Bei
			// drei Zeilen ist ein Suchfeld Dekoration.
			if (countRows(page) >= 6) {
				this.pageSearchShown = true;
				this.pageSearch.render(ctx, this.textRenderer,
					right - fieldW, top, fieldW, Metrics.SEARCH_H, mouseX, mouseY);
			} else {
				this.pageSearch.clear();
			}
		}
		// Nur wenn es wirklich eine Version gibt. Eine fehlende Mod hat
		// keine - dann stand dort ein nacktes "v".
		if (!mod.version().isEmpty()) {
			Ui.drawTop(ctx, this.textRenderer, Ui.small("v" + mod.version()),
				cx + Ui.widthTitle(this.textRenderer, mod.name().getString()) + 8,
				headY + Ui.TITLE_H - Ui.SMALL_H, Ui.TOP_SMALL, Theme.TEXT_FAINT);
		}

		// ROLLEN WIE BEI DER UEBERSICHT, mit demselben Schnittfenster.
		// Die Ueberschrift bleibt stehen, alles darunter faehrt darunter
		// durch - ohne das Fenster wuerde eine Gruppe beim Hochrollen ueber
		// den Titel laufen.
		int maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
		if (scroll > maxScroll) {
			scroll = maxScroll;
		}
		// BEIDES setzen: enableScissor schneidet Minecrafts Aufrufe,
		// Sdf.clip die eigenen Flaechen und Buchstaben. Fehlt das zweite,
		// laeuft die gerollte Seite ueber den Fensterrand und zeichnet
		// ueber die Ueberschrift.
		ctx.enableScissor(cx, listTop, cx + cw, listBottom);
		Sdf.clip(new ScreenRect(cx, listTop, cw, listBottom - listTop));
		int bottom = renderModBody(ctx, mod, cx, listTop - scroll, cw, mouseX, mouseY);
		Sdf.clip(null);
		ctx.disableScissor();
		contentHeight = bottom - (listTop - scroll);

		// AUSSERHALB des Schnittfensters: Der Farbwaehler liegt ueber der
		// Seite und soll ueber deren Rand hinausragen duerfen. Innerhalb
		// waere er an derselben Kante abgeschnitten wie die Liste.
		ColourPopup.render(ctx, this.textRenderer, mouseX, mouseY);
	}

	/**
	 * Der rollbare Teil der Mod-Seite.
	 *
	 * Gibt die Unterkante des Gezeichneten zurueck - daraus entsteht die
	 * Rollhoehe. Sie hier zu MESSEN statt getrennt auszurechnen ist der
	 * Grund, warum sie nicht auseinanderlaufen kann.
	 */
	private int renderModBody(DrawContext ctx, HubEntry mod, int x, int y, int w,
	                          int mouseX, int mouseY) {
		// ZUERST zuruecksetzen: Die Methode hat mehrere Ausgaenge. Wuerde
		// das Merkmal nur im Erfolgsfall gesetzt, bliebe bei einer Mod ohne
		// Einstellungen die Lage der vorigen stehen - und ein Klick ins
		// Leere wuerde dort einen Schalter umlegen.
		this.pageListShown = false;

		// Kopfbereich: grosses Symbol und Beschreibung nebeneinander.
		int isz = 32;
		int pad = Metrics.CARD_PAD;
		java.util.List<String> lines = Ui.wrap(this.textRenderer,
			mod.description().getString(), w - pad * 2 - isz - 12);
		int textH = lines.size() * Ui.advance(Ui.BODY_H, Ui.BODY_D, 3);
		int boxH = Math.max(isz, textH) + pad * 2;

		if (!Surface.glassCard(ctx, x, y, w, boxH, Theme.CARD)) {
			Surface.card(ctx, x, y, w, boxH, Theme.CARD);
			Surface.rimCard(ctx, x, y, w, boxH, Theme.RIM_CARD);
		}
		drawModIcon(ctx, mod, x + pad, y + pad, isz);

		int tx = x + pad + isz + 12;
		// Kurzer Text mittig zum Symbol statt oben buendig - sonst klebt
		// eine einzelne Zeile an der Oberkante eines 32 Pixel hohen
		// Symbols.
		int ty = y + pad + Math.max(0, (isz - textH) / 2);
		for (String line : lines) {
			Ui.drawTop(ctx, this.textRenderer, Ui.text(line),
				tx, ty, Ui.TOP_BODY, Theme.TEXT_MUTED);
			ty += Ui.advance(Ui.BODY_H, Ui.BODY_D, 3);
		}

		y += boxH + Metrics.CARD_GAP;

		// Je nach Zustand ein anderer Knopf - oder keiner.
		if (mod.state() == HubEntry.State.MISSING) {
			if (mod.modrinth() != null) {
				drawActionButton(ctx, x, y, w, mouseX, mouseY,
					Text.translatable("lumilib.mod.get").getString(),
					ButtonAction.MODRINTH);
				return y + 22;
			}
			Ui.drawTop(ctx, this.textRenderer,
				Ui.text(Text.translatable("lumilib.mod.no_link").getString()),
				x, y + 3, Ui.TOP_BODY, Theme.TEXT_FAINT);
			return y + 3 + Ui.BODY_H;
		}
		if (mod.state() == HubEntry.State.PRESENT) {
			Ui.drawTop(ctx, this.textRenderer,
				Ui.text(Text.translatable("lumilib.mod.not_linked").getString()),
				x, y + 3, Ui.TOP_BODY, Theme.TEXT_FAINT);
			return y + 3 + Ui.BODY_H;
		}

		// EINSTELLUNGEN IM HUB SELBST - der eigentliche Weg.
		//
		// Ein Knopf, der einen zweiten Bildschirm aufmacht, war immer nur
		// ein Platzhalter: Man verlaesst den Hub, sieht eine fremde
		// Oberflaeche und muss sich zurueckdruecken. Bringt die Mod eine
		// SettingsPage mit, steht alles hier.

		// Vom Server abgeschaltet: oben eine rote Zeile, darunter die
		// Einstellungen wie immer. Die bleiben bedienbar - sie gelten
		// wieder, sobald man den Server verlaesst, und genau das sagt der
		// Text. Bei jedem Bild gelesen: Schickt der Server neue Regeln,
		// waehrend der Hub offen ist, stimmt die Zeile sofort.
		if (ServerRules.disabled(mod.id())) {
			String line = Ui.fit(this.textRenderer,
				Text.translatable("lumilib.server.disabled").getString(), w);
			Ui.drawTop(ctx, this.textRenderer, Ui.text(line),
				x, y + 3, Ui.TOP_BODY, Theme.WARN);
			y += 3 + Ui.advance(Ui.BODY_H, Ui.BODY_D, 6);
		}

		SettingsPage page = settingsPage(mod);
		if (page != null) {
			// Lage MERKEN statt beim Klick neu ausrechnen. Genau diese
			// zweite Rechnung hat im Hub schon einmal Klickflaechen um acht
			// Pixel verschoben.
			this.pageListX = x;
			this.pageListY = y;
			this.pageListW = w;
			this.pageListShown = true;
			String q = pageQuery();
			// Eine leere Liste ohne Erklaerung sieht aus wie ein Fehler.
			if (SettingsList.empty(page, q)) {
				this.pageListShown = false;
				Ui.drawTop(ctx, this.textRenderer,
					Ui.text(Text.translatable("lumilib.search.empty").getString()),
					x + Metrics.SET_INSET, y + 6, Ui.TOP_BODY, Theme.TEXT_FAINT);
				return y + 6 + Ui.advance(Ui.BODY_H, Ui.BODY_D, 3);
			}
			SettingsList.render(ctx, this.textRenderer, page, x, y, w, mouseX, mouseY, q);
			return y + SettingsList.height(page, w, q);
		}

		// Knopf - nur wenn die Mod ueberhaupt Einstellungen mitbringt.
		if (mod.mod() != null && mod.mod().hasSettings()) {
			String label = Text.translatable("lumilib.mod.open_settings").getString();
			drawActionButton(ctx, x, y, w, mouseX, mouseY, label,
				ButtonAction.SETTINGS);
			return y + 22;
		}
		Ui.drawTop(ctx, this.textRenderer,
			Ui.text(Text.translatable("lumilib.mod.no_settings").getString()),
			x, y + 3, Ui.TOP_BODY, Theme.TEXT_FAINT);
		return y + 3 + Ui.BODY_H;
	}

	/** Ist die linke Maustaste gerade gedrueckt? */
	private boolean mouseDown() {
		long handle = this.client == null ? 0L : this.client.getWindow().getHandle();
		return handle != 0L
			&& org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle,
				org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
				== org.lwjgl.glfw.GLFW.GLFW_PRESS;
	}

	/** Letzte bekannte Mausposition - der Waehler braucht beide Achsen. */
	private int lastMouseY;

	/**
	 * Fuehrt einen gezogenen Regler nach und beendet das Ziehen.
	 *
	 * WARUM HIER UND NICHT IN mouseDragged: Minecraft hat die Signatur
	 * dieser Methode in den letzten Versionen mehrfach geaendert. Der
	 * Zustand der Maustaste laesst sich direkt bei GLFW erfragen - das
	 * ist seit Jahren dieselbe Funktion und haengt an keiner
	 * Minecraft-Version.
	 */
	private void updateDrag(int mouseX) {
		if (ColourPopup.dragging()) {
			if (mouseDown()) {
				ColourPopup.drag(mouseX, lastMouseY);
			} else {
				ColourPopup.endDrag();
				SettingsPage p = pageCache;
				if (p != null) {
					p.saveNow();
				}
			}
			return;
		}
		if (this.dragging == null) {
			return;
		}
		boolean down = mouseDown();

		HubEntry open = selected();
		SettingsPage page = open == null ? null : settingsPage(open);

		if (!down || page == null) {
			// Einmal am Ende speichern, nicht in jedem Bild.
			if (page != null) {
				page.saveNow();
			}
			this.dragging = null;
			return;
		}
		SettingsList.drag(this.dragging, mouseX);
	}

	/**
	 * Die Einstellungsseite der gewaehlten Mod, einmal gebaut.
	 *
	 * ZWISCHENSPEICHER, UND ZWAR ZWINGEND: Der Erbauer legt bei jedem Aufruf
	 * neue Listen an. Ohne Speicher waere das sechzig Mal pro Sekunde -
	 * Muell, den niemand braucht. Der Schluessel ist die Mod-Kennung; wechselt
	 * die Auswahl, wird neu gebaut.
	 */
	private String pageOwner;
	private SettingsPage pageCache;

	/** Lage der zuletzt gezeichneten Einstellungsliste. */
	private int pageListX, pageListY, pageListW;
	private boolean pageListShown;

	/** Suchfeld im Kopf der Mod-Seite - eigenes, nicht das der Uebersicht. */
	private final SearchField pageSearch = new SearchField();
	private boolean pageSearchShown;
	private boolean pageResetShown;
	private int pageResetX, pageResetY;

	/**
	 * Nach jeder Eingabe im Suchfeld der Mod-Seite.
	 *
	 * Der Farbwaehler MUSS zu: Er haengt an der Lage einer bestimmten
	 * Zeile, und die verschiebt sich beim Filtern - oder verschwindet.
	 * Stehen bliebe ein Waehler, der auf eine andere Farbe schreibt.
	 */
	private void onPageSearchChanged() {
		this.scroll = 0;
		SettingsList.reset();
		this.dragging = null;
	}

	/** Die Suche der Mod-Seite, oder "" wenn gerade keine sichtbar ist. */
	private String pageQuery() {
		return pageSearchShown ? this.pageSearch.text() : "";
	}

	/** Steht irgendeine Zeile der Seite NICHT auf ihrem Standardwert? */
	private static boolean anyOffDefault(SettingsPage page) {
		for (SettingsPage.Section s : page.sections()) {
			for (SettingsPage.Row r : s.rows()) {
				if (page.canReset(r) && !page.isDefault(r)) {
					return true;
				}
			}
		}
		return false;
	}

	private static int countRows(SettingsPage page) {
		int n = 0;
		for (SettingsPage.Section s : page.sections()) {
			n += s.rows().size();
		}
		return n;
	}

	/**
	 * Der Regler, an dem gerade gezogen wird.
	 *
	 * Beim Ziehen verlaesst der Zeiger staendig die Zeile. Wuerde bei jeder
	 * Bewegung neu gesucht, welche Zeile getroffen ist, bliebe der Regler
	 * beim ersten Ausrutscher nach oben oder unten stehen.
	 */
	private SettingsList.Hit dragging;

	private SettingsPage settingsPage(HubEntry mod) {
		if (mod.mod() == null || !mod.mod().hasInlineSettings()) {
			return null;
		}
		if (!mod.id().equals(pageOwner)) {
			// Aufgeklappte Farbzeile der VORIGEN Mod schliessen - sonst
			// bliebe eine Zeile offen, die es auf dieser Seite nicht gibt.
			SettingsList.reset();
			// Suche der vorigen Mod loeschen. Sonst landet man auf der
			// neuen Seite in einer gefilterten Liste, ohne zu sehen,
			// warum die Haelfte fehlt.
			this.pageSearch.clear();
			this.pageSearch.setFocused(false);
			// Faellt der Erbauer der Mod um, soll das den Hub nicht
			// mitreissen - dann eben der Knopf als Rueckfallebene.
			try {
				pageCache = mod.mod().settings().get();
			} catch (Throwable t) {
				LumiLib.LOGGER.warn("[LumiLib] Could not build the settings page for "
					+ mod.id() + ".", t);
				pageCache = null;
			}
			pageOwner = mod.id();
		}
		return pageCache;
	}

	/** Was der Knopf auf der Mod-Seite tut. */
	private enum ButtonAction { SETTINGS, MODRINTH }

	private ButtonAction buttonAction = ButtonAction.SETTINGS;

	/**
	 * Der eine Knopf der Mod-Seite.
	 *
	 * EINE Stelle fuer Zeichnen UND Lage - die Klickpruefung liest
	 * dieselben Felder. Zwei getrennte Rechnungen waeren genau der
	 * Fehlertyp, der uns die verschobenen Klickbereiche eingebracht hat.
	 */
	private void drawActionButton(DrawContext ctx, int x, int y, int w,
	                              int mouseX, int mouseY, String label,
	                              ButtonAction action) {
		int bw = Math.min(w, Ui.widthBold(this.textRenderer, label) + 28);
		int bh = 22;
		boolean hover = Draw.inside(mouseX, mouseY, x, y, bw, bh);
		int fill = hover ? Theme.CARD_HOVER : Theme.CARD;
		if (!Surface.glassCard(ctx, x, y, bw, bh, fill)) {
			Surface.card(ctx, x, y, bw, bh, fill);
		}
		Ui.drawCenteredLine(ctx, this.textRenderer, Ui.bold(label),
			x + (bw - Ui.widthBold(this.textRenderer, label)) / 2,
			y + bh / 2, Ui.BODY_H, Ui.BODY_D, Ui.TOP_BODY,
			hover ? Theme.TEXT : Theme.TEXT_MUTED);

		this.settingsBtnX = x;
		this.settingsBtnY = y;
		this.settingsBtnW = bw;
		this.settingsBtnH = bh;
		this.settingsBtnShown = true;
		this.buttonAction = action;
	}

	private void drawCard(DrawContext ctx, HubEntry mod, int x, int y, int w, boolean hover) {
		Surface.shadowCard(ctx, x, y, w, Metrics.CARD_H, 3, 2, Theme.SHADOW_CARD);

		// Ein Aufruf statt vier. Die alte Folge hatte eine sichtbare Naht
		// auf halber Hoehe - gemessen 21 Helligkeitsstufen Sprung.
		int fill = hover ? Theme.CARD_HOVER : Theme.CARD;
		if (!Surface.glassCard(ctx, x, y, w, Metrics.CARD_H, fill)) {
			Surface.card(ctx, x, y, w, Metrics.CARD_H, fill);
			Surface.innerCard(ctx, x, y, w, Metrics.CARD_H, Theme.INNER_CARD);
			Surface.bevelCard(ctx, x, y, w, Metrics.CARD_H,
				Theme.BEVEL_LIGHT_CARD, Theme.BEVEL_DARK_CARD);
			Surface.rimCard(ctx, x, y, w, Metrics.CARD_H, Theme.RIM_CARD);
		}

		int p = Metrics.CARD_PAD;
		int isz = Metrics.CARD_ICON;

		// Icon MITTIG zum Block aus Name und Version, nicht oben buendig.
		int blockH = Ui.advance(Ui.BODY_H, Ui.BODY_D, 2) + Ui.SMALL_H;
		int rowH = Math.max(isz, blockH);
		int mid = y + p + rowH / 2;

		drawModIcon(ctx, mod, x + p, mid - isz / 2, isz);

		int tx = x + p + isz + 9;
		int tw = w - (tx - x) - p;
		int nameTop = mid - blockH / 2;

		Text name = Ui.bold(Ui.fitBold(this.textRenderer, mod.name().getString(), tw));
		if (mod.version().isEmpty()) {
			// Keine Version, also auch keine zweite Zeile - und dann gehoert
			// der Name auf die Mitte des Symbols, sonst klebt er oben und
			// darunter steht Leere. Vorher stand hier ein nacktes "v".
			Ui.drawCenteredIn(ctx, this.textRenderer, name, tx,
				mid - isz / 2, isz, Ui.BODY_H, Ui.BODY_D, Ui.TOP_BODY, Theme.TEXT);
		} else {
			Ui.drawTop(ctx, this.textRenderer, name, tx, nameTop,
				Ui.TOP_BODY, Theme.TEXT);
			Ui.drawTop(ctx, this.textRenderer,
				Ui.small(Ui.fitSmall(this.textRenderer, "v" + mod.version(), tw)),
				tx, nameTop + Ui.advance(Ui.BODY_H, Ui.BODY_D, 2),
				Ui.TOP_SMALL, Theme.TEXT_FAINT);
		}

		Ui.drawTop(ctx, this.textRenderer,
			Ui.text(Ui.fit(this.textRenderer, mod.description().getString(), w - p * 2)),
			x + p, y + p + rowH + 6, Ui.TOP_BODY, Theme.TEXT_MUTED);
	}

	/**
	 * Icon einer Mod.
	 *
	 * VIER STUFEN, in dieser Reihenfolge:
	 * 1. Ein bei der Anmeldung ausdruecklich angegebenes Sprite. Steht
	 *    vorn, weil "ausdruecklich" sonst nichts bedeutet - der
	 *    Normalfall gibt hier null an und faellt auf Stufe 2.
	 * 2. Das Icon der INSTALLIERTEN Mod - liegt ohnehin in deren Jar und
	 *    wird auch von Mod Menu und Modrinth angezeigt. Es ist damit
	 *    immer das AKTUELLE, auch nach einem Update der Mod.
	 * 3. Das mitgelieferte Icon aus CatalogIcons - fuer Mods, die fehlen
	 *    und deshalb kein Jar haben, aus dem sich etwas lesen liesse.
	 * 4. Das Standard-Icon: Kaestchen mit Wuerfel. Greift nur noch bei
	 *    einer fremden Mod ohne lesbares Icon.
	 *
	 * KEIN ANFANGSBUCHSTABE MEHR:
	 * Der stand frueher an letzter Stelle. Bei fuenf Eintraegen sah die
	 * Liste damit aus wie eine Tabelle aus Buchstaben, und bei zwei Mods
	 * mit gleichem Anfangsbuchstaben war er ohnehin nichtssagend.
	 *
	 * Gezeichnet wird mit runden Ecken. Mod-Icons sind fuer Modrinth
	 * gemacht und haben meist einen deckenden Hintergrund - roh gezeichnet
	 * waere das ein aufgeklebtes Quadrat zwischen lauter runden Flaechen.
	 */
	private void drawModIcon(DrawContext ctx, HubEntry mod, int x, int y, int size) {
		// Radius wie bei den Zeilen: ein Viertel der Kante, aber hoechstens 6.
		int radius = Math.min(6, Math.max(2, size / 4));

		// Ausdruecklich angegebenes Sprite zuerst: Wer bei der Anmeldung
		// eines mitgibt, will bewusst ein anderes Bild als im Mod-Ordner.
		// Stuende das Jar-Icon davor, liesse sich das nie uebersteuern.
		Identifier own = mod.mod() == null ? null : mod.mod().icon();
		if (own != null) {
			// OHNE RUNDUNG, und das ist kein Versehen: Ein angemeldetes
			// Sprite liegt im GUI-Atlas. Aus dem Atlas kann der eigene
			// Shader nicht lesen - er bekaeme Minecrafts Ersatztextur und
			// zeichnete das schwarz-magenta Karo. Genau das ist passiert,
			// als diese Stufe nach vorn rutschte. Wer hier etwas anmeldet,
			// bekommt also die Ecken, die er selbst mitliefert.
			ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, own, x, y, size, size);
			return;
		}
		// Fehlende Mods haben kein Jar und damit kein Icon - der Versuch
		// waere in jedem Bild derselbe Fehlschlag.
		Identifier installed = mod.state() == HubEntry.State.MISSING
			? null : ModIcons.get(mod.id());
		if (installed != null) {
			if (Sdf.roundedTexture(ctx, installed, x, y, size, size, radius, -1)) {
				return;
			}
			ctx.drawTexture(RenderPipelines.GUI_TEXTURED, installed,
				x, y, 0.0F, 0.0F, size, size, size, size);
			return;
		}
		// Mitgeliefertes Icon - greift genau dann, wenn die Mod fehlt.
		// Leicht durchscheinend, damit "nicht installiert" auch am Bild
		// zu sehen ist und nicht nur an der blassen Schrift.
		Identifier bundled = CatalogIcons.get(mod.id());
		if (bundled != null) {
			// 0xD9 statt voll deckend: gerade genug, um sich von einer
			// installierten Mod zu unterscheiden, und nicht so blass,
			// dass das Icon ausgewaschen wirkt.
			int tint = mod.state() == HubEntry.State.MISSING ? 0xD9FFFFFF : -1;
			if (Sdf.roundedTexture(ctx, bundled, x, y, size, size, radius, tint)) {
				return;
			}
			ctx.drawTexture(RenderPipelines.GUI_TEXTURED, bundled,
				x, y, 0.0F, 0.0F, size, size, size, size);
			return;
		}
		drawDefaultIcon(ctx, x, y, size, mod.state() == HubEntry.State.MISSING);
	}

	/**
	 * Standard-Icon: Kaestchen mit Wuerfel.
	 *
	 * ZWEI TEILE statt einem Bild, und zwar mit Absicht: Das Kaestchen ist
	 * dasselbe Sprite wie bei den Zeilen und hat damit automatisch denselben
	 * Eckradius. Haette ich Kaestchen und Wuerfel in eine Textur gebacken,
	 * muesste ich den Radius an zwei Stellen gleich halten.
	 *
	 * WARUM EIN WUERFEL und kein Umriss: In der Sidebar sind es 13 logische
	 * Pixel, der Wuerfel darin etwa 8. Strichzeichnungen werden dort zu
	 * Grau. Tiefe steckt deshalb in drei Deckkraftstufen innerhalb der
	 * Textur, nicht in Linien.
	 */
	private void drawDefaultIcon(DrawContext ctx, int x, int y, int size, boolean faint) {
		Surface.row(ctx, x, y, size, size, Theme.ICON_BG);
		// 58 % der Kante, auf gerade Pixel gerundet - sonst sitzt der
		// Wuerfel bei ungerader Differenz einen halben Pixel schief.
		int inner = Math.max(6, (int) (size * 0.58F) & ~1);
		int off = (size - inner) / 2;
		Surface.icon(ctx, ICON_MOD_DEFAULT, x + off, y + off, inner,
			faint ? Theme.TEXT_FAINT : Theme.TEXT_MUTED);
	}

	// ------------------------------------------------------------------
	// Eingabe
	// ------------------------------------------------------------------

	/**
	 * Escape geht ERST eine Ebene zurueck.
	 *
	 * Auf einer Mod-Seite schliesst Escape nicht den ganzen Hub, sondern
	 * fuehrt zur Uebersicht - so wie man es von jedem Menue kennt. Erst
	 * von der Uebersicht aus schliesst es wirklich.
	 */
	@Override
	public boolean charTyped(net.minecraft.client.input.CharInput input) {
		if (this.search.charTyped(input)) {
			this.scroll = 0;
			return true;
		}
		if (this.pageSearch.charTyped(input)) {
			onPageSearchChanged();
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
		// Offener Farbwaehler schluckt Escape - sonst schliesst sich
		// gleich die ganze Mod-Seite mit.
		if (ColourPopup.isOpen()
			&& input.getKeycode() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			ColourPopup.close();
			return true;
		}
		if (this.search.keyPressed(input)) {
			this.scroll = 0;
			return true;
		}
		if (this.pageSearch.keyPressed(input)) {
			onPageSearchChanged();
			return true;
		}
		if (input.getKeycode() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
			&& selectedId != null) {
			selectedId = null;
			scroll = 0;
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = click.x();
		double my = click.y();

		// Der Farbwaehler liegt oben auf und bekommt den Klick ZUERST,
		// noch vor super: Sonst greift ein Widget darunter. Ein Klick
		// daneben schliesst ihn und wird geschluckt - wer zumacht, will
		// nicht gleichzeitig den Schalter darunter umlegen.
		if (ColourPopup.isOpen()) {
			if (ColourPopup.click(mx, my)) {
				return true;
			}
			ColourPopup.close();
			return true;
		}
		if (super.mouseClicked(click, doubled)) {
			return true;
		}

		// Suchfeld zuerst - es liegt rechts und ueberschneidet nichts.
		if (selectedId == null && this.search.mouseClicked(mx, my)) {
			return true;
		}
		this.search.setFocused(false);

		// Kopfzeile der Mod-Seite: Zuruecksetzen und Suchfeld.
		if (selectedId != null) {
			if (this.pageResetShown && Draw.inside(mx, my,
					this.pageResetX - 5, this.pageResetY - 5,
					Metrics.RESET_ALL + 10, Metrics.RESET_ALL + 10)) {
				HubEntry open = selected();
				SettingsPage page = open == null ? null : settingsPage(open);
				if (page != null) {
					page.resetAll();
				}
				return true;
			}
			if (this.pageSearchShown && this.pageSearch.mouseClicked(mx, my)) {
				this.scroll = 0;
				return true;
			}
			this.pageSearch.setFocused(false);
		}

		int x = sx + Metrics.PAD;
		int w = sw - Metrics.PAD * 2;

		// Update-Zeile: oeffnet Mod Menu, mit dem Hub als Rueckweg.
		if (this.updatesClickable
			&& mx >= x && mx < x + w
			&& my >= this.updatesRowY && my < this.updatesRowY + Metrics.ROW_H) {
			try {
				if (this.client != null) {
					this.client.setScreen(ModMenuUpdates.screen(this));
					return true;
				}
			} catch (Throwable t) {
				LumiLib.LOGGER.warn("[LumiLib] Could not open Mod Menu.", t);
			}
		}

		// All-Mods-Knopf: zurueck zur Uebersicht.
		if (Draw.inside(mx, my, x - 4, allModsRowY(), w + 8, Metrics.ROW_H)) {
			selectedId = null;
			scroll = 0;
			return true;
		}

		int y = modRowsTop();
		int limit = sidebarListBottom();
		for (HubEntry e : visible) {
			if (y + Metrics.ROW_H > limit) {
				break;
			}
			// Stecknadel ZUERST: Sie liegt innerhalb der Zeile, also wuerde
			// die Zeilenpruefung den Klick sonst wegschnappen und die Mod
			// oeffnen statt sie anzupinnen.
			if (Draw.inside(mx, my, pinX(x, w) - 3, y,
					Metrics.PIN + 6, Metrics.ROW_H)) {
				togglePin(e.id());
				return true;
			}
			if (Draw.inside(mx, my, x - 4, y, w + 8, Metrics.ROW_H)) {
				selectedId = e.id();
				// Oben anfangen. Ohne das uebernimmt die neue Seite
				// den Rollstand der Uebersicht und startet mittendrin.
				scroll = 0;
				return true;
			}
			y += Metrics.ROW_H;
		}

		// Einstellungen auf der Mod-Seite.
		//
		// Nur innerhalb des Rollfensters: Was darueber oder darunter liegt,
		// ist weggeschnitten und darf auch nicht anklickbar sein - sonst
		// trifft man einen Schalter, den man gar nicht sieht.
		if (pageListShown && my >= listTop && my < listBottom) {
			HubEntry open = selected();
			SettingsPage page = open == null ? null : settingsPage(open);
			SettingsList.Hit h = page == null ? null
				: SettingsList.hit(page, pageListX, pageListY, pageListW, mx, my,
					pageQuery());
			// !h.reset(): Der Zuruecksetzen-Knopf liegt in derselben Zeile.
			// Ohne die Abfrage wuerde ein Klick darauf den Farbwaehler
			// aufmachen, statt die Farbe zurueckzusetzen.
			//
			// h.right(): Der Waehler haengt an der rechten Kante des
			// Farbfelds, und die ist um den Knopf nach links gerueckt.
			if (h != null && !h.reset() && h.row() instanceof SettingsPage.Colour c) {
				ColourPopup.openAt(c, h.right(),
					h.y() + h.h(), h.y(), this.width, this.height);
				return true;
			}
			if (SettingsList.activate(page, h, mx)) {
				// Regler merken, damit das Ziehen weiterlaeuft.
				this.dragging = SettingsList.draggable(h) ? h : null;
				// Kein Neuaufbau noetig: render() holt die Liste ohnehin in
				// jedem Bild neu. Ein Schalter wie show_missing wirkt damit
				// sofort im naechsten Bild.
				return true;
			}
		}

		// Knopf auf der Mod-Seite.
		if (settingsBtnShown
			&& Draw.inside(mx, my, settingsBtnX, settingsBtnY, settingsBtnW, settingsBtnH)) {
			HubEntry open = selected();
			if (open != null) {
				if (buttonAction == ButtonAction.MODRINTH) {
					openLink(open.modrinth());
				} else {
					open(open);
				}
			}
			return true;
		}

		// Karte in der Uebersicht: oeffnet die Seite der Mod, nicht mehr
		// direkt deren eigenen Bildschirm. Der Weg dorthin fuehrt jetzt
		// ueber LumiLib.
		if (selectedId == null && my >= listTop && my < listBottom) {
			for (int i = 0; i < visible.size(); i++) {
				int bx = cx + (i % cols) * (colW + Metrics.CARD_GAP);
				int by = listTop + (i / cols) * (Metrics.CARD_H + Metrics.CARD_GAP) - scroll;
				if (Draw.inside(mx, my, bx, by, colW, Metrics.CARD_H)) {
					selectedId = visible.get(i).id();
						scroll = 0;
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseX >= cx) {
			int maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
			scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 18)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	/**
	 * Oeffnet eine Adresse - ueber Minecrafts Rueckfrage.
	 *
	 * ConfirmLinkScreen fragt erst, bevor ein Browser aufgeht, und zeigt
	 * dabei die Adresse an. Einen Browser ungefragt aufzumachen waere
	 * uebergriffig, egal wie harmlos der Link ist.
	 */
	private void openLink(String url) {
		if (this.client == null || url == null || url.isEmpty()) {
			return;
		}
		ConfirmLinkScreen.open(this, url, false);
	}

	/**
	 * Oeffnet den EIGENEN Screen einer Mod.
	 *
	 * Geprueft wird screenFactory, nicht hasSettings: Letzteres ist auch
	 * dann wahr, wenn die Mod nur eine Inline-Seite mitbringt - und dann
	 * waere der Aufruf unten eine NullPointerException.
	 *
	 * try/catch darum, weil so ein Screen fast immer ein YACL-Screen ist.
	 * YACL ist seit dieser Fassung keine harte Abhaengigkeit mehr; fehlt
	 * es, fliegt hier ein NoClassDefFoundError. Der darf den Hub nicht
	 * mitreissen - dann bleibt man eben auf der Uebersicht stehen.
	 */
	private void open(HubEntry entry) {
		LumiMod mod = entry.mod();
		if (this.client == null || mod == null || mod.screenFactory() == null) {
			return;
		}
		try {
			Screen next = mod.screenFactory().apply(this);
			if (next != null) {
				this.client.setScreen(next);
			}
		} catch (Throwable t) {
			LumiLib.LOGGER.warn("[LumiLib] Could not open the settings screen of "
				+ mod.id() + ".", t);
		}
	}

	private SkinTextures sessionSkin() {
		if (this.client != null && this.client.player != null) {
			return this.client.player.getSkin();
		}
		UUID uuid = LumiLib.sessionUuid();
		return uuid != null ? DefaultSkinHelper.getSkinTextures(uuid) : DefaultSkinHelper.getSteve();
	}
}
