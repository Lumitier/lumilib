package com.lumitier.lumilib.client;

/**
 * Masse des Hubs, aus einer gelungenen Referenzoberflaeche ABGEMESSEN.
 *
 * Im Vergleichsbild (Inhaltsbereich 1628 x 925) ergaben sich:
 *
 *     Karte               401 x 132      -> Hoehe = 14,3 % der Inhaltshoehe
 *     Icon in der Karte   48             -> 36 % der Kartenhoehe
 *     Innenabstand Karte  ~20            -> 15 % der Kartenhoehe
 *     Abstand zwischen    18             -> 2 % der Inhaltshoehe
 *     Spalten             immer 3
 *     Sidebar-Zeile       46 hoch, Icon 28 (61 %)
 *     Ueberschrift        rund 1,6-fache Hoehe des Fliesstexts
 *
 * Der erste Entwurf hatte Karten mit 52 Pixeln Hoehe und 18er Icons bei
 * 9 Pixeln Innenabstand - alles etwa halb so gross wie es sein muesste,
 * und ohne jede Groessenstaffel in der Schrift. Deshalb wirkte es gedraengt
 * und beliebig zugleich.
 *
 * Die Werte hier sind in logischen Pixeln bei ueblicher GUI-Skalierung
 * (Bildschirm logisch ~960 x 540). Fenstergroesse und Spaltenbreite werden
 * weiterhin aus der Bildschirmgroesse gerechnet, nicht gedeckelt.
 */
public final class Metrics {

	private Metrics() {}

	/** Anteil des Bildschirms, den das Fenster einnimmt. */
	public static final float PANEL_W = 0.88F;
	public static final float PANEL_H = 0.84F;

	/** Innenabstand des Fensters. */
	public static final int PAD = 10;

	/** Sidebar-Breite als Anteil der Fensterbreite. */
	public static final float SIDEBAR_W = 0.26F;

	// --- Sidebar -------------------------------------------------------
	public static final int ROW_H       = 21;
	public static final int ROW_ICON    = 13;
	/** Abstand zwischen Icon und Beschriftung. */
	public static final int ROW_ICON_GAP = 7;
	/** Kopfzeile eines Abschnitts ("MODS"). */
	public static final int SECTION_H   = 20;

	/** Kantenlaenge der Stecknadel rechts in der Mod-Zeile. */
	public static final int PIN         = 9;
	/**
	 * Platz, den die Stecknadel der Beschriftung wegnimmt.
	 *
	 * IMMER abgezogen, auch wenn gerade keine Nadel zu sehen ist: Sonst
	 * waere der Text beim Ueberfahren der Zeile ploetzlich kuerzer und
	 * ein langer Name wuerde unter der Maus abschnappen.
	 */
	public static final int PIN_SLOT    = PIN + 5;

	/** Kantenlaenge des Skinkopfs im Kontoblock. */
	public static final int HEAD        = 22;

	/**
	 * GESAMTHOEHE der Kontozeile - bewusst groesser als HEAD.
	 *
	 * Vorher stand hier kein eigener Wert; die Hoehe fiel als
	 * max(HEAD, Textblock) an und wurde damit 22. Im Entwurf sind es 30,
	 * und weil jede weitere Zeile der Sidebar auf dieser Hoehe aufbaut,
	 * rutschte im Spiel ALLES darunter um acht Pixel nach oben.
	 *
	 * An vier Stellen gegen den Entwurf gemessen (logische Pixel):
	 *     Kontoname  Tinte oben   gemessen 67,5   gerechnet 67
	 *     Updates-Zeile           gemessen 115,5  gerechnet 115
	 *     MODS                    gemessen 148,5  gerechnet 148
	 *     erste Mod-Zeile         gemessen 163    gerechnet 162
	 *
	 * Die acht Pixel sind kein Zufall: Kopf und Textblock sind beide 22
	 * hoch, der Block braucht aber Luft nach oben und unten, sonst klebt
	 * er am Trennstrich.
	 */
	public static final int ACCOUNT_H   = 30;

	// --- Karten --------------------------------------------------------
	public static final int CARD_H      = 62;
	public static final int CARD_GAP    = 8;
	public static final int CARD_PAD    = 10;
	public static final int CARD_ICON   = 24;
	/** Spaltenzahl, solange die Breite reicht. */
	public static final int CARD_COLS   = 3;
	/** Darunter wird eine Spalte weniger genommen. */
	public static final int CARD_MIN_W  = 132;

