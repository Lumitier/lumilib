package com.lumitier.lumilib.client;

/**
 * Farben und Masse des Hubs.
 *
 * Format durchgehend ARGB als int (0xAARRGGBB).
 *
 * HERKUNFT DER WERTE - nichts davon ist geraten:
 *
 * 1. Aus einem Referenz-Screenshot pixelweise gemessen ergaben sich drei
 *    getrennte Helligkeitsstufen:
 *        Sidebar 173  <  Fenster 188  <  Karte 203
 *    Entscheidend ist die Reihenfolge: Die Sidebar ist DUNKLER (eingelassen),
 *    die Karte HELLER (erhaben). Ein frueherer Entwurf hatte beide mit Weiss
 *    aufgehellt - dadurch stand die Hierarchie auf dem Kopf, alle drei lagen
 *    innerhalb von 19 Helligkeitswerten, und das Ergebnis war ein weisser
 *    Klotz ohne erkennbare Struktur.
 *
 * 2. Die Farbtoene stammen aus einem oeffentlichen, MIT-lizenzierten
 *    Material-System (WalksyLib). Uebernommen ist die LEHRE daraus, kein
 *    Code: Flaechen tragen WARME Toene, kein neutrales Weiss oder Grau.
 *    Schrift ist nie voll deckend - 85 / 50 / 25 Prozent Schwarz statt
 *    0xFF000000. Und jede erhobene Flaeche wirft einen Schatten.
 *
 * Die Masse stehen hier nur, wo sie absolut sein duerfen. Fenster- und
 * Spaltenbreiten werden im Screen aus der Bildschirmgroesse BERECHNET: die
 * logische Bildschirmbreite haengt an der GUI-Skalierung des Nutzers und
 * liegt meist zwischen 640 und 1000, nicht bei 1920.
 */
public final class Theme {

	private Theme() {}

	// --- Flaechen ------------------------------------------------------

	/** Abdunkeln der Welt. Zurueckgenommen - der Blur verschleiert schon genug. */
	// 0x6F statt der urspruenglichen 0x2E. Begruendung steht bei PANEL.
	// Zum Vergleich: Minecrafts Inventar verdunkelt mit rund 0xC0.
	public static final int SCRIM        = 0x6F000000;

	// DURCHSICHT IST DER PUNKT.
	//
	// Gemessen wurde nicht nur Helligkeit, sondern die Standardabweichung
	// innerhalb einer Flaeche - also ob der Hintergrund durchscheint:
	//
	//                  Referenz   erster Entwurf
	//     Sidebar        21.1          0.2
	//     Karte           6.4          0.2
	//
	// Bei der Referenz sieht man die unscharfe Welt DURCH die Flaechen,
	// deshalb schwankt es dort. Der erste Entwurf lag bei 87% Deckkraft -
	// da kam nichts durch, und genau das fehlte, nicht die Lichtbrechung.
	//
	// Die Werte hier lassen rund 45% des Hintergrunds durch.

	/** Fenster: warmes Off-White, gut durchscheinend. */
	// WARUM NICHT DURCHSICHTIGER:
	// Bei 0x7A kamen 40 Prozent der Welt durch das Fenster. Ueber einer
	// dunklen Szene sah das gut aus - der Entwurf lag ueber genau so einer.
	// Ueber hellem Himmel wurde das Fenster so hell wie der Himmel dahinter
	// und verschwand. Gemessen: Kontrast zur Umgebung 94 ueber der dunklen
	// Szene, 35 ueber der hellen. Dasselbe Fenster, zwei Welten.
	//
	// Durchgerechnet ueber fuenf Szenen von Hoehle bis Schnee im Sonnenlicht
	// (Scrim 0x6F, Panel 0xAF, 18 Prozent Welt sichtbar):
	//   Fensterhelligkeit schwankt nur noch um 38 statt 82 Stufen
	//   schlechtester Kontrast zur Umgebung 72 statt 32
	//
	// Achtzehn Prozent reichen, damit sich die weichgezeichnete Welt hinter
	// dem Fenster sichtbar bewegt - das ist der Glaseindruck. Mehr macht das
	// Fenster nicht glasiger, sondern nur wetterabhaengig.
	public static final int PANEL        = 0xAFF3EEEB;
	/** Sidebar: warmbrauner Schleier - eingelassen, also DUNKLER. */
	public static final int SIDEBAR      = 0x3332261E;
	/** Karte: heller Schleier - erhaben, also HELLER. */
	public static final int CARD         = 0x5CFFFFFF;
	/** Karte unter dem Mauszeiger. */
	public static final int CARD_HOVER   = 0x8CFFFFFF;
	/** Zeile in der Sidebar unter dem Mauszeiger. */
	public static final int ROW_HOVER    = 0x3DFFFFFF;

