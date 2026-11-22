package com.lumitier.lumilib.client;

import com.lumitier.lumilib.LumiLib;
import com.lumitier.lumilib.api.SettingsPage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Zeichnet eine SettingsPage und nimmt Klicks darauf entgegen.
 *
 * WARUM EINE EIGENE KLASSE:
 * LumiHubScreen ist bei tausend Zeilen angekommen. Die Einstellungen sind
 * ein in sich geschlossenes Stueck - eigene Masse, eigene Trefferpruefung,
 * eigene Bauformen. Sie dort hineinzuschreiben haette die Datei um die
 * Haelfte wachsen lassen, ohne dass etwas davon mit dem Rest zu tun hat.
 *
 * DER WICHTIGSTE PUNKT - EINE EINZIGE DURCHLAUFSTELLE:
 * Zeichnen, Messen und Klicken laufen durch dieselbe Methode walk(). Alle
 * drei bekommen dieselben Rechtecke, weil sie aus derselben Rechnung
 * stammen. Jede frueher verschobene Klickflaeche im Hub kam daher, dass
 * zwei Stellen dasselbe ausgerechnet haben und eine davon spaeter
 * geaendert wurde.
 *
 * AUFBAU EINES ABSCHNITTS, von oben nach unten:
 *
 *     Luft                      SET_SECTION_GAP (beim ersten nicht)
 *     Ueberschrift              HEAD_H
 *     Luft                      SET_HEAD_GAP
 *     Gruppe                    Summe der Zeilenhoehen, EINE Karte
 *         Zeile
 *         Trennstrich
 *         Zeile
 */
final class SettingsList {

	private SettingsList() {}

	/** Beim Wechsel der Mod-Seite aufrufen. */
	static void reset() {
		ColourPopup.close();
		openIndex = -1;
		openList = null;
		openPage = null;
	}

	// ------------------------------------------------------------------
	// Unterseiten
	// ------------------------------------------------------------------

	/**
	 * Der aufgeschlagene Eintrag einer Liste, oder null.
	 *
	 * WARUM STATISCH UND NICHT AM FENSTER: Genauso macht es ColourPopup
	 * schon. SettingsList hat ueberhaupt keinen Zustand am Objekt - es
	 * gibt kein Objekt, die Klasse besteht nur aus statischen Methoden.
	 * Ein Feld am Hub waere konsequenter, haette aber bedeutet, es durch
	 * jede der acht Methoden durchzureichen.
	 *
	 * ZURUECKGESETZT WIRD BEIM SEITENWECHSEL (siehe reset oben) - sonst
	 * stuende beim Oeffnen der naechsten Mod noch die Unterseite der
	 * vorigen offen.
	 */
	private static SettingsPage.ListRow openList;

	/**
	 * Die NUMMER des aufgeschlagenen Eintrags, nicht der Eintrag selbst.
	 *
	 * DAS IST WICHTIG: Die Eintraege kommen aus einem Supplier und werden
	 * bei jedem Bild neu gebaut - mitsamt neuer Lambdas in ihren Zeilen.
	 * Zwei Aufrufe liefern also nie gleiche Objekte, auch wenn inhaltlich
	 * dasselbe darin steht. Haette ich den Eintrag selbst gemerkt, waere
	 * jeder Vergleich damit fehlgeschlagen und die Unterseite im naechsten
	 * Bild wieder zugeklappt.
	 *
	 * Ueber die Nummer wird der Eintrag stattdessen jedes Mal frisch
	 * geholt. Das ist nebenbei auch richtiger: Aendert die Mod einen Wert
	 * von aussen, steht er sofort da.
	 */
	private static int openIndex = -1;

	/**
	 * Die aufgeschlagene Unterseite, einmal gebaut.
	 *
	 * NICHT bei jedem Bild neu: Eine Seite zu bauen legt Standardwerte in
	 * einer Tabelle ab, und ein neues Objekt je Bild haette bedeutet,
	 * dass jeder Klick auf einen anderen Zeilenobjekt trifft als der, den
	 * das vorige Bild gezeichnet hat.
	 */
	private static SettingsPage openPage;

	/**
	 * Die Seite, die gerade gilt.
	 *
	 * In einer Unterseite muessen Zuruecksetzen, Standardwerte und die
	 * Breite der Bedienelemente von DEREN Seitenobjekt kommen, nicht von
	 * der Mod-Seite darunter - sonst kennt die Abfrage die Zeilen nicht,
	 * die gerade zu sehen sind.
	 */
	private static SettingsPage effective(SettingsPage page) {
		return openPage != null ? openPage : page;
	}

	/**
	 * Die Seite, die gerade zu sehen ist: die offene Unterseite, sonst die
	 * Hauptseite. Fuer die Vorschau - eine Unterseite kann eine andere
	 * Figur zeigen als die Hauptseite (z. B. einen anderen Spieler).
	 */
	static SettingsPage shown(SettingsPage page) {
		return effective(page);
	}

	/** Ist gerade eine Unterseite aufgeschlagen? */
	/** Welche Unterseite offen ist, -1 fuer keine. Der Hub erkennt daran einen Seitenwechsel. */
	static int subPage() {
		return openIndex;
	}

	static boolean inSubPage() {
		return openIndex >= 0;
	}

	/** Eine Ebene zurueck. Gibt false, wenn es nichts zuzuklappen gab. */
	static boolean back() {
		if (openIndex < 0) {
			return false;
		}
		openIndex = -1;
		openList = null;
		openPage = null;
		return true;
	}

