package com.lumitier.lumilib.client;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import com.lumitier.lumilib.client.gpu.Sdf;

import static com.lumitier.lumilib.LumiLib.MOD_ID;

/**
 * Flaechen des Hubs - gezeichnet als GUI-Sprites, nicht als Rechtecke.
 *
 * WARUM NICHT DrawContext.fill:
 * fill() kennt nur Rechtecke. Abgerundete Ecken entstehen dort als
 * Treppenstufen und ein weicher Schatten ist gar nicht moeglich. Genau diese
 * harten Kanten sind der Grund, warum eine selbstgezeichnete Oberflaeche
 * billig aussieht - nicht die Farbwahl.
 *
 * Die Sprites liegen unter assets/lumilib/textures/gui/sprites/ und bringen
 * je eine .mcmeta mit Nine-Slice-Angaben mit, genau wie Vanillas Knopf:
 *
 *     { "gui": { "scaling": { "type": "nine_slice", "width": .., "height": .., "border": .. } } }
 *
 * Minecraft skaliert dabei nur die Mitte und laesst den Rahmen unangetastet.
 * Der Eckradius bleibt also in Pixeln fest - deshalb gibt es drei Groessen
 * statt einer skalierbaren.
 *
 * Alle Sprites sind WEISS mit geglaettetem Alphakanal. Die Farbe kommt zur
 * Laufzeit ueber den letzten Parameter von drawGuiTexture, dadurch reicht
 * eine Textur fuer Fenster, Sidebar, Karten und Hover.
 */
public final class Surface {

	private Surface() {}

	// GUI-Sprites werden ohne "textures/gui/sprites/" und ohne ".png" angesprochen.
	private static final Identifier LG     = Identifier.of(MOD_ID, "surface_lg");
	private static final Identifier MD     = Identifier.of(MOD_ID, "surface_md");
	private static final Identifier SM     = Identifier.of(MOD_ID, "surface_sm");
	private static final Identifier SHADOW_LG = Identifier.of(MOD_ID, "shadow_lg");
	private static final Identifier SHADOW_SM = Identifier.of(MOD_ID, "shadow_sm");
	private static final Identifier RIM_LG = Identifier.of(MOD_ID, "rim_lg");
	private static final Identifier RIM_MD = Identifier.of(MOD_ID, "rim_md");
	private static final Identifier BEVEL_LG = Identifier.of(MOD_ID, "bevel_lg");
	private static final Identifier BEVEL_MD = Identifier.of(MOD_ID, "bevel_md");
	private static final Identifier INNER_LG = Identifier.of(MOD_ID, "inner_lg");
	private static final Identifier INNER_MD = Identifier.of(MOD_ID, "inner_md");
	private static final Identifier GLARE_LG = Identifier.of(MOD_ID, "glare_lg");
	private static final Identifier GLARE_MD = Identifier.of(MOD_ID, "glare_md");