	/**
	 * Ausgewaehlte Zeile in der Sidebar.
	 *
	 * Deutlich kraeftiger als ROW_HOVER - sonst ist nicht zu
	 * unterscheiden, ob die Maus nur darueber steht oder die Seite
	 * wirklich offen ist.
	 */
	public static final int ROW_SELECTED = 0x7AFFFFFF;

	/** Lichtkante auf dem Fenster. */
	public static final int RIM          = 0x94FFFFFF;
	/** Lichtkante auf Karten, etwas zurueckhaltender. */
	public static final int RIM_CARD     = 0x66FFFFFF;

	// --- Glas: Fase, Innenschatten, Glanz ------------------------------
	//
	// Diese drei erzeugen DICKE. Eine Flaeche ohne sie liest das Auge als
	// aufgeklebtes Papier, egal wie durchsichtig sie ist - Durchsicht allein
	// macht noch kein Glas.

	/** Helle Innenkante oben. */
	public static final int BEVEL_LIGHT  = 0x80FFFFFF;
	/** Dunkle Innenkante unten. */
	public static final int BEVEL_DARK   = 0x38000000;
	/** Innenkante auf Karten, zurueckhaltender. */
	public static final int BEVEL_LIGHT_CARD = 0x4DFFFFFF;
	public static final int BEVEL_DARK_CARD  = 0x24000000;
	/** Dunkler Saum ganz innen. */
	public static final int INNER        = 0x14000000;
	public static final int INNER_CARD   = 0x0D000000;
	/**
	 * Glanzbahn - AUS.
	 *
	 * Addierte sich mit Innenschatten und Scrim zu einem milchigen Schleier
	 * ueber der gesamten Flaeche und frass den Textkontrast auf. Eine
	 * Glanzbahn funktioniert auf einer kraeftigen Flaeche; auf einer stark
	 * durchscheinenden wird sie zu Nebel.
	 *
	 * Bleibt als Wert stehen, damit sie sich spaeter wieder zuschalten
	 * laesst, falls die Flaechen kraeftiger werden.
	 */
	public static final int GLARE        = 0x00FFFFFF;
	public static final int GLARE_CARD   = 0x00FFFFFF;

	/** Schlagschatten unter dem Fenster. */
	public static final int SHADOW       = 0x47000000;
	/** Schlagschatten unter Karten. */
	public static final int SHADOW_CARD  = 0x24000000;

	/** Eingabefeld - Suchpille. */
	/**
	 * Eingabefeld - HELL, nicht dunkel.
	 *
	 * Zuerst stand hier 0x24 schwarz, dann 0x3D schwarz. Beides war
	 * falsch: auf einer hellen Flaeche wird ein dunkles Feld zu einem
	 * grauen Klotz, nicht zu einem Eingabefeld. Die Oberflaeche setzt
	 * sonst ueberall HELLE Flaechen auf dunkleren Grund - Karten,
	 * Sidebar, ausgewaehlte Zeilen. Das Feld macht es jetzt genauso.
	 */
	public static final int FIELD        = 0x3DFFFFFF;
	public static final int FIELD_HOVER  = 0x52FFFFFF;
	public static final int FIELD_FOCUS  = 0x70FFFFFF;
	public static final int FIELD_EDGE   = 0x1FFFFFFF;

