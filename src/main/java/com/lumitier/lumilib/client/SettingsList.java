package com.lumitier.lumilib.client;

import com.lumitier.lumilib.api.SettingsPage;
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
	           int right, boolean reset) {}

	/**
	 * Rechte Kante der Bedienelemente.
	 *
	 * EINE Stelle fuer alle: Schalter, Regler, Farbfeld und der Anker des
	 * Farbwaehlers richten sich danach. Wandert der Zuruecksetzen-Knopf
	 * spaeter, wandert alles mit, statt an vier Stellen einzeln.
	 */
	static int controlRight(SettingsPage page, int x, int w) {
		return x + w - Metrics.SET_INSET
			- (page.canResetAny() ? Metrics.RESET_SLOT : 0);
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
	private static int rowHeight(SettingsPage.Row row) {
		if (row.description() == null) {
			return Metrics.SET_ROW_H;
		}
		return Metrics.SET_PAD * 2 + Ui.BODY_H
			+ Metrics.SET_DESC_GAP + Ui.SMALL_H + Ui.SMALL_D;
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
		for (SettingsPage.Section section : page.sections()) {
			java.util.List<SettingsPage.Row> rows = visible(section, query);
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
				groupH += rowHeight(row);
			}
			if (onGroup != null) {
				onGroup.group(section, rows, headTop, x, y, w, groupH);
			}

			int ry = y;
			for (SettingsPage.Row row : rows) {
				int rh = rowHeight(row);
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
					dy += rowHeight(rows.get(i));
					Draw.hLine(ctx, gx, dy, gw, Theme.DIVIDER);
				}
			},
			(row, rx, ry, rw, rh) ->
				drawRow(ctx, tr, page, row, rx, ry, rw, rh, mouseX, mouseY));
	}

	private static void drawRow(DrawContext ctx, TextRenderer tr, SettingsPage page,
	                            SettingsPage.Row row,
	                            int x, int y, int w, int h, int mouseX, int mouseY) {
		boolean hover = Draw.inside(mouseX, mouseY, x, y, w, h);
		boolean slot = page.canResetAny();
		int right = controlRight(page, x, w);

		// Hover nur bei Zeilen, die als GANZES ein Knopf sind. Bei einem
		// Schalter ist der Schalter das Ziel - eine aufleuchtende Zeile
		// wuerde dort etwas versprechen, was sie nicht einloest.
		if (hover && row instanceof SettingsPage.Action) {
			Surface.row(ctx, x + 1, y + 1, w - 2, h - 2, Theme.ROW_HOVER);
		}

		int tx = x + Metrics.SET_INSET;
		int room = w - Metrics.SET_INSET * 2 - controlWidth(row, slot);

		// Ein Hinweis ist kein Bedienelement: nicht fett, und bei einer
		// Warnung rot. Sonst sieht er aus wie etwas, das man anklicken kann.
		boolean note = row instanceof SettingsPage.Note;
		int labelColour = row instanceof SettingsPage.Note n && n.warning()
			? Theme.WARN : (note ? Theme.TEXT_MUTED : Theme.TEXT);

		if (row.description() == null) {
			// Eine Zeile, also mittig - es gibt nichts, womit sie fluchten muss.
			Ui.drawCenteredIn(ctx, tr,
				note ? Ui.text(Ui.fit(tr, row.label().getString(), room))
					 : Ui.bold(Ui.fitBold(tr, row.label().getString(), room)),
				tx, y, h, Ui.BODY_H, Ui.BODY_D, Ui.TOP_BODY, labelColour);
		} else {
			int top = y + Metrics.SET_PAD;
			Ui.drawTop(ctx, tr,
				note ? Ui.text(Ui.fit(tr, row.label().getString(), room))
					 : Ui.bold(Ui.fitBold(tr, row.label().getString(), room)),
				tx, top, Ui.TOP_BODY, labelColour);
			Ui.drawTop(ctx, tr,
				Ui.small(Ui.fitSmall(tr, row.description().getString(), room)),
				tx, top + Metrics.SET_DESC_GAP, Ui.TOP_SMALL, Theme.TEXT_FAINT);
		}

		if (row instanceof SettingsPage.Toggle t) {
			drawToggle(ctx, right - Metrics.TOGGLE_W,
				y + (h - Metrics.TOGGLE_H) / 2, t.getter().getAsBoolean());
		} else if (row instanceof SettingsPage.Slider sl) {
			drawSlider(ctx, tr, sl, y, h, right);
		} else if (row instanceof SettingsPage.Colour c) {
			drawSwatch(ctx, tr, c, y, h, right);
		}

		if (page.canReset(row)) {
			int rx = resetX(x, w);
			drawReset(ctx, rx, y, h, page.isDefault(row),
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
	private static void drawToggle(DrawContext ctx, int x, int y, boolean on) {
		int h = Metrics.TOGGLE_H;
		int w = Metrics.TOGGLE_W;
		Draw.roundedRect(ctx, x, y, w, h, h / 2,
			on ? Theme.TOGGLE_ON : Theme.TOGGLE_OFF);

		int r = (h - Metrics.TOGGLE_INSET * 2) / 2;
		int cy = y + h / 2;
		int cx = on
			? x + w - Metrics.TOGGLE_INSET - r
			: x + Metrics.TOGGLE_INSET + r;
		Draw.dot(ctx, cx, cy, r, Theme.TOGGLE_KNOB);
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
			boolean onReset = page.canReset(row)
				&& Draw.inside(mx, my, resetX(rx, rw) - 4, ry, Metrics.RESET + 8, rh);
			found[0] = new Hit(row, rx, ry, rw, rh, controlRight(page, rx, rw), onReset);
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
			page.reset(h.row());
			return true;
		}
		if (h.row() instanceof SettingsPage.Toggle t) {
			t.setter().accept(!t.getter().getAsBoolean());
		} else if (h.row() instanceof SettingsPage.Action a) {
			a.onClick().run();
		} else if (h.row() instanceof SettingsPage.Slider s) {
			setFromMouse(s, h, mx);
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
