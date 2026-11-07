package com.lumitier.lumilib.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import com.lumitier.lumilib.client.gpu.SdfFont;
import com.lumitier.lumilib.client.gpu.SdfText;

import static com.lumitier.lumilib.LumiLib.MOD_ID;

/**
 * Schrift des Hubs.
 *
 * Minecrafts Standardschrift ist eine Pixelschrift mit fester Groesse. In
 * einer Oberflaeche mit weichen Kanten und Schatten wirkt sie wie ein
 * Fremdkoerper - der Rest ist geglaettet, die Schrift nicht.
 *
 * DER WEG DAHIN - ohne Shader und ohne Mixin:
 * Minecraft kann TrueType-Schriften direkt aus Resourcepacks laden. Unter
 * assets/lumilib/font/ liegen zwei Provider-Dateien:
 *
 *     { "providers": [ { "type": "ttf", "file": "lumilib:inter_regular.ttf",
 *                        "size": 10.0, "oversample": 4.0, "shift": [0,0] } ] }
 *
 * "oversample" ist der entscheidende Wert: Minecraft rastert die Schrift in
 * vierfacher Aufloesung und verkleinert sie dann - daher die weichen Kanten.
 *
 * SCHRIFTWAHL UND LIZENZ:
 * Inter, unter der SIL Open Font License. Die darf weitergegeben werden.
 * Die Referenzoberflaeche benutzt Apples San Francisco - die ist NICHT
 * weitergabefaehig, egal wo sie zu finden ist. Inter ist ihr bewusst nahe
 * und fuer Bildschirmoberflaechen entworfen.
 *
 * Auf zwei Schnitte und den noetigen Zeichensatz verkleinert kosten beide
 * zusammen keine 100 KB.
 *
 * HINWEIS ZUR SIGNATUR: Style.withFont nimmt in 1.21.11 kein Identifier mehr,
 * sondern einen StyleSpriteSource. Aeltere Beispiele kompilieren hier nicht.
 */
public final class Ui {

	private Ui() {}

	/**
	 * GROESSENSTAFFEL - der wichtigste Teil dieser Klasse.
	 *
	 * Beim Vergleich mit einer gelungenen Oberflaeche war der auffaelligste
	 * Unterschied nicht Farbe oder Form, sondern dass dort die Ueberschrift
	 * rund anderthalb mal so gross ist wie der Fliesstext und die
	 * Versionszeile kleiner. Ohne solche Staffel liest sich nichts als
	 * Hierarchie - alles wirkt gleich wichtig und damit beliebig.
	 *
	 * Minecraft kann das ohne Umwege: pro Groesse ein eigener
	 * TTF-Provider, alle auf dieselbe Schriftdatei.
	 */
	public static final StyleSpriteSource.Font SMALL =
		new StyleSpriteSource.Font(Identifier.of(MOD_ID, "ui_small"));
	public static final StyleSpriteSource.Font REGULAR =
		new StyleSpriteSource.Font(Identifier.of(MOD_ID, "ui"));
	public static final StyleSpriteSource.Font BOLD =
		new StyleSpriteSource.Font(Identifier.of(MOD_ID, "ui_bold"));
	public static final StyleSpriteSource.Font TITLE =
		new StyleSpriteSource.Font(Identifier.of(MOD_ID, "ui_title"));

	/** Zeichenkette in der Oberflaechenschrift. */
	public static Text text(String s) {
		return Text.literal(s == null ? "" : s).styled(st -> st.withFont(REGULAR));
	}

	/** Zeichenkette im kraeftigeren Schnitt - Ueberschriften, Namen. */
	public static Text bold(String s) {
		return Text.literal(s == null ? "" : s).styled(st -> st.withFont(BOLD));
	}

	/** Bestehenden Text auf die Oberflaechenschrift umstellen. */
	public static Text of(Text t) {
		return t.copy().styled(st -> st.withFont(REGULAR));
	}

	/** Bestehenden Text im kraeftigeren Schnitt. */
	public static Text boldOf(Text t) {
		return t.copy().styled(st -> st.withFont(BOLD));
	}