	/**
	 * Kraeftigere Kante fuer Flaechen mit eigener Farbe.
	 *
	 * FIELD_EDGE ist weisses Licht und verschwindet auf einem hellen
	 * Verlauf - genau dort, wo der Farbwaehler eine Kante braucht.
	 * Dunkel funktioniert auf jedem Grund.
	 */
	// ------------------------------------------------------------------
	// GLASKANTE
	//
	// Drei Werte, die der Shader als Konstanten eingebaut bekommt. Sie
	// stehen hier und nicht dort, damit Flaeche und Karte verschieden
	// stark glaenzen koennen - eine Karte liegt auf dem Panel, nicht auf
	// der Welt, und braucht weniger.
	//
	// SPEC  gerichtetes Glanzlicht an der oberen linken Rundung
	// LENS  zweites helles Band innerhalb der Lichtkante
	// DENS  wie stark die Flaeche zum Rand hin dichter wird
	// ------------------------------------------------------------------

	public static final int SPEC        = 0xD9FFFFFF;
	public static final int SPEC_CARD   = 0x8AFFFFFF;
	public static final int LENS        = 0x4DFFFFFF;
	public static final int LENS_CARD   = 0x2EFFFFFF;

	/** Kein Farbwert, sondern ein Faktor: 0,45 heisst 45 % mehr am Rand. */
	/**
	 * Brechung, in Bildschirmpunkten.
	 *
	 * REFRACT  wie weit die Lesestelle am Rand hoechstens wandert
	 * THICK    ueber wie viele Punkte nach innen die Brechung abklingt
	 * BLUR     Abstand der Weichzeichner-Abgriffe
	 *
	 * Gemessen in ECHTEN Punkten, nicht in logischen: Der Shader liest
	 * in der Bildschirmkopie nach, und die hat die Aufloesung des
	 * Fensters. Bei GUI-Skalierung 3 ist ein logischer Pixel dort drei.
	 */
	public static final float REFRACT   = 44.0F;
	public static final float THICK     = 40.0F;
	public static final float BLUR      = 11.0F;

	/**
	 * Dispersion - wie weit Rot und Blau auseinanderlaufen.
	 *
	 * Echtes Glas bricht kurze Wellenlaengen staerker. Ohne das sieht
	 * die Brechung aus wie ein Versatz; mit ihm entsteht am Rand ein
	 * feiner Farbsaum, und genau der liest sich als Glas. Walksy fuehrt
	 * denselben Wert als REF_DISPERSION.
	 *
	 * 0,07 heisst: Blau wandert 7 % weiter als Gruen, Rot 7 % weniger.
	 * Darueber wird es ein Regenbogen statt eines Saums.
	 */
	public static final float DISPERSE  = 0.05F;

	/**
	 * Fresnel - das Aufhellen zum Rand hin.
	 *
	 * Flach auf Glas geschaut ist es durchsichtig, streifend wird es zum
	 * Spiegel. FRESNEL_PX ist die Strecke nach innen, ueber die das
	 * abklingt; HARD, wie scharf. Das ist der Unterschied zwischen
	 * "Rand mit Linie" und "Scheibe mit Dicke".
	 */
	public static final float FRESNEL   = 16.0F;
	public static final float FRESNEL_H = 4.0F;
	public static final int   FRESNEL_A = 0x59FFFFFF;

	/**
	 * Toenung NUR fuer die brechende Flaeche.
	 *
	 * Deutlich durchsichtiger als PANEL: Wer durch Glas schaut, soll
	 * auch etwas sehen. PANEL ist mit 69 % darauf ausgelegt, dass
	 * dahinter nichts durchkommt - hier waere das schade um die
	 * Brechung, fuer die der ganze Aufwand betrieben wurde.
	 */
	public static final int GLASS_TINT  = 0x8AF3EEEB;

	/**
	 * Helligkeitsband des Hintergrunds hinter dem Glas.
	 *
	 * Ohne das ist die Scheibe nachts schwarz und auf Schnee blendend -
	 * und die Schrift darauf je nach Blickrichtung unlesbar. Die
	 * Helligkeit wird deshalb in dieses Band gestaucht, der Farbton
	 * bleibt. Walksy fuehrt dieselben zwei Werte als
	 * OVER_WORLD_TONE_FLOOR und _CEILING mit 0,16 und 0,72.
	 *
	 * Hoeher als seine, weil mein Panel heller ist als seines.
	 */
	public static final float TONE_FLOOR = 0.30F;
	public static final float TONE_CEIL  = 0.80F;