	// --- Kopfbereich des Inhalts ---------------------------------------
	public static final int TITLE_H     = 26;
	public static final int SEARCH_H    = 20;
	public static final int SEARCH_W    = 150;
	public static final int SEARCH_ICON = 10;

	// ------------------------------------------------------------------
	// EINSTELLUNGEN
	//
	// Aus Walksys Oberflaeche gemessen und auf die eigene Schriftstaffel
	// umgerechnet. Sein Bild war nicht massstaeblich, deshalb ueber das
	// Verhaeltnis zur Versalhoehe: dort 14 Bildpunkte, hier BODY_H = 9,
	// also Faktor 0,643.
	//
	//     einfache Zeile       45 -> 29
	//     Zeile mit Untertext  84 -> 54
	//     Schalter          64x28 -> 41x18
	//     Innenabstand         18 -> 12
	// ------------------------------------------------------------------

	/** Senkrechter Innenabstand einer Einstellungszeile. */
	/** Kantenlaenge des Zuruecksetzen-Knopfs rechts aussen. */
	public static final int RESET      = 9;
	/**
	 * Platz, den der Zuruecksetzen-Knopf den Bedienelementen wegnimmt.
	 *
	 * Wird fuer die GANZE SEITE reserviert, sobald auch nur eine Zeile
	 * einen Standardwert hat - sonst saessen Schalter und Regler je nach
	 * Zeile unterschiedlich weit rechts, und die rechte Kante der Liste
	 * waere keine Linie mehr, sondern eine Treppe.
	 */
	public static final int RESET_SLOT = RESET + 9;

	/**
	 * Zuruecksetzen-Knopf im Kopf der Mod-Seite.
	 *
	 * Groesser als der in der Zeile: Er steht neben dem Suchfeld und dem
	 * Titel, nicht zwischen Schaltern - in der Groesse einer Zeilennadel
	 * sieht er dort verloren aus.
	 */
	public static final int RESET_ALL  = 12;

	public static final int SET_PAD = 10;

	/** Waagrechter Innenabstand, links wie rechts. */
	public static final int SET_INSET = 12;

	/** Einfache Zeile: 2 * SET_PAD + BODY_H. */
	public static final int SET_ROW_H = 29;

	/** Abstand von der Oberkante der Beschriftung zur des Untertextes. */
	public static final int SET_DESC_GAP = 15;

	/** Luft ueber einer Abschnittsueberschrift. */
	public static final int SET_SECTION_GAP = 18;

	/** Luft zwischen Ueberschrift und der Gruppe darunter. */
	public static final int SET_HEAD_GAP = 8;

	/** Schalter. Breite zu Hoehe rund 2,2 zu 1, wie im Vorbild. */
	public static final int TOGGLE_W = 40;
	public static final int TOGGLE_H = 18;

	/** Abstand des Knopfes zum Rand des Schalters. */
	public static final int TOGGLE_INSET = 2;

	// ------------------------------------------------------------------
	// REGLER
	//
	// Die Bahn ist duenn, der Knopf gross: Getroffen wird der KNOPF, und
	// ein 4 Pixel hoher Streifen laesst sich mit der Maus nicht greifen.
	// Die Trefferflaeche ist deshalb die ganze Zeilenhoehe ueber der
	// Breite der Bahn, nicht die Bahn selbst.
	// ------------------------------------------------------------------

	public static final int SLIDER_W = 86;
	public static final int SLIDER_TRACK = 4;
	public static final int SLIDER_KNOB = 6;

	/** Platz fuer den Zahlenwert rechts daneben. */
	public static final int VALUE_W = 34;

	// ------------------------------------------------------------------
	// FARBFELD
	//
	// Breiter als hoch, damit man eine Farbe wirklich beurteilen kann -
	// ein Quadrat von 14 Pixeln reicht dafuer nicht.
	// ------------------------------------------------------------------

	public static final int SWATCH_W = 26;
	public static final int SWATCH_H = 14;

	/** Platz fuer den Hexwert links daneben, Form #AARRGGBB. */
	public static final int HEX_W = 46;
}