	private static void sprite(DrawContext ctx, Identifier id, int x, int y, int w, int h, int argb) {
		if (w <= 0 || h <= 0) {
			return;
		}
		ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, id, x, y, w, h, argb);
	}

	/**
	 * Das Fenster als EINE Glasflaeche - Fuellung, Innenschatten, Fase und
	 * Lichtkante in einem Zug.
	 *
	 * Vorher waren das vier Zeichenaufrufe mit vier PNGs, von denen jedes
	 * bei anderer Fenstergroesse anders gedehnt wurde. Der Shader leitet
	 * alle vier aus derselben Abstandsfunktion ab, also sitzen sie immer
	 * zueinander passend.
	 *
	 * @return false, wenn der Shaderweg nicht geht - dann muss der
	 *         Aufrufer die vier Sprites einzeln zeichnen
	 */
	/**
	 * Glas mit echter Brechung - der Hintergrund wird am Rand verbogen.
	 *
	 * Nur fuer das grosse Panel ueber der Welt. Flaechen AUF dem Panel
	 * wuerden dieselbe Kopie brechen, also die Welt statt das Panel
	 * darunter - das saehe an der Panelkante falsch aus. Die bekommen
	 * weiter glassCard.
	 *
	 * @return false, wenn keine Bildschirmkopie da ist; dann faellt der
	 *         Aufrufer auf glassPanel zurueck
	 */
	public static boolean refractPanel(DrawContext ctx, int x, int y, int w, int h,
	                                   int argb) {
		// Eigene, duennere Toenung - siehe Theme.GLASS_TINT.
		return Sdf.glass(ctx, x, y, w, h, 9, Theme.GLASS_TINT);
	}

	public static boolean glassPanel(DrawContext ctx, int x, int y, int w, int h, int argb) {
		return Sdf.roundedRect(ctx, Sdf.Style.PANEL, x, y, w, h, 9, argb);
	}

	/**
	 * Karte oder Sidebar als EINE Glasflaeche.
	 *
	 * Ersetzt die Folge card + innerCard + bevelCard + rimCard. Die alte
	 * Folge hatte eine sichtbare Naht auf halber Hoehe: die Fase wurde in
	 * zwei Durchgaengen mit Beschnitt gezeichnet, oben hell, unten dunkel.
	 * Gemessen war das ein Sprung von 21 Helligkeitsstufen mitten in der
	 * Kante. Der Shader kennt die Richtung der Kante an jeder Stelle und
	 * braucht den Trick nicht.
	 *
	 * @return false, wenn der Shaderweg nicht geht
	 */
	public static boolean glassCard(DrawContext ctx, int x, int y, int w, int h, int argb) {
		return Sdf.roundedRect(ctx, Sdf.Style.CARD, x, y, w, h, 6, argb);
	}

	/** Grosse Flaeche, grosser Radius - nur die Fuellung, als Sprite. */
	public static void panel(DrawContext ctx, int x, int y, int w, int h, int argb) {
		sprite(ctx, LG, x, y, w, h, argb);
	}

	/** Mittlere Flaeche - Sidebar und Karten. */
	public static void card(DrawContext ctx, int x, int y, int w, int h, int argb) {
		sprite(ctx, MD, x, y, w, h, argb);
	}

	/** Kleine Flaeche - Zeilen, Hover, Pillen. */
	public static void row(DrawContext ctx, int x, int y, int w, int h, int argb) {
		sprite(ctx, SM, x, y, w, h, argb);
	}

	/**
	 * Lichtkante des Fensters. Oben hell, unten zurueckgenommen - das
	 * Gefaelle entsteht durch zwei Durchgaenge, nicht durch die Textur.
	 */
	public static void rimPanel(DrawContext ctx, int x, int y, int w, int h, int argb) {
		splitTone(ctx, RIM_LG, x, y, w, h, argb, dim(argb, 0.45F));
	}

	/** Alphawert eines ARGB abschwaechen. */
	private static int dim(int argb, float factor) {
		int a = Math.round(((argb >>> 24) & 0xFF) * factor);
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	/** Lichtkante einer Karte. */
	public static void rimCard(DrawContext ctx, int x, int y, int w, int h, int argb) {
		splitTone(ctx, RIM_MD, x, y, w, h, argb, dim(argb, 0.45F));
	}

	/**
	 * Schlagschatten UNTER einer Flaeche.
	 *
	 * Wird nach allen Seiten um spread ausgeweitet und zusaetzlich um
	 * offsetY nach unten geschoben - Licht faellt von oben, also liegt der
	 * Schatten unterhalb. Vor der Flaeche zeichnen, nicht danach.
	 */
	public static void shadowPanel(DrawContext ctx, int x, int y, int w, int h,
	                               int spread, int offsetY, int argb) {
		sprite(ctx, SHADOW_LG, x - spread, y - spread + offsetY,
			w + spread * 2, h + spread * 2, argb);
	}

	/** Schlagschatten unter einer Karte. */
	public static void shadowCard(DrawContext ctx, int x, int y, int w, int h,
	                              int spread, int offsetY, int argb) {
		sprite(ctx, SHADOW_SM, x - spread, y - spread + offsetY,
			w + spread * 2, h + spread * 2, argb);
	}

	// ------------------------------------------------------------------
	// Glas: Fase, Innenschatten, Glanz
	//
	// Diese drei erzeugen den Eindruck einer DICKEN Scheibe. Ohne sie liest
	// das Auge auch eine perfekt durchsichtige Flaeche als aufgeklebtes
	// Papier - Durchsicht allein macht noch kein Glas.
	//
	// Nach der Flaeche und VOR der aeusseren Lichtkante zeichnen.
	// ------------------------------------------------------------------

	/**
	 * Fase des Fensters: helle Bahn innen an der Oberkante, dunkle an der
	 * Unterkante. Genau dieses Gefaelle liest das Auge als Materialdicke.
	 */
	public static void bevelPanel(DrawContext ctx, int x, int y, int w, int h,
	                              int lightArgb, int darkArgb) {
		splitTone(ctx, BEVEL_LG, x, y, w, h, lightArgb, darkArgb);
	}

	/** Fase einer Karte. */
	public static void bevelCard(DrawContext ctx, int x, int y, int w, int h,
	                             int lightArgb, int darkArgb) {
		splitTone(ctx, BEVEL_MD, x, y, w, h, lightArgb, darkArgb);
	}

	/**
	 * Zeichnet dasselbe Sprite zweimal - obere Haelfte in der einen Farbe,
	 * untere in der anderen.
	 *
	 * WARUM NICHT EINE VERLAUFSTEXTUR:
	 * Minecraft KACHELT beim Nine-Slice die Randstreifen, es streckt sie
	 * nicht. Bei einer 36er Textur mit Rand 12 ist der linke Randstreifen
	 * 12 Pixel hoch und wiederholt sich ueber die gesamte Fensterhoehe.
	 * Eine Textur mit Verlauf von oben nach unten faengt bei jeder Kachel
	 * von vorn an - das ergab die sichtbaren Streifen am Fensterrand.
	 *
	 * Gleichmaessige Ringe lassen sich dagegen beliebig kacheln. Das
	 * Gefaelle entsteht hier, nicht in der Textur.
	 *
	 * Beschnitte verschachteln sich korrekt: ScissorStack.push verschneidet
	 * mit dem vorherigen Rechteck, der Kartenbeschnitt bleibt also erhalten.
	 */
	private static void splitTone(DrawContext ctx, Identifier id,
	                              int x, int y, int w, int h,
	                              int topArgb, int bottomArgb) {
		int mid = y + h / 2;
		ctx.enableScissor(x, y, x + w, mid);
		sprite(ctx, id, x, y, w, h, topArgb);
		ctx.disableScissor();

		ctx.enableScissor(x, mid, x + w, y + h);
		sprite(ctx, id, x, y, w, h, bottomArgb);
		ctx.disableScissor();
	}

	/** Dunkler Saum ganz innen - setzt die Flaeche vom Hintergrund ab. */
	public static void innerPanel(DrawContext ctx, int x, int y, int w, int h, int argb) {
		sprite(ctx, INNER_LG, x, y, w, h, argb);
	}

	public static void innerCard(DrawContext ctx, int x, int y, int w, int h, int argb) {
		sprite(ctx, INNER_MD, x, y, w, h, argb);
	}

	/**
	 * Beliebiges Sprite als Symbol, eingefaerbt. Fuer Lupe, Statuspunkte und
	 * Mod-Icons. Die betreffenden Sprites tragen "stretch" statt Nine-Slice,
	 * werden also als Ganzes skaliert - bei einem Symbol ist genau das
	 * richtig, ein gekachelter Rand waere Unsinn.
	 */
	public static void icon(DrawContext ctx, Identifier id, int x, int y, int size, int argb) {
		sprite(ctx, id, x, y, size, size, argb);
	}

	/** Duenner Umriss fuer Eingabefelder. */
	public static void rowOutline(DrawContext ctx, int x, int y, int w, int h, int argb) {
		sprite(ctx, SM, x, y, w, h, argb);
	}

	/** Diagonale Glanzbahn - eine Scheibe spiegelt, eine Farbflaeche nicht. */
	public static void glarePanel(DrawContext ctx, int x, int y, int w, int h, int argb) {
		sprite(ctx, GLARE_LG, x, y, w, h, argb);
	}

	public static void glareCard(DrawContext ctx, int x, int y, int w, int h, int argb) {
		sprite(ctx, GLARE_MD, x, y, w, h, argb);
	}
}