	/**
	 * Wie viel Farbe der Hintergrund hinter dem Glas behaelt.
	 *
	 * 1 waere die volle Farbe, 0 reines Grau. Mattiertes Glas streut das
	 * Licht - es behaelt den Farbton, aber nicht dessen Kraft. Ohne
	 * diesen Wert bleibt jede Unebenheit im Hintergrund als Farbfleck
	 * stehen, und eine leere Flaeche sieht fleckig aus statt ruhig.
	 */
	public static final float SATURATION = 0.42F;

	public static final float DENS      = 0.45F;
	public static final float DENS_CARD = 0.22F;

	public static final int EDGE_STRONG  = 0x3D000000;

	/** Helle Gegenlinie aussen um EDGE_STRONG - hebt die Kante vom Glas ab. */
	public static final int EDGE_LIGHT   = 0x52FFFFFF;

	/** Haarlinie zwischen Bereichen. */
	public static final int DIVIDER      = 0x2E3C3C43;
	/** Platzhalter hinter einem fehlenden Mod-Icon. */
	public static final int ICON_BG      = 0x2E000000;

	// ------------------------------------------------------------------
	// SCHALTER
	//
	// Das Blau ist aus Walksys Oberflaeche gemessen: 0,132,232. Es ist
	// nicht meine Erfindung und auch nicht Minecrafts - es ist die
	// Farbe, die jedes Betriebssystem fuer einen eingeschalteten Schalter
	// benutzt. Genau deshalb erkennt man sie ohne Beschriftung.
	//
	// Die Mod-Icons sind warm und golden. Ein goldener Schalter wuerde
	// sich darin verlieren; der Kontrast ist hier der Punkt.
	// ------------------------------------------------------------------

	/**
	 * Warnfarbe fuer Hinweiszeilen.
	 *
	 * Nicht Minecrafts reines Rot: Das ist auf hellem Grund grell und
	 * schlecht lesbar. Dieses Rot ist abgedunkelt und entspannt genug,
	 * dass eine ganze Zeile darin lesbar bleibt.
	 */
	public static final int WARN         = 0xFFB3261E;

	public static final int TOGGLE_ON    = 0xFF0084E8;
	public static final int TOGGLE_OFF   = 0x33000000;
	public static final int TOGGLE_KNOB  = 0xFFFFFFFF;

	// --- Schrift -------------------------------------------------------
	//
	// Erst auf 85/50/35 Prozent Schwarz gesetzt, weil das Vorbild es so
	// macht. Das war ein Fehler im Zusammenspiel: Dessen Flaechen sind
	// kraeftiger, meine tragen zusaetzlich Glanz und Innenschatten. Grau
	// auf hellem, leicht verschleiertem Grund ergibt Grau auf Grau.
	//
	// Lesbarkeit schlaegt Vorbildtreue.
	public static final int TEXT         = 0xFF14141A;
	public static final int TEXT_MUTED   = 0xC4000000;
	public static final int TEXT_FAINT   = 0x99000000;

	// --- Akzente -------------------------------------------------------
	/** Warmes Gold, derselbe Ton wie im Mod-Icon. */
	public static final int ACCENT       = 0xFFE8A33D;
	/** Gruen fuer Microsoft-Konto. */
	public static final int OK           = 0xE62E8B4F;
	/** Grau fuer Offline. */
	public static final int NEUTRAL      = 0xD98E8E93;

	// --- Masse ---------------------------------------------------------
	public static final int ROW_H        = 17;
	public static final int CARD_H       = 52;
	public static final int CARD_GAP     = 7;
	/** Schmalste sinnvolle Kartenbreite, bestimmt die Spaltenzahl. */
	public static final int CARD_MIN_W   = 150;
	/** Ausweitung und Versatz des Fensterschattens. */
	public static final int SHADOW_SPREAD = 8;
	public static final int SHADOW_OFFSET = 3;
}