	/** Klein - Versionszeilen, Abschnittsbeschriftungen. */
	public static Text small(String s) {
		return Text.literal(s == null ? "" : s).styled(st -> st.withFont(SMALL));
	}

	public static Text smallOf(Text t) {
		return t.copy().styled(st -> st.withFont(SMALL));
	}

	/** Abschnittsueberschrift in den Einstellungen - zwischen Text und Titel. */
	public static Text head(String s) {
		return Text.literal(s == null ? "" : s).styled(st -> st.withFont(BOLD));
	}

	public static Text headOf(Text t) {
		return t.copy().styled(st -> st.withFont(BOLD));
	}

	/** Gross - Ueberschrift des Inhaltsbereichs. */
	public static Text title(String s) {
		return Text.literal(s == null ? "" : s).styled(st -> st.withFont(TITLE));
	}

	public static Text titleOf(Text t) {
		return t.copy().styled(st -> st.withFont(TITLE));
	}

	// ------------------------------------------------------------------
	// SICHTBARE ZEILENHOEHEN
	//
	// TextRenderer.fontHeight liefert immer 9, egal welche Schrift gesetzt
	// ist - der Wert stammt aus Minecrafts Pixelschrift. Fuer eigene
	// TTF-Provider stimmt er nicht, und wer damit zentriert, setzt alles
	// schief.
	//
	// Diese Werte sind aus der Provider-Groesse abgeleitet (rund 0,72 der
	// Schriftgroesse entspricht der Versalhoehe) und entsprechen dem, was
	// in der Entwurfsvorschau gemessen wurde. Sitzt im Spiel etwas daneben,
	// ist das hier die Stellschraube - NICHT die einzelnen Zeichenaufrufe.
	// ------------------------------------------------------------------

	public static final int SMALL_H = 7;    // Versalhoehe bei Groesse 10
	public static final int BODY_H  = 9;    // Versalhoehe bei Groesse 12,5
	public static final int HEAD_H  = 11;   // Versalhoehe bei Groesse 15
	public static final int TITLE_H = 13;   // Versalhoehe bei Groesse 19

	// ------------------------------------------------------------------
	// UNTERLAENGEN
	//
	// Die Versalhoehe reicht zum ZENTRIEREN einer einzelnen Zeile - das Auge
	// mittet nach den Grossbuchstaben, nicht nach dem tiefsten Punkt.
	//
	// Zum STAPELN zweier Zeilen reicht sie NICHT. "Player314" hat ein y, das
	// unter die Grundlinie ragt. Wer den Zeilenabstand nur aus der
	// Versalhoehe bildet, setzt die naechste Zeile mitten in diese
	// Unterlaenge - genau das liess "Offline session" in den Namen laufen.
	//
	// Aus der Schriftdatei gerechnet (unitsPerEm 1000, Unterlaenge 295):
	//     Groesse 10    2,95  ->  3
	//     Groesse 12,5  3,69  ->  4
	//     Groesse 19    5,61  ->  6
	//
	// Zeilenabstand = Versalhoehe + Unterlaenge + Durchschuss.
	// ------------------------------------------------------------------

	public static final int SMALL_D = 3;
	public static final int BODY_D  = 4;
	public static final int HEAD_D  = 4;    // 15 * 0,295 = 4,43
	public static final int TITLE_D = 6;

	/** Vorschub von Oberkante zu Oberkante der naechsten Zeile. */
	public static int advance(int capHeight, int descender, int leading) {
		return capHeight + descender + leading;
	}