	/**
	 * Die Abschnitte, die gerade zu sehen sind.
	 *
	 * DER KERN DER UNTERSEITEN, und der Grund, warum sie fast nichts
	 * gekostet haben: Eine Unterseite ist kein zweiter Zeichenweg,
	 * sondern ein zusammengebauter Abschnitt mit gewoehnlichen Zeilen.
	 * Zeichnen, Messen und Treffer laufen danach durch genau denselben
	 * Code wie die Hauptseite.
	 *
	 * Die Zurueck-Zeile ist eine gewoehnliche Action. Auch dafuer gibt es
	 * also keinen Sonderfall.
	 */
	private static java.util.List<SettingsPage.Section> sections(SettingsPage page) {
		if (openIndex < 0 || openList == null) {
			return page.sections();
		}
		java.util.List<SettingsPage.ListRow.Entry> entries = openList.entries().get();
		// Wurde die Liste von aussen kuerzer, zeigt die Unterseite Regler,
		// die auf nichts mehr wirken. Dann zurueck zur Hauptseite.
		if (openIndex >= entries.size()) {
			back();
			return page.sections();
		}
		SettingsPage.ListRow.Entry entry = entries.get(openIndex);
		if (entry.page() == null) {
			// Kann nur passieren, wenn sich die Liste unter der offenen
			// Unterseite geaendert hat. Zurueck statt Absturz.
			back();
			return page.sections();
		}
		if (openPage == null) {
			openPage = entry.page().get();
		}
		// Die Zurueck-Zeile steht in einem eigenen Abschnitt davor. Sie
		// gehoert bewusst NICHT zur Unterseite der Mod - sonst muesste
		// jede Mod sie selbst einbauen und koennte sie vergessen.
		java.util.List<SettingsPage.Section> out = new java.util.ArrayList<>();
		out.add(new SettingsPage.Section(entry.label(), java.util.List.of(
			new SettingsPage.Action(
				net.minecraft.text.Text.translatable("lumilib.list.back"), null,
				SettingsList::back))));
		out.addAll(openPage.sections());
		return out;
	}

	/**
	 * Eine getroffene Zeile samt ihrem Rechteck.
	 *
	 * WOZU DAS RECHTECK MITKOMMT: Beim Ziehen eines Reglers bewegt sich
	 * nur die Maus, nicht die Zeile. Haette ich die Lage beim Ziehen neu
	 * gesucht, wuerde der Regler springen, sobald der Mauszeiger die Zeile
	 * verlaesst - und genau das tut er beim Ziehen staendig.
	 */
	record Hit(SettingsPage.Row row, int x, int y, int w, int h,
	           int right, boolean reset, int part) {

		/**
		 * part sagt bei einer ListRow, WELCHE ihrer Unterzeilen getroffen
		 * wurde: -1 die Kopfzeile mit dem Plus, sonst die Nummer des
		 * Eintrags. Bei jeder anderen Bauform ist es -1.
		 *
		 * Noetig, weil eine ListRow mehrere Zeilen hoch ist, aber als EINE
		 * Zeile durch walk laeuft - und activate() nur die x-Lage der Maus
		 * bekommt, nicht die y-Lage.
		 */
		Hit(SettingsPage.Row row, int x, int y, int w, int h,
		    int right, boolean reset) {
			this(row, x, y, w, h, right, reset, -1);
		}
	}

	/**
	 * Rechte Kante der Bedienelemente.
	 *
	 * EINE Stelle fuer alle: Schalter, Regler, Farbfeld und der Anker des
	 * Farbwaehlers richten sich danach. Wandert der Zuruecksetzen-Knopf
	 * spaeter, wandert alles mit, statt an vier Stellen einzeln.
	 */
	static int controlRight(SettingsPage page, int x, int w) {
		return x + w - Metrics.SET_INSET
			- (effective(page).canResetAny() ? Metrics.RESET_SLOT : 0);
	}

	/** Linke Kante des Zuruecksetzen-Knopfs. */
	private static int resetX(int x, int w) {
		return x + w - Metrics.SET_INSET - Metrics.RESET;
	}

	/** Was walk() fuer jede Zeile meldet. */
	private interface RowVisitor {
		void row(SettingsPage.Row row, int x, int y, int w, int h);
	}

	/**
	 * Was walk() fuer jede Gruppe meldet, VOR deren Zeilen.
	 *
	 * rows ist die GEFILTERTE Liste, nicht section.rows(). Wer die
	 * Trennstriche zeichnet, muss dieselben Zeilen sehen wie der
	 * Zeilendurchlauf - sonst zieht er Striche fuer Zeilen, die die Suche
	 * gerade ausgeblendet hat.
	 */
	private interface GroupVisitor {
		void group(SettingsPage.Section section, java.util.List<SettingsPage.Row> rows,
		           int headTop, int x, int y, int w, int h);
	}

	// ------------------------------------------------------------------
	// Masse
	// ------------------------------------------------------------------

	/**
	 * Hoehe einer Zeile.
	 *
	 * Mit Untertext: Innenabstand, Beschriftung, Abstand, Untertext samt
	 * Unterlaenge, Innenabstand. Ohne Untertext nur die Beschriftung
	 * zwischen den Innenabstaenden. Im Vorbild gemessen: 54 und 29.
	 */
	// ------------------------------------------------------------------
	// HINWEISZEILEN
	//
	// Eine Meldung ist kein Bedienelement, sondern etwas, das man liest.
	// Sie ist deshalb eine ZEILE WIE JEDE ANDERE, nur getoent: gleiche
	// Hoehe, gleicher Einzug, gleiche Schreiblinie. Nur der Text ist
	// umgebrochen statt gekuerzt, und die Zeile waechst dabei mit.
	//
	// WAS HIER VORHER FALSCH WAR, zweimal:
	//
	// 1. Ein eingerueckter Kasten mit eigenem Rand. Der sah fuer sich
	//    genommen gut aus, aber sein Text begann weiter rechts als der
	//    jeder anderen Zeile - und in einer Liste faellt genau das auf,
	//    nicht die schoene Form.
	//
	// 2. Ein Symbol links davor rueckt den Text ein - der Einzug betraegt
	//    12 Pixel, ein lesbares Symbol ebenfalls, daneben passt es nicht.
	//    Das Symbol ist trotzdem da: Es steht AUF dem Einzug, der Text
	//    dahinter. Die Zeile verliert damit die Flucht mit den anderen,
	//    gewinnt aber das Zeichen, an dem man eine Meldung sofort als
	//    solche erkennt. Bewusst so entschieden, nicht uebersehen.
	// ------------------------------------------------------------------

	/** Abstand zweier umgebrochener Zeilen, Oberkante zu Oberkante. */
	/**
	 * Hoehe einer Eintragszeile in einer Liste.
	 *
	 * 30 Pixel, und zwar fuer ALLE Eintraege gleich - auch die
	 * einzeiligen. Zwei Hoehen je nach Inhalt waeren ein zweites Mass an
	 * jeder der sieben Stellen, die hier rechnen (Hoehe der Zeile, Lage
	 * des naechsten Eintrags, Treffer, Ueberfahren, Muelleimer), und die
	 * Trefferpruefung muesste die Eintraege durchzaehlen statt zu teilen.
	 * Ein Mass fuer alle: 22 Pixel Fach plus vier Luft oben und unten,
	 * und zwei Textzeilen passen genauso hinein.
	 */
	private static final int LIST_ENTRY_H = 30;

	/** Luft unter dem letzten Eintrag einer Liste. */
	private static final int LIST_BOTTOM_PAD = 6;

	/** Kantenlaenge eines Item-Bildes. Von Minecraft fest vorgegeben. */
	private static final int ITEM = 16;

	/**
	 * Kantenlaenge des Fachs hinter dem Item.
	 *
	 * WARUM UEBERHAUPT EIN FACH: Minecrafts Item-Bilder haben keinen
	 * eigenen Rand und keine einheitliche Silhouette - eine Elytra ist
	 * flach und breit, ein Schwert duenn und schraeg. Nebeneinander in
	 * einer Liste fransen sie aus und die Textkante wirkt zerfasert. Ein
	 * Fach dahinter gibt jedem Bild dieselbe Flaeche, so wie im Inventar.
	 */
	private static final int SLOT = 22;

	private static final int NOTE_LEAD = 13;
	/** Kantenlaenge des Zeichens - gerade, damit es auf die Textmitte passt. */
	private static final int NOTE_ICON = 12;
	private static final int NOTE_GAP = 7;

	private static final Identifier PLUS = Identifier.of(LumiLib.MOD_ID, "plus");
	private static final Identifier TRASH = Identifier.of(LumiLib.MOD_ID, "trash");

	private static final Identifier NOTE_SIGN =
		Identifier.of(com.lumitier.lumilib.LumiLib.MOD_ID, "info");

	private static java.util.List<String> noteLines(SettingsPage.Note n, int w) {
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		java.util.List<String> out = new java.util.ArrayList<>(
			Ui.wrap(tr, n.label().getString(),
				w - Metrics.SET_INSET * 2 - NOTE_ICON - NOTE_GAP));
		if (out.isEmpty()) {
			out.add("");
		}
		return out;
	}

	/**
	 * Eine Meldung: getoente Zeile, Text in der Flucht aller anderen.
	 *
	 * ZUR TOENUNG: Die Flaeche ist die Textfarbe mit niedriger Deckkraft,
	 * kein eigener Farbwert. So passt sie automatisch, wenn das Thema
	 * wechselt - und kann gar nicht erst gegen die Palette laufen.
	 *
	 * ZUR EINZEILIGEN FASSUNG: Sie geht ueber denselben Aufruf wie eine
	 * gewoehnliche einzeilige Zeile. Nicht aus Bequemlichkeit - nur so
	 * sitzt sie GARANTIERT auf derselben Schreiblinie wie die Zeile
	 * darunter, auch wenn sich an der Mittelung je etwas aendert.
	 */
	private static void drawNote(DrawContext ctx, TextRenderer tr,
	                             SettingsPage.Note n, int x, int y, int w, int h) {
		int colour = n.warning() ? Theme.WARN : Theme.TEXT_MUTED;
		Draw.roundedRect(ctx, x, y, w, h, 6, (0x24 << 24) | (colour & 0xFFFFFF));

		java.util.List<String> lines = noteLines(n, w);

		// Die Oberkante wird GERECHNET und nicht ueber drawCenteredIn
		// bestimmt. Grund: drawCenteredIn mittet nach der Tinte GENAU
		// DIESER Zeichenkette - eine Meldung mit "g" und "y" saesse damit
		// anders als eine ohne, und gegen die Zeile darunter faellt das
		// auf. Mit fester Versalhoehe und Unterlaenge sitzt jede Meldung
		// auf derselben Schreiblinie wie jede gewoehnliche Zeile.
		int blockH = Ui.BODY_H + Ui.BODY_D + (lines.size() - 1) * NOTE_LEAD;
		int top = y + (h - blockH) / 2;

		// Zeichen auf die Mitte des Versalblocks der ERSTEN Zeile. Nicht
		// auf die Mitte der Zeile: Bei drei Zeilen Text stuende es sonst
		// neben der zweiten und sahe aus, als gehoere es nicht zum Anfang.
		float iconYf = Ui.capBlockCentreBelow(top, Ui.TOP_BODY, false)
			- NOTE_ICON / 2.0F;
		int iconY = (int) Math.floor(iconYf);
		var pose = ctx.getMatrices();
		pose.pushMatrix();
		pose.translate(0.0F, iconYf - iconY);
		Surface.icon(ctx, NOTE_SIGN, x + Metrics.SET_INSET, iconY, NOTE_ICON, colour);
		pose.popMatrix();

		int tx = x + Metrics.SET_INSET + NOTE_ICON + NOTE_GAP;
		for (int i = 0; i < lines.size(); i++) {
			Ui.drawTop(ctx, tr, Ui.text(lines.get(i)), tx,
				top + i * NOTE_LEAD, Ui.TOP_BODY, colour);
		}
	}

	private static int rowHeight(SettingsPage.Row row, int w) {
		if (row instanceof SettingsPage.ListRow l) {
			// Kopfzeile plus je Eintrag eine. Eine leere Liste ist nur
			// die Kopfzeile - und das ist richtig so: Wer noch nichts
			// hinzugefuegt hat, braucht keinen leeren Kasten, sondern
			// den Plus-Knopf.
			//
			// Unter dem letzten Eintrag etwas Luft: Ohne sie klebte die
			// Unterzeile eines zweizeiligen Eintrags an der Unterkante
			// des Kastens.
			int n = l.entries().get().size();
			return Metrics.SET_ROW_H + n * LIST_ENTRY_H + (n > 0 ? LIST_BOTTOM_PAD : 0);
		}
		if (row instanceof SettingsPage.Note n) {
			// Eine Zeile ist genau so hoch wie jede andere. Erst ab der
			// zweiten waechst sie - und zwar um genau eine Zeile, nicht
			// um Zeile plus Luft.
			return Metrics.SET_ROW_H + (noteLines(n, w).size() - 1) * NOTE_LEAD;
		}
		if (row.description() == null) {
			return Metrics.SET_ROW_H;
		}
		return Metrics.SET_PAD * 2 + descBlockH();
	}

	/**
	 * Hoehe des zweizeiligen Blocks: von der Oberkante der Beschriftung
	 * bis zur Unterlaenge des Untertextes.
	 *
	 * HIER STAND EIN FEHLER, und er war im Bild zu sehen: Die Rechnung
	 * zaehlte BODY_H zusaetzlich zu SET_DESC_GAP. SET_DESC_GAP ist aber
	 * der Abstand von Oberkante zu Oberkante, enthaelt die Hoehe der
	 * Beschriftung also schon. Die neun Pixel landeten damit UNTEN:
	 * 10 Pixel Luft ueber dem Text, 19 darunter.
	 */
	private static int descBlockH() {
		return Metrics.SET_DESC_GAP + Ui.SMALL_H + Ui.SMALL_D;
	}