	// ------------------------------------------------------------------
	// VERSATZ DER SICHTBAREN OBERKANTE
	//
	// Das ist die wichtigste Konstante dieser Klasse, und sie war der Grund,
	// warum die Oberflaeche im Spiel anders aussah als im Entwurf.
	//
	// Minecraft legt die Grundlinie auf die Oberlaenge der Vanilla-Schrift,
	// also 7 Pixel unter das uebergebene y. Eine TTF-Glyphe wird darueber
	// um ihre eigene Versalhoehe nach oben gezeichnet (FreeTypes
	// bitmap_top, in TtfGlyph als ascent gespeichert). Daraus folgt:
	//
	//     sichtbare Oberkante = y + 7 - Versalhoehe
	//
	//     ui_small (10)    7,1  ->  y - 0,1     praktisch bei y
	//     ui       (12,5)  8,9  ->  y - 1,9
	//     ui_title (19)   13,5  ->  y - 6,5     sechs Pixel zu hoch
	//
	// Die Ueberschrift rutscht also nach oben, der kleine Text nicht - und
	// genau dieses Durcheinander liess das Ganze "komisch formatiert"
	// aussehen. Wer die Oberkante an eine bestimmte Stelle setzen will,
	// muss diesen Versatz ausgleichen.
	//
	// Werte aus der Schriftdatei gerechnet (unitsPerEm 1000, sCapHeight 710),
	// nicht geschaetzt.
	// ------------------------------------------------------------------

	public static final int TOP_SMALL = 0;
	public static final int TOP_BODY  = -2;
	// ui_head (15)  10,65  ->  y - 3,65.  Fuer diese Groesse gibt es KEINEN
	// Vanilla-Provider. Faellt die Darstellung auf Minecrafts Schrift
	// zurueck, erscheinen Abschnittsueberschriften in Fliesstextgroesse -
	// kleiner als gewollt, aber lesbar. Ein eigener Provider waere eine
	// weitere TTF im Jar, nur fuer den Notfall.
	public static final int TOP_HEAD  = -4;
	public static final int TOP_TITLE = -6;

	/**
	 * Zeichnet Text so, dass seine SICHTBARE Oberkante auf top liegt.
	 *
	 * @param topOffset TOP_SMALL, TOP_BODY oder TOP_TITLE
	 */
	public static void drawTop(DrawContext ctx, TextRenderer tr, Text text,
	                           int x, int top, int topOffset, int argb) {
		// ZUERST ueber das Abstandsfeld. Dort wird die Kante jedes
		// Buchstabens in der Groesse neu gerechnet, in der gezeichnet
		// wird - Minecraft skaliert stattdessen eine feste Rasterung.
		// Genau das sah "komisch formatiert" aus.
		if (SdfText.drawTop(ctx, text.getString(), x, top,
				pxFor(topOffset), argb, isBold(text))) {
			return;
		}
		ctx.drawText(tr, text, x, top - topOffset, argb, false);
	}

	/**
	 * Schriftgroesse zum Versatz.
	 *
	 * Der Versatz identifiziert die Staffel eindeutig - TOP_SMALL gehoert
	 * zu 10, TOP_TITLE zu 19. Das spart einen zusaetzlichen Parameter an
	 * jeder der Zeichenstellen.
	 */
	private static float pxFor(int topOffset) {
		if (topOffset == TOP_TITLE) {
			return 19.0F;
		}
		if (topOffset == TOP_HEAD) {
			return 15.0F;
		}
		if (topOffset == TOP_SMALL) {
			return 10.0F;
		}
		return 12.5F;
	}

	/** Kraeftiger Schnitt? Erkennbar am gesetzten Zeichensatz. */
	private static boolean isBold(Text text) {
		StyleSpriteSource f = text.getStyle().getFont();
		return BOLD.equals(f) || TITLE.equals(f);
	}

	/**
	 * Zeichnet Text senkrecht MITTIG zu einer gedachten Linie.
	 *
	 * @param centerY    gemeinsame Mittellinie von Symbol und Beschriftung
	 * @param capHeight  SMALL_H, BODY_H oder TITLE_H
	 * @param topOffset  passend dazu TOP_SMALL, TOP_BODY oder TOP_TITLE
	 */
	public static void drawCentered(DrawContext ctx, TextRenderer tr, Text text,
	                                int x, int centerY, int capHeight,
	                                int topOffset, int argb) {
		drawTop(ctx, tr, text, x, centerY - capHeight / 2, topOffset, argb);
	}

	/**
	 * Zeichnet Text senkrecht mittig - nach der SICHTBAREN Tinte.
	 *
	 * WARUM ES drawCentered NICHT TUT:
	 * drawCentered mittet nach der Versalhoehe. Bei "Player887" ragt das y
	 * aber nach unten, und die sichtbare Tinte sitzt dadurch tiefer als
	 * die gerechnete Mitte - gemessen 3,4 Pixel neben der Mitte eines
	 * 22 Pixel hohen Symbols daneben.
	 *
	 * Richtig gemittet wird nach Versalhoehe PLUS Unterlaenge. Das ist
	 * unabhaengig davon, ob die Zeichenkette zufaellig eine Unterlaenge
	 * enthaelt - sonst saesse "LumiLib" anders als "Perpetual Snow".
	 */
	public static void drawCenteredLine(DrawContext ctx, TextRenderer tr, Text text,
	                                    int x, int centerY, int capHeight,
	                                    int descender, int topOffset, int argb) {
		// AUFRUNDEN, nicht abrunden. Exakt waeren bei Fliesstext 6,29
		// Pixel; abgerundet auf 6 bleibt ein Rest von bis zu 1,5 Pixeln
		// nach unten. Mit 7 liegt der groesste Fehler ueber sieben
		// geprueften Zeichenketten bei 0,47 Pixeln - also unter einem
		// halben.
		drawTop(ctx, tr, text, x, centerY - (capHeight + descender + 1) / 2,
			topOffset, argb);
	}

	/**
	 * Zeichnet EINE Zeile genau mittig in ein Rechteck.
	 *
	 * UNTERSCHIED ZU drawCenteredLine, und warum es beide gibt:
	 * drawCenteredLine mittet den Kasten aus Versalhoehe PLUS Unterlaenge
	 * und ist damit fuer LISTEN richtig: Alle Zeilen bekommen dieselbe
	 * Schreiblinie, egal welche Buchstaben darin vorkommen. Wuerde man
	 * dort nach der sichtbaren Tinte mitten, saesse "Perpetual Snow" um
	 * knapp zwei Pixel anders als "LumiLib" - in einer Liste faellt das
	 * sofort auf.
	 *
	 * Bei einer EINZELNEN Zeile gibt es nichts, womit sie fluchten muss.
	 * Dort zaehlt nur, dass oben und unten gleich viel Luft bleibt, und
	 * das heisst: nach der sichtbaren Tinte mitten.
	 *
	 * ZWEI DINGE, DIE drawCenteredLine UNTERSCHLAEGT:
	 * 1. Der Rand des Abstandsfeldes. Die Oberkante, die drawTop bekommt,
	 *    ist die Oberkante des Kastens, nicht die des Buchstabens - bei
	 *    12,5 Pixeln liegen 2,1 Pixel dazwischen.
	 * 2. Die halbe Zeile bei ungerader Hoehe. Eine 21 Pixel hohe Zeile hat
	 *    ihre Mitte bei 10,5, nicht bei 10.
	 * Zusammen waren das gemessene 0,98 Pixel zu hoch.
	 *
	 * @param top       Oberkante des Rechtecks
	 * @param height    dessen Hoehe
	 * @param capHeight SMALL_H, BODY_H oder TITLE_H
	 * @param descender passend dazu SMALL_D, BODY_D oder TITLE_D
	 */
	public static void drawCenteredIn(DrawContext ctx, TextRenderer tr, Text text,
	                                  int x, int top, int height, int capHeight,
	                                  int descender, int topOffset, int argb) {
		boolean boldWeight = isBold(text);
		float px = pxFor(topOffset);
		SdfFont f = SdfText.font(boldWeight);
		// Ohne eigene Schrift bleibt nur die grobe Rechnung: kein Rand des
		// Abstandsfeldes, und die Unterlaenge als Pauschale statt gemessen.
		float pad = f == null ? 0.0F : f.inkTopOffset(px);
		float below = f == null ? descender : f.descentOf(text.getString(), px);
		float t = top + height / 2.0F - pad - (capHeight + below) / 2.0F;
		drawTop(ctx, tr, text, x, Math.round(t), topOffset, argb);
	}