	/** Gesamthoehe - der Hub braucht sie, um das Rollen zu begrenzen. */
	static int height(SettingsPage page, int w, String query) {
		int[] bottom = { 0 };
		walk(page, 0, 0, w, query, null, (row, rx, ry, rw, rh) -> bottom[0] = ry + rh);
		return bottom[0];
	}

	// ------------------------------------------------------------------
	// Suche
	// ------------------------------------------------------------------

	/**
	 * Die Zeilen eines Abschnitts, die zur Suche passen.
	 *
	 * Gesucht wird in Beschriftung UND Beschreibung. Wer "farbe" tippt,
	 * meint auch eine Zeile, die "Farbe des Umrisses" nur in der
	 * Erklaerung stehen hat.
	 *
	 * Passt der Abschnittsname selbst, bleiben ALLE seine Zeilen stehen -
	 * "pulse" soll die ganze Gruppe zeigen, nicht nur die eine Zeile, die
	 * zufaellig genauso heisst.
	 */
	private static java.util.List<SettingsPage.Row> visible(
			SettingsPage.Section section, String query) {
		if (query == null || query.isBlank()) {
			return section.rows();
		}
		String q = query.trim().toLowerCase(java.util.Locale.ROOT);
		if (section.title() != null
				&& section.title().getString().toLowerCase(java.util.Locale.ROOT).contains(q)) {
			return section.rows();
		}
		java.util.List<SettingsPage.Row> out = new java.util.ArrayList<>();
		for (SettingsPage.Row row : section.rows()) {
			if (matches(row, q)) {
				out.add(row);
			}
		}
		return out;
	}

	private static boolean matches(SettingsPage.Row row, String q) {
		if (row.label().getString().toLowerCase(java.util.Locale.ROOT).contains(q)) {
			return true;
		}
		return row.description() != null
			&& row.description().getString().toLowerCase(java.util.Locale.ROOT).contains(q);
	}