	/** Breite in der Oberflaechenschrift - NICHT getWidth(String) benutzen. */
	/**
	 * Breite in Bildschirmpixeln - fuer GENAU die Staffel, in der auch
	 * gezeichnet wird.
	 *
	 * DAS IST DIE WICHTIGSTE REGEL DIESER KLASSE:
	 * Wer in einem Schnitt misst und in einem anderen zeichnet, bekommt
	 * stillschweigend falsche Ergebnisse. Der fette Schnitt ist breiter
	 * als der normale - misst man normal und zeichnet fett, wird zu frueh
	 * gekuerzt und jede Zentrierung sitzt daneben. Umgekehrt beim kleinen
	 * Schnitt: dort kuerzt man frueher, als noetig waere.
	 *
	 * Deshalb gibt es diese Fassung mit Groesse und Schnitt, und die
	 * kurzen Namen darunter fuer die drei Staffeln.
	 */
	public static int width(TextRenderer tr, String s, float px, boolean boldWeight) {
		float w = SdfText.width(s, px, boldWeight);
		return w >= 0.0F ? Math.round(w) : tr.getWidth(boldWeight ? bold(s) : text(s));
	}

	/** Fliesstext, normal. */
	public static int width(TextRenderer tr, String s) {
		return width(tr, s, 12.5F, false);
	}

	/** Fliesstext, fett - Namen und Beschriftungen von Knoepfen. */
	public static int widthBold(TextRenderer tr, String s) {
		return width(tr, s, 12.5F, true);
	}

	/** Klein - Versionszeilen. */
	public static int widthSmall(TextRenderer tr, String s) {
		return width(tr, s, 10.0F, false);
	}

	/** Abschnittsueberschrift. */
	public static int widthHead(TextRenderer tr, String s) {
		return width(tr, s, 15.0F, true);
	}

	/** Ueberschrift. */
	public static int widthTitle(TextRenderer tr, String s) {
		return width(tr, s, 19.0F, true);
	}

	/**
	 * Bricht Text auf eine Pixelbreite um, an Wortgrenzen.
	 *
	 * Gemessen wird ueber width() - also ueber dieselbe Quelle wie beim
	 * Zeichnen. Ein Wort, das allein schon zu breit ist, bleibt stehen
	 * statt zerhackt zu werden; abgeschnitten wird es dann beim Zeichnen.
	 */
	public static java.util.List<String> wrap(TextRenderer tr, String s, int maxWidth) {
		java.util.List<String> out = new java.util.ArrayList<>();
		if (s == null || s.isEmpty() || maxWidth <= 0) {
			return out;
		}
		StringBuilder line = new StringBuilder();
		for (String word : s.split(" ")) {
			if (line.isEmpty()) {
				line.append(word);
				continue;
			}
			String probe = line + " " + word;
			if (width(tr, probe) <= maxWidth) {
				line.setLength(0);
				line.append(probe);
			} else {
				out.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if (!line.isEmpty()) {
			out.add(line.toString());
		}
		return out;
	}

	/**
	 * Kuerzt auf eine Pixelbreite und haengt ein Auslassungszeichen an.
	 *
	 * Gemessen wird in der Oberflaechenschrift, nicht in der Pixelschrift -
	 * sonst sitzt die Kuerzung daneben, weil die Zeichen anders breit sind.
	 */
	public static String fit(TextRenderer tr, String s, int maxWidth,
	                        float px, boolean boldWeight) {
		if (s == null || s.isEmpty() || width(tr, s, px, boldWeight) <= maxWidth) {
			return s;
		}
		int room = maxWidth - width(tr, "...", px, boldWeight);
		if (room <= 0) {
			return "";
		}
		int end = s.length();
		while (end > 0 && width(tr, s.substring(0, end), px, boldWeight) > room) {
			end--;
		}
		return s.substring(0, end).stripTrailing() + "...";
	}

	/** Fliesstext, normal. */
	public static String fit(TextRenderer tr, String s, int maxWidth) {
		return fit(tr, s, maxWidth, 12.5F, false);
	}

	/** Fliesstext, fett. */
	public static String fitBold(TextRenderer tr, String s, int maxWidth) {
		return fit(tr, s, maxWidth, 12.5F, true);
	}

	/** Klein. */
	public static String fitSmall(TextRenderer tr, String s, int maxWidth) {
		return fit(tr, s, maxWidth, 10.0F, false);
	}
}