	/** Findet die Suche ueberhaupt etwas? */
	static boolean empty(SettingsPage page, String query) {
		for (SettingsPage.Section section : page.sections()) {
			if (!visible(section, query).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------
	// Durchlauf
	// ------------------------------------------------------------------

	private static void walk(SettingsPage page, int x, int y, int w, String query,
	                         GroupVisitor onGroup, RowVisitor onRow) {
		boolean first = true;
		// In einer Unterseite wird NICHT gesucht: Die Suche gehoert zur
		// Mod-Seite, und ein Filter ueber drei Zeilen hilft niemandem.
		String q = openIndex < 0 ? query : null;
		for (SettingsPage.Section section : sections(page)) {
			java.util.List<SettingsPage.Row> rows = visible(section, q);
			if (rows.isEmpty()) {
				continue;
			}
			if (!first) {
				y += Metrics.SET_SECTION_GAP;
			}
			first = false;

			// Ein Abschnitt ohne Namen bekommt keine Ueberschrift und auch
			// nicht deren Luft - sonst klafft oben ein Loch.
			int headTop = -1;
			if (section.title() != null && !section.title().getString().isEmpty()) {
				headTop = y;
				y += Ui.HEAD_H + Metrics.SET_HEAD_GAP;
			}

			int groupH = 0;
			for (SettingsPage.Row row : rows) {
				groupH += rowHeight(row, w);
			}
			if (onGroup != null) {
				onGroup.group(section, rows, headTop, x, y, w, groupH);
			}

			int ry = y;
			for (SettingsPage.Row row : rows) {
				int rh = rowHeight(row, w);
				onRow.row(row, x, ry, w, rh);
				ry += rh;
			}
			y += groupH;
		}
	}

	// ------------------------------------------------------------------
	// Zeichnen
	// ------------------------------------------------------------------

	static void render(DrawContext ctx, TextRenderer tr, SettingsPage page,
	                   int x, int y, int w, int mouseX, int mouseY, String query) {
		walk(page, x, y, w, query,
			(section, rows, headTop, gx, gy, gw, gh) -> {
				if (headTop >= 0) {
					Ui.drawTop(ctx, tr, Ui.headOf(section.title()), gx, headTop,
						Ui.TOP_HEAD, Theme.TEXT);
				}
				if (!Surface.glassCard(ctx, gx, gy, gw, gh, Theme.CARD)) {
					Surface.card(ctx, gx, gy, gw, gh, Theme.CARD);
					Surface.rimCard(ctx, gx, gy, gw, gh, Theme.RIM_CARD);
				}
				// Trennstriche gleich hier, solange die Lage der Gruppe
				// vorliegt - und VOR den Zeilen, damit Text und Schalter
				// darueber liegen statt darunter.
				int dy = gy;
				for (int i = 0; i < rows.size() - 1; i++) {
					dy += rowHeight(rows.get(i), gw);
					// Kein Strich neben einem Hinweis: Der traegt schon
					// eine eigene getoente Flaeche, ein Strich direkt an
					// deren Kante sieht aus wie ein Fehler im Abstand.
					if (rows.get(i) instanceof SettingsPage.Note
						|| rows.get(i + 1) instanceof SettingsPage.Note) {
						continue;
					}
					Draw.hLine(ctx, gx, dy, gw, Theme.DIVIDER);
				}
			},
			(row, rx, ry, rw, rh) ->
				drawRow(ctx, tr, page, row, rx, ry, rw, rh, mouseX, mouseY));
	}

	private static void drawRow(DrawContext ctx, TextRenderer tr, SettingsPage page,
	                            SettingsPage.Row row,
	                            int x, int y, int w, int h, int mouseX, int mouseY) {
		if (row instanceof SettingsPage.Note n) {
			drawNote(ctx, tr, n, x, y, w, h);
			return;
		}
		if (row instanceof SettingsPage.ListRow l) {
			drawList(ctx, tr, page, l, x, y, w, mouseX, mouseY);
			return;
		}

		boolean hover = Draw.inside(mouseX, mouseY, x, y, w, h);
		boolean slot = effective(page).canResetAny();
		int right = controlRight(page, x, w);

		// Hover nur bei Zeilen, die als GANZES ein Knopf sind. Bei einem
		// Schalter ist der Schalter das Ziel - eine aufleuchtende Zeile
		// wuerde dort etwas versprechen, was sie nicht einloest.
		if (hover && row instanceof SettingsPage.Action) {
			Surface.row(ctx, x + 1, y + 1, w - 2, h - 2, Theme.ROW_HOVER);
		}

		int tx = x + Metrics.SET_INSET;
		int room = w - Metrics.SET_INSET * 2 - controlWidth(row, slot);

		int labelColour = Theme.TEXT;

		if (row.description() == null) {
			// Eine Zeile, also mittig - es gibt nichts, womit sie fluchten muss.
			Ui.drawCenteredIn(ctx, tr,
				Ui.bold(Ui.fitBold(tr, row.label().getString(), room)),
				tx, y, h, Ui.BODY_H, Ui.BODY_D, Ui.TOP_BODY, labelColour);
		} else {
			// Den ganzen Block mittig setzen, nicht am oberen Innenabstand
			// festnageln. Dann bleibt die Zeile auch dann symmetrisch,
			// wenn sich eine der Schriftgroessen aendert - und das
			// Bedienelement rechts, das in der Zeilenmitte sitzt, fluchtet
			// mit der Mitte des Blocks statt tiefer zu haengen.
			int top = y + (h - descBlockH()) / 2;
			Ui.drawTop(ctx, tr,
				Ui.bold(Ui.fitBold(tr, row.label().getString(), room)),
				tx, top, Ui.TOP_BODY, labelColour);
			Ui.drawTop(ctx, tr,
				Ui.small(Ui.fitSmall(tr, row.description().getString(), room)),
				tx, top + Metrics.SET_DESC_GAP, Ui.TOP_SMALL, Theme.TEXT_FAINT);
		}

		if (row instanceof SettingsPage.Toggle t) {
			drawToggle(ctx, right - Metrics.TOGGLE_W,
				y + (h - Metrics.TOGGLE_H) / 2, t, t.getter().getAsBoolean());
		} else if (row instanceof SettingsPage.Slider sl) {
			drawSlider(ctx, tr, sl, y, h, right);
		} else if (row instanceof SettingsPage.Colour c) {
			drawSwatch(ctx, tr, c, y, h, right);
		} else if (row instanceof SettingsPage.Choice ch) {
			drawChoice(ctx, tr, ch, y, h, right, hover);
		}

		if (effective(page).canReset(row)) {
			int rx = resetX(x, w);
			drawReset(ctx, rx, y, h, effective(page).isDefault(row),
				Draw.inside(mouseX, mouseY, rx - 4, y, Metrics.RESET + 8, h));
		}
	}

	/**
	 * Der Zuruecksetzen-Knopf.
	 *
	 * ABGEBLENDET STATT AUSGEBLENDET, wenn der Wert schon auf Standard
	 * steht: Ein Zeichen, das beim Verstellen auftaucht und beim
	 * Zuruecksetzen verschwindet, laesst die ganze Zeile zucken - und man
	 * kann nicht mehr auf einen Blick sehen, welche Zeilen ueberhaupt
	 * einen Standardwert haben.
	 */
	/**
	 * Eine Liste: Kopfzeile mit Plus, darunter je Eintrag eine Zeile.
	 *
	 * AUFBAU EINER EINTRAGSZEILE, von links nach rechts:
	 *
	 *     SET_INSET  Bild des Items (16)  ROW_ICON_GAP  Name  ...  Muelleimer
	 *
	 * Das Bild wird mit drawItem gezeichnet, also von Minecraft selbst -
	 * damit sieht ein Item hier genauso aus wie im Inventar, mit allem,
	 * was dazugehoert: Modelle fuer Bloecke, Faerbung fuer Leder,
	 * Verzauberungsschimmer. Nachgebaut waere nichts davon da.
	 */
	private static void drawList(DrawContext ctx, TextRenderer tr, SettingsPage page,
	                             SettingsPage.ListRow l,
	                             int x, int y, int w, int mouseX, int mouseY) {
		int right = controlRight(page, x, w);

		// --- Kopfzeile ---
		Ui.drawCenteredIn(ctx, tr, Ui.text(l.label().getString()),
			x + Metrics.SET_INSET, y, Metrics.SET_ROW_H,
			Ui.BODY_H, Ui.BODY_D, Ui.TOP_BODY, Theme.TEXT);

		if (l.onAdd() != null) {
			boolean can = l.canAdd();
			int bx = right - Metrics.ROW_ICON;
			int by = y + (Metrics.SET_ROW_H - Metrics.ROW_ICON) / 2;
			boolean over = can && Draw.inside(mouseX, mouseY,
				bx - 4, by - 4, Metrics.ROW_ICON + 8, Metrics.ROW_ICON + 8);
			Surface.icon(ctx, PLUS, bx, by, Metrics.ROW_ICON,
				can ? (over ? Theme.ACCENT : Theme.TEXT) : Theme.TEXT_FAINT);
		}

		// --- Eintraege ---
		java.util.List<SettingsPage.ListRow.Entry> entries = l.entries().get();
		int ey = y + Metrics.SET_ROW_H;
		for (int i = 0; i < entries.size(); i++) {
			SettingsPage.ListRow.Entry e = entries.get(i);
			boolean hover = Draw.inside(mouseX, mouseY, x, ey, w, LIST_ENTRY_H);
			if (hover) {
				Draw.roundedRect(ctx, x + Metrics.SET_PAD, ey,
					w - Metrics.SET_PAD * 2, LIST_ENTRY_H, 6, Theme.ROW_HOVER);
			}

			int ix = x + Metrics.SET_INSET;
			if (e.icon() != null) {
				// Erst das Fach, dann das Bild mittig hinein.
				int sy = ey + (LIST_ENTRY_H - SLOT) / 2;
				Draw.roundedRect(ctx, ix, sy, SLOT, SLOT, 5, Theme.FIELD);
				ctx.drawItem(e.icon(), ix + (SLOT - ITEM) / 2, sy + (SLOT - ITEM) / 2);
			}
			int tx = ix + (e.icon() == null ? 0 : SLOT + Metrics.ROW_ICON_GAP);
			int tw = right - tx - Metrics.ROW_ICON - 8;

			boolean twoLine = e.sub() != null && !e.sub().getString().isEmpty();
			String text = Ui.fit(tr, e.label().getString(), tw);

			// Einzeilig sitzt der Name mittig, zweizeilig teilen sich Name
			// und Unterzeile die Hoehe. Dieselbe Rechnung, nur ein anderes
			// Rechteck - kein zweiter Zeichenweg.
			int nameTop = twoLine ? ey + 2 : ey;
			int nameH = twoLine ? LIST_ENTRY_H / 2 : LIST_ENTRY_H;
			Ui.drawCenteredIn(ctx, tr, Ui.text(text), tx, nameTop, nameH,
				Ui.BODY_H, Ui.BODY_D, Ui.TOP_BODY,
				e.muted() ? Theme.TEXT_FAINT : Theme.TEXT);

			if (twoLine) {
				String sub = Ui.fitSmall(tr, e.sub().getString(), tw);
				Ui.drawCenteredIn(ctx, tr, Ui.small(sub),
					tx, ey + LIST_ENTRY_H / 2, LIST_ENTRY_H / 2 - 2,
					Ui.SMALL_H, Ui.SMALL_D, Ui.TOP_SMALL,
					e.muted() ? Theme.TEXT_FAINT : Theme.TEXT_MUTED);
			}

			if (e.muted()) {
				// Durchgestrichen UND blass: Ein Eintrag, der gerade nicht
				// wirkt, soll auf einen Blick vom Rest zu unterscheiden
				// sein. Blass allein reicht nicht - das sieht nach
				// "abgeschaltet" aus, nicht nach "wird verdeckt".
				//
				// NUR DIE ERSTE ZEILE: Der Strich gehoert zum Namen. Durch
				// beide Zeilen gezogen saehe der Eintrag nach geloescht
				// aus, nicht nach verdeckt.
				int line = nameTop + nameH / 2;
				Draw.hLine(ctx, tx, line, Ui.width(tr, text), Theme.TEXT_FAINT);
			}

			if (l.onRemove() != null) {
				int bx = right - Metrics.ROW_ICON;
				int by = ey + (LIST_ENTRY_H - Metrics.ROW_ICON) / 2;
				boolean over = Draw.inside(mouseX, mouseY,
					bx - 4, by - 4, Metrics.ROW_ICON + 8, Metrics.ROW_ICON + 8);
				Surface.icon(ctx, TRASH, bx, by, Metrics.ROW_ICON,
					over ? Theme.WARN : Theme.TEXT_FAINT);
			}
			ey += LIST_ENTRY_H;
		}
	}

	private static void drawReset(DrawContext ctx, int x, int y, int h,
	                              boolean atDefault, boolean hover) {
		int colour = atDefault
			? Theme.TEXT_FAINT
			: (hover ? Theme.TEXT : Theme.TEXT_MUTED);
		Surface.icon(ctx, Identifier.of(com.lumitier.lumilib.LumiLib.MOD_ID, "undo"),
			x, y + (h - Metrics.RESET) / 2, Metrics.RESET, colour);
	}

	/**
	 * Farbfeld und Hexwert.
	 *
	 * ZWEI LAGEN unter der Farbe: ein Karomuster. Ohne das sieht eine
	 * halb durchsichtige Farbe genauso aus wie eine helle deckende -
	 * und gerade die Deckkraft will man hier einstellen koennen.
	 */
	/** Breite des Auswahlfelds rechts. Passt "Game language" in kleiner Schrift. */
	private static final int CHOICE_W = 96;

	/**
	 * Auswahlfeld: Rahmen wie ein Eingabefeld, darin der aktuelle Wert,
	 * links und rechts kleine Pfeile - sie sagen, dass ein Klick weiterblaettert.
	 */
	private static void drawChoice(DrawContext ctx, TextRenderer tr, SettingsPage.Choice c,
	                               int y, int h, int right, boolean hover) {
		int bh = Metrics.SWATCH_H + 4;
		int bx = right - CHOICE_W;
		int by = y + (h - bh) / 2;
		Draw.roundedRect(ctx, bx, by, CHOICE_W, bh, 4, hover ? Theme.ROW_HOVER : Theme.ROW_SELECTED);
		Draw.roundedOutline(ctx, bx, by, CHOICE_W, bh, 4, Theme.FIELD_EDGE);
		String text = Ui.fitSmall(tr, c.options().get(c.index()).getString(), CHOICE_W - 24);
		int tw = Ui.widthSmall(tr, text);
		Ui.drawCenteredIn(ctx, tr, Ui.small(text), bx + (CHOICE_W - tw) / 2, by, bh,
			Ui.SMALL_H, Ui.SMALL_D, Ui.TOP_SMALL, Theme.TEXT);
		// Pfeile aus Linien, 3 Pixel hoch - wie die Pfeile der Kategorien.
		int cy = by + bh / 2;
		for (int i = 0; i < 3; i++) {
			ctx.fill(bx + 6 + i, cy - i, bx + 7 + i, cy + i + 1, Theme.TEXT_MUTED);
			ctx.fill(bx + CHOICE_W - 7 - i, cy - i, bx + CHOICE_W - 6 - i, cy + i + 1, Theme.TEXT_MUTED);
		}
	}

	private static void drawSwatch(DrawContext ctx, TextRenderer tr,
	                               SettingsPage.Colour c, int y, int h, int right) {
		int argb = c.getter().getAsInt();
		int sw = Metrics.SWATCH_W;
		int sh = Metrics.SWATCH_H;
		int sx = right - sw;
		int sy = y + (h - sh) / 2;

		// Karomuster, vier Pixel gross.
		for (int row = 0; row * 4 < sh; row++) {
			for (int col = 0; col * 4 < sw; col++) {
				int cx = sx + col * 4;
				int cy = sy + row * 4;
				ctx.fill(cx, cy, Math.min(cx + 4, sx + sw), Math.min(cy + 4, sy + sh),
					(row + col) % 2 == 0 ? 0xFFFFFFFF : 0xFFBDBDBD);
			}
		}
		Draw.roundedRect(ctx, sx, sy, sw, sh, 3, argb);
		Draw.roundedOutline(ctx, sx, sy, sw, sh, 3, Theme.FIELD_EDGE);

		String hex = String.format("#%08X", argb);
		Ui.drawCenteredIn(ctx, tr, Ui.small(hex),
			sx - 6 - Ui.widthSmall(tr, hex), y, h,
			Ui.SMALL_H, Ui.SMALL_D, Ui.TOP_SMALL, Theme.TEXT_FAINT);
	}

	/**
	 * Platz, den das Bedienelement rechts braucht.
	 *
	 * EINE Stelle fuer alle Bauformen: Der Text links wird genau auf den
	 * Rest gekuerzt. Stuende die Zahl an zwei Stellen, liefe der Text
	 * irgendwann unter den Regler.
	 */
	private static int controlWidth(SettingsPage.Row row, boolean slot) {
		int extra = slot ? Metrics.RESET_SLOT : 0;
		if (row instanceof SettingsPage.Toggle) {
			return Metrics.TOGGLE_W + 12 + extra;
		}
		if (row instanceof SettingsPage.Slider) {
			return Metrics.SLIDER_W + Metrics.VALUE_W + 14 + extra;
		}
		if (row instanceof SettingsPage.Colour) {
			return Metrics.SWATCH_W + Metrics.HEX_W + 14 + extra;
		}
		if (row instanceof SettingsPage.Choice) {
			return CHOICE_W + 12 + extra;
		}
		// Hinweis- und Aktionszeilen haben kein Bedienelement, aber der
		// Knopf steht trotzdem da, wenn sie einen Standardwert haben.
		return extra;
	}

	/** Linke Kante der Reglerbahn. */
	private static int trackX(int right) {
		return right - Metrics.VALUE_W - 6 - Metrics.SLIDER_W;
	}

	/**
	 * Regler: Bahn, gefuellter Teil, Knopf, Zahl.
	 *
	 * Die Bahn ist um den Knopfradius EINGERUECKT, nicht randbuendig.
	 * Sonst haengt der Knopf am Anschlag zur Haelfte ueber der Bahn
	 * hinaus und der Regler sieht kaputt aus.
	 */
	private static void drawSlider(DrawContext ctx, TextRenderer tr,
	                               SettingsPage.Slider s, int y, int h, int right) {
		int r = Metrics.SLIDER_KNOB;
		int tx = trackX(right);
		int ty = y + (h - Metrics.SLIDER_TRACK) / 2;
		int usable = Metrics.SLIDER_W - r * 2;

		Draw.roundedRect(ctx, tx, ty, Metrics.SLIDER_W, Metrics.SLIDER_TRACK,
			Metrics.SLIDER_TRACK / 2, Theme.TOGGLE_OFF);

		int knobX = tx + r + (int) Math.round(usable * s.fraction());
		Draw.roundedRect(ctx, tx, ty, knobX - tx, Metrics.SLIDER_TRACK,
			Metrics.SLIDER_TRACK / 2, Theme.TOGGLE_ON);
		Draw.dot(ctx, knobX, y + h / 2, r, Theme.TOGGLE_KNOB);

		String value = format(s);
		Ui.drawCenteredIn(ctx, tr, Ui.small(value),
			right - Ui.widthSmall(tr, value),
			y, h, Ui.SMALL_H, Ui.SMALL_D, Ui.TOP_SMALL, Theme.TEXT_MUTED);
	}

	/**
	 * Zahl unter dem Regler.
	 *
	 * Ganzzahlen ohne Komma, Kommazahlen mit zwei Stellen und ohne
	 * ueberfluessige Nullen: 1,5 statt 1,50 - sonst steht bei einem
	 * Regler von 0 bis 3 staendig eine Null zu viel.
	 */
	private static String format(SettingsPage.Slider s) {
		double v = s.getter().getAsDouble();
		if (s.integer()) {
			return Integer.toString((int) Math.round(v));
		}
		String t = String.format(java.util.Locale.ROOT, "%.2f", v);
		if (t.endsWith("0")) {
			t = t.substring(0, t.length() - 1);
		}
		if (t.endsWith("0")) {
			t = t.substring(0, t.length() - 2);
		}
		return t;
	}

	/**
	 * Der Schalter.
	 *
	 * OHNE SHADER, mit Absicht: Die Flaechen des Hubs laufen ueber den
	 * eigenen Shader, weil dort Kante, Fase und Innenschatten zusammen
	 * entstehen muessen. Ein Schalter hat davon nichts - er ist eine
	 * Pille in einer Farbe und ein weisser Kreis. Draw zeichnet das ohne
	 * Umweg, ohne Rueckfallebene und in jeder Groesse gleich.
	 */
	private static void drawToggle(DrawContext ctx, int x, int y, Object key, boolean on) {
		int h = Metrics.TOGGLE_H;
		int w = Metrics.TOGGLE_W;

		// Stand des Schalters, 0 = aus, 1 = an. Laeuft dem Wert hinterher,
		// statt zu springen - so sieht man, dass der Klick angekommen ist.
		// Ein Array je Schalter, einmal angelegt; danach kostet es nichts.
		Knob st = KNOBS.computeIfAbsent(key, x0 -> new Knob(on ? 1.0F : 0.0F));
		long now = Anim.now();
		float dt = st.last == 0L ? 16.0F : Math.min(100.0F, now - st.last);
		st.last = now;
		st.value = Anim.approach(st.value, on ? 1.0F : 0.0F, dt, Anim.SLIDE_MS);
		float k = st.value;

		Draw.roundedRect(ctx, x, y, w, h, h / 2, Anim.mix(Theme.TOGGLE_OFF, Theme.TOGGLE_ON, k));

		int r = (h - Metrics.TOGGLE_INSET * 2) / 2;
		int cy = y + h / 2;
		int left = x + Metrics.TOGGLE_INSET + r;
		int span = w - Metrics.TOGGLE_INSET * 2 - r * 2;
		float cx = left + span * k;
		int ix = (int) Math.floor(cx);
		var pose = ctx.getMatrices();
		pose.pushMatrix();
		pose.translate(cx - ix, 0.0F);
		Draw.dot(ctx, ix, cy, r, Theme.TOGGLE_KNOB);
		pose.popMatrix();
	}

	/**
	 * Stand jedes Schalters, nach Zeile.
	 *
	 * IdentityHashMap: Die Zeilen einer Seite sind feste Objekte, die die
	 * Seite zwischenspeichert. Zwei Schalter mit gleichem Namen auf
	 * verschiedenen Seiten bleiben so getrennt.
	 */
	private static final java.util.Map<Object, Knob> KNOBS = new java.util.IdentityHashMap<>();

	private static final class Knob {
		float value;
		/** Zeitpunkt des letzten Bilds, als long - ein float waere dafuer zu ungenau. */
		long last;

		Knob(float value) {
			this.value = value;
		}
	}

	// ------------------------------------------------------------------
	// Klicken
	// ------------------------------------------------------------------

	/**
	 * Welche Zeile liegt unter dem Zeiger? Veraendert nichts.
	 *
	 * Trennung von Suchen und Tun, damit das Ziehen eines Reglers die
	 * einmal gefundene Zeile weiterbenutzen kann.
	 */
	static Hit hit(SettingsPage page, int x, int y, int w, double mx, double my,
	               String query) {
		Hit[] found = { null };
		walk(page, x, y, w, query, null, (row, rx, ry, rw, rh) -> {
			if (found[0] != null || !Draw.inside(mx, my, rx, ry, rw, rh)) {
				return;
			}
			// Der Knopf liegt INNERHALB der Zeile. Wird er nicht zuerst
			// geprueft, schnappt die Zeile den Klick weg und schaltet den
			// Schalter um, statt zurueckzusetzen.
			boolean onReset = effective(page).canReset(row)
				&& Draw.inside(mx, my, resetX(rx, rw) - 4, ry, Metrics.RESET + 8, rh);

			// Bei einer Liste zaehlt zusaetzlich, WELCHE Unterzeile
			// getroffen wurde. Hier ist die y-Lage der Maus bekannt,
			// in activate() nicht mehr - deshalb wird sie hier in eine
			// Nummer uebersetzt und mitgegeben.
			int part = -1;
			if (row instanceof SettingsPage.ListRow) {
				double below = my - ry - Metrics.SET_ROW_H;
				if (below >= 0) {
					part = (int) (below / LIST_ENTRY_H);
				}
			}
			found[0] = new Hit(row, rx, ry, rw, rh,
				controlRight(page, rx, rw), onReset, part);
		});
		return found[0];
	}

	/**
	 * Fuehrt aus, was die Zeile tut.
	 *
	 * Gespeichert wird SOFORT, nicht beim Verlassen: Der Hub hat keinen
	 * Fertig-Knopf, und ein Absturz wuerde die Aenderung sonst
	 * verschlucken.
	 *
	 * @return true, wenn die Zeile etwas getan hat
	 */
	static boolean activate(SettingsPage page, Hit h, double mx) {
		if (h == null) {
			return false;
		}
		if (h.reset()) {
			// reset() speichert selbst und meldet false, wenn der Wert
			// ohnehin schon auf Standard stand. Dann ist der Klick zwar
			// folgenlos, aber verbraucht - er darf nicht an die Zeile
			// darunter durchfallen.
			effective(page).reset(h.row());
			return true;
		}
		if (h.row() instanceof SettingsPage.Toggle t) {
			t.setter().accept(!t.getter().getAsBoolean());
		} else if (h.row() instanceof SettingsPage.Action a) {
			a.onClick().run();
		} else if (h.row() instanceof SettingsPage.Choice ch) {
			ch.step(1);
		} else if (h.row() instanceof SettingsPage.Slider s) {
			setFromMouse(s, h, mx);
		} else if (h.row() instanceof SettingsPage.ListRow l) {
			return activateList(l, h, mx);
		} else if (h.row() instanceof SettingsPage.Colour c) {
			// Der Waehler wird vom Hub geoeffnet - nur der kennt die
			// Fenstergroesse und weiss, ob er noch nach unten passt.
			return false;
		} else {
			return false;                      // Hinweiszeile: nichts zu tun
		}
		page.saveNow();
		return true;
	}

	/**
	 * Klick in eine Liste.
	 *
	 * Die Knopfflaechen sind um vier Pixel groesser als das Bild. Ein
	 * Muelleimer von zwoelf Pixeln ist sonst zu klein zum Treffen,
	 * besonders weil danach etwas verschwindet - ein Fehlklick waere
	 * teuer.
	 *
	 * REIHENFOLGE: Erst die Knoepfe, dann die Zeile. Sonst schnappt die
	 * Zeile den Klick weg und klappt die Unterseite auf, statt zu
	 * loeschen.
	 */
	private static boolean activateList(SettingsPage.ListRow l, Hit h, double mx) {
		boolean onButton = mx >= h.right() - Metrics.ROW_ICON - 4
			&& mx <= h.right() + 4;

		if (h.part() < 0) {
			if (onButton && l.canAdd()) {
				l.onAdd().run();
				return true;
			}
			return false;
		}

		java.util.List<SettingsPage.ListRow.Entry> entries = l.entries().get();
		if (h.part() >= entries.size()) {
			return false;
		}
		if (onButton && l.onRemove() != null) {
			l.onRemove().accept(h.part());
			return true;
		}

		SettingsPage.ListRow.Entry entry = entries.get(h.part());

		// Eintrag mit eigener Handlung: ausfuehren und hierbleiben.
		if (entry.onClick() != null) {
			entry.onClick().run();
			return true;
		}

		// Ohne Unterseite ist der Eintrag reine Anzeige.
		if (entry.page() == null) {
			return false;
		}

		// KEINE ZWEITE EBENE: Es gibt genau einen Platz fuer eine
		// aufgeschlagene Unterseite. Stuende hier eine Liste INNERHALB einer
		// Unterseite, wuerde ein Klick die aeussere lautlos durch die innere
		// ersetzen - und der Zurueck-Knopf landete an der falschen Stelle.
		// Lieber nichts tun: Listen in Unterseiten gehoeren ueber onClick
		// bedient, nicht ueber eine weitere Ebene.
		if (openIndex >= 0) {
			return false;
		}

		openList = l;
		openIndex = h.part();
		openPage = null;
		return true;
	}

	/**
	 * Wert beim Ziehen nachfuehren - OHNE zu speichern.
	 *
	 * Das Speichern bleibt bewusst aus: Beim Ziehen liefe es sonst
	 * sechzig Mal pro Sekunde durch, und die Config-Datei wuerde fuer
	 * eine einzige Handbewegung hundertfach geschrieben. Gespeichert
	 * wird einmal, wenn die Maustaste losgeht.
	 */
	static void drag(Hit h, double mx) {
		if (h != null && h.row() instanceof SettingsPage.Slider s) {
			setFromMouse(s, h, mx);
		}
	}

	/** Ist diese Zeile eine, die man ZIEHEN kann? */
	static boolean draggable(Hit h) {
		return h != null && !h.reset() && h.row() instanceof SettingsPage.Slider;
	}

	/**
	 * Mausposition in einen Wert umrechnen.
	 *
	 * Gerechnet wird gegen die EINGERUECKTE Bahn, also dieselbe Strecke,
	 * auf der auch der Knopf laeuft. Gegen die volle Breite gerechnet
	 * saesse der Knopf an den Enden neben dem Zeiger.
	 */
	private static void setFromMouse(SettingsPage.Slider s, Hit h, double mx) {
		int r = Metrics.SLIDER_KNOB;
		int tx = trackX(h.right()) + r;
		int usable = Metrics.SLIDER_W - r * 2;
		if (usable <= 0) {
			return;
		}
		double f = Math.max(0.0, Math.min(1.0, (mx - tx) / usable));
		s.setter().accept(s.clamp(s.min() + f * (s.max() - s.min())));
	}
}
