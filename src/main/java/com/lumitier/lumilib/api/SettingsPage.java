package com.lumitier.lumilib.api;

import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Die Einstellungen einer Mod, wie der Hub sie ZEICHNET.
 *
 * WOZU, obwohl es YACL schon gibt:
 * YACL baut einen eigenen Bildschirm. Wer dort etwas umstellen will, verlaesst
 * den Hub, sieht eine fremde Oberflaeche und kommt ueber Escape zurueck. Das
 * ist ein Bruch mitten in der Benutzung. Hier wird stattdessen BESCHRIEBEN,
 * was es gibt - gezeichnet wird es vom Hub selbst, mit denselben Flaechen,
 * Schriften und Abstaenden wie alles andere.
 *
 * YACL bleibt als Rueckfallebene. Eine Mod, die nur einen YACL-Bildschirm
 * anmeldet, bekommt weiterhin ihren Knopf.
 *
 * SO SIEHT DIE BENUTZUNG AUS - mehr ist es nicht:
 *
 *     SettingsPage.builder("deinemod")
 *         .section("general")
 *             .toggle("show_hud", () -> cfg.showHud, v -> cfg.showHud = v)
 *             .toggle("play_sound", () -> cfg.sound, v -> cfg.sound = v)
 *         .section("advanced")
 *             .action("reset_all", () -> cfg.resetAll())
 *         .save(DeineMod::save)
 *         .build();
 *
 * DIE TEXTE STEHEN NICHT HIER, sondern in der Sprachdatei. Aus Mod-Kennung
 * und Options-Kennung werden die Schluessel gebaut:
 *
 *     &lt;modId&gt;.group.&lt;id&gt;        Ueberschrift eines Abschnitts
 *     &lt;modId&gt;.option.&lt;id&gt;       Beschriftung einer Zeile
 *     &lt;modId&gt;.option.&lt;id&gt;.desc  zweite Zeile darunter, FREIWILLIG
 *
 * Fehlt der .desc-Schluessel, bleibt die zweite Zeile weg und die Zeile wird
 * entsprechend flacher. Das ist genau dasselbe Muster wie in Options, damit
 * eine Mod ihre Sprachdateien nicht anfassen muss, wenn sie von YACL
 * hierher wechselt.
 *
 * ACHTUNG - OEFFENTLICHE API: nur hinzufuegen, nie entfernen oder aendern.
 */
public final class SettingsPage {

	private final List<Section> sections;
	private final @Nullable Runnable save;
	private final Map<Row, Reset> resets;
	private final @Nullable IntSupplier refresh;

	private SettingsPage(List<Section> sections, @Nullable Runnable save,
	                     Map<Row, Reset> resets, @Nullable IntSupplier refresh) {
		this.sections = List.copyOf(sections);
		this.save = save;
		this.resets = resets;
		this.refresh = refresh;
	}

	/**
	 * Stand, mit dem diese Seite gebaut wurde - oder 0 ohne refreshWhen.
	 * Der Hub vergleicht ihn in jedem Bild mit dem aktuellen Wert und baut
	 * die Seite neu, sobald er sich aendert. Seit LumiLib 1.3.2.
	 */
	public int refreshKey() {
		return refresh == null ? 0 : refresh.getAsInt();
	}

	public List<Section> sections() {
		return sections;
	}

	// ------------------------------------------------------------------
	// Zuruecksetzen
	//
	// WARUM NICHT ALS FELD IN Toggle, Slider UND Colour:
	// Das waere eine neue Komponente in drei Records gewesen - und damit ein
	// neuer kanonischer Konstruktor. Jede Mod, die gegen eine aeltere Fassung
	// gebaut wurde, haette beim Start einen NoSuchMethodError bekommen. Die
	// Standardwerte liegen deshalb NEBEN den Zeilen, in einer Zuordnung, und
	// die Records bleiben Byte fuer Byte, wie sie waren.
	//
	// IdentityHashMap, nicht HashMap: Records vergleichen sich ueber ihre
	// Inhalte. Zwei Schalter mit gleicher Beschriftung und gleichem Getter
	// waeren damit DERSELBE Schluessel, und der zweite wuerde den
	// Standardwert des ersten zurueckschreiben.
	// ------------------------------------------------------------------

	/** Standardwert einer Zeile: wie man ihn setzt, und ob er schon gilt. */
	private record Reset(Runnable apply, BooleanSupplier atDefault) {}

	/** Hat diese Zeile ueberhaupt einen Standardwert? */
	public boolean canReset(@Nullable Row row) {
		return row != null && resets.containsKey(row);
	}

	/** Gibt es irgendwo auf der Seite etwas zum Zuruecksetzen? */
	public boolean canResetAny() {
		return !resets.isEmpty();
	}

	/**
	 * Steht die Zeile schon auf ihrem Standardwert?
	 *
	 * Zeilen ohne Standardwert gelten als "steht schon richtig" - so muss
	 * der Hub nicht zwei Fragen stellen, um zu wissen, ob der Knopf
	 * abgeblendet gehoert.
	 */
	public boolean isDefault(@Nullable Row row) {
		Reset r = row == null ? null : resets.get(row);
		return r == null || r.atDefault().getAsBoolean();
	}

	/** Setzt die Zeile zurueck und speichert. @return true, wenn etwas geschah */
	public boolean reset(@Nullable Row row) {
		Reset r = row == null ? null : resets.get(row);
		if (r == null || r.atDefault().getAsBoolean()) {
			return false;
		}
		r.apply().run();
		saveNow();
		return true;
	}

	/** Alles auf Standard - einmal gespeichert, nicht pro Zeile. */
	public boolean resetAll() {
		boolean any = false;
		for (Reset r : resets.values()) {
			if (!r.atDefault().getAsBoolean()) {
				r.apply().run();
				any = true;
			}
		}
		if (any) {
			saveNow();
		}
		return any;
	}

	/**
	 * Wird nach JEDER Aenderung gerufen.
	 *
	 * Nicht erst beim Verlassen: Der Hub hat keinen "Fertig"-Knopf, und ein
	 * Absturz oder ein Alt+F4 wuerde die Aenderung sonst verschlucken. Die
	 * Mengen sind winzig, das Schreiben kostet nichts.
	 */
	public void saveNow() {
		if (save != null) {
			save.run();
		}
	}

	/** Ein Abschnitt mit Ueberschrift. */
	public record Section(Text title, List<Row> rows) {}

	/**
	 * Eine Zeile.
	 *
	 * BEWUSST KEIN sealed interface: Kaeme spaeter eine Bauform dazu, wuerde
	 * jedes vollstaendige switch in fremdem Code brechen. Der Hub prueft mit
	 * instanceof und zeichnet, was er kennt.
	 */
	public interface Row {
		Text label();

		@Nullable Text description();
	}

	/** Ja/Nein-Schalter. */
	public record Toggle(Text label, @Nullable Text description,
	                     BooleanSupplier getter, Consumer<Boolean> setter)
		implements Row {}

	/** Zeile, die beim Klick etwas tut. Kein Schalter, kein Wert. */
	public record Action(Text label, @Nullable Text description, Runnable onClick)
		implements Row {}

	/**
	 * Zahlenwert zwischen zwei Grenzen.
	 *
	 * ALLES ALS double, auch fuer Ganzzahlen: Sonst braeuchte es zwei fast
	 * gleiche Bauformen, zwei Zeichenwege und zwei Trefferpruefungen. Das
	 * Feld integer sagt nur, wie der Wert ANGEZEIGT und gerundet wird.
	 */
	public record Slider(Text label, @Nullable Text description,
	                     double min, double max, double step, boolean integer,
	                     DoubleSupplier getter, DoubleConsumer setter)
		implements Row {

		/** Wert auf Schrittweite und Grenzen gebracht. */
		public double clamp(double v) {
			double c = Math.max(min, Math.min(max, v));
			if (step > 0.0) {
				c = min + Math.round((c - min) / step) * step;
				c = Math.max(min, Math.min(max, c));
			}
			return integer ? Math.round(c) : c;
		}

		/** Anteil von 0 bis 1 - fuer die Lage des Knopfes. */
		public double fraction() {
			return max <= min ? 0.0
				: Math.max(0.0, Math.min(1.0, (getter.getAsDouble() - min) / (max - min)));
		}
	}

	/**
	 * Farbe als ARGB-Ganzzahl.
	 *
	 * KEIN EIGENES FENSTER: Die Zeile klappt beim Klick auf und zeigt
	 * darunter vier gewoehnliche Regler fuer Rot, Gruen, Blau und
	 * Deckkraft. Ein Farbrad waere huebscher, braeuchte aber eine zweite
	 * Ebene ueber der Seite - und damit ein zweites Schnittfenster, eine
	 * zweite Trefferpruefung und eine zweite Art, etwas zu schliessen.
	 * Vier Regler koennen alles, was das Rad kann, und benutzen Code,
	 * der ohnehin schon da ist.
	 */
	public record Colour(Text label, @Nullable Text description,
	                     IntSupplier getter, IntConsumer setter)
		implements Row {

		/** Die vier Kanaele als gewoehnliche Regler. */
		public List<Slider> channels() {
			return List.of(
				channel("R", 16), channel("G", 8), channel("B", 0), channel("A", 24));
		}

		/**
		 * Ein Kanal.
		 *
		 * Gelesen und geschrieben wird IMMER die ganze Farbe - so kann
		 * kein Kanal einen veralteten Stand der anderen drei
		 * zurueckschreiben.
		 */
		private Slider channel(String name, int shift) {
			return new Slider(Text.literal(name), null, 0.0, 255.0, 1.0, true,
				() -> (getter.getAsInt() >> shift) & 0xFF,
				v -> {
					int whole = getter.getAsInt();
					int part = ((int) Math.round(v)) & 0xFF;
					setter.accept((whole & ~(0xFF << shift)) | (part << shift));
				});
		}
	}

	/**
	 * Reiner Text, kein Bedienelement.
	 *
	 * Fuer Hinweise, die nur unter einer Bedingung auftauchen - etwa
	 * "Minecrafts eigener Schatten-Schalter ist aus, die Mod tut gerade
	 * nichts". Ohne so eine Zeile sieht das nach einem Fehler aus.
	 */
	public record Note(Text label, @Nullable Text description, boolean warning)
		implements Row {}

	// ------------------------------------------------------------------
	// Aufbau
	// ------------------------------------------------------------------

	public static Builder builder(String modId) {
		return new Builder(modId);
	}

	public static final class Builder {
		private final String modId;
		private final List<Section> sections = new ArrayList<>();
		private final Map<Row, Reset> resets = new IdentityHashMap<>();
		private Text currentTitle;
		private List<Row> currentRows;
		private Runnable save;
		private IntSupplier refresh;

		private Builder(String modId) {
			this.modId = modId;
		}

		/** Beginnt einen Abschnitt. Der vorige wird abgeschlossen. */
		public Builder section(String id) {
			flush();
			this.currentTitle = Text.translatable(modId + ".group." + id);
			this.currentRows = new ArrayList<>();
			return this;
		}

		public Builder toggle(String id, BooleanSupplier getter, Consumer<Boolean> setter) {
			return row(new Toggle(label(id), description(id), getter, setter));
		}

		public Builder action(String id, Runnable onClick) {
			return row(new Action(label(id), description(id), onClick));
		}

		/** Kommazahl-Regler. Getter und Setter bleiben bei Float. */
		public Builder slider(String id, float min, float max, float step,
		                      Supplier<Float> getter, Consumer<Float> setter) {
			return row(new Slider(label(id), description(id), min, max, step, false,
				() -> getter.get(), v -> setter.accept((float) v)));
		}

		/** Ganzzahl-Regler. Getter und Setter bleiben bei Integer. */
		public Builder intSlider(String id, int min, int max, int step,
		                         Supplier<Integer> getter, Consumer<Integer> setter) {
			return row(new Slider(label(id), description(id), min, max, step, true,
				() -> getter.get(), v -> setter.accept((int) Math.round(v))));
		}

		/** Farbwaehler. Getter und Setter bleiben bei ARGB-Integern. */
		public Builder color(String id, Supplier<Integer> getter, Consumer<Integer> setter) {
			return row(new Colour(label(id), description(id),
				() -> getter.get(), v -> setter.accept(v)));
		}

		// --------------------------------------------------------------
		// Dieselben Bauformen MIT Standardwert.
		//
		// Die Kurzformen ohne Standardwert bleiben bestehen und werden
		// nicht veraendert - eine Mod, die gegen die aeltere Fassung
		// gebaut wurde, laeuft unveraendert weiter. Sie bekommt dann
		// eben keine Zuruecksetzen-Knoepfe, bis sie neu gebaut wird.
		// --------------------------------------------------------------

		/** Schalter mit Standardwert. */
		public Builder toggle(String id, BooleanSupplier getter,
		                      Consumer<Boolean> setter, boolean standard) {
			Toggle t = new Toggle(label(id), description(id), getter, setter);
			resets.put(t, new Reset(
				() -> setter.accept(standard),
				() -> getter.getAsBoolean() == standard));
			return row(t);
		}

		/**
		 * Kommazahl-Regler mit Standardwert.
		 *
		 * VERGLICHEN WIRD MIT TOLERANZ, nicht auf Gleichheit: Ein Float,
		 * der durch double und zurueck gelaufen ist, trifft den
		 * Standardwert selten exakt. Ohne Toleranz bliebe der Knopf
		 * dauerhaft aktiv, obwohl der Regler sichtbar am Standard steht.
		 * Eine halbe Schrittweite ist die Grenze, denn naeher kann man
		 * mit dem Regler gar nicht herankommen.
		 */
		public Builder slider(String id, float min, float max, float step,
		                      Supplier<Float> getter, Consumer<Float> setter,
		                      float standard) {
			Slider s = new Slider(label(id), description(id), min, max, step, false,
				() -> getter.get(), v -> setter.accept((float) v));
			float tol = Math.max(step, 1.0e-4F) / 2.0F;
			resets.put(s, new Reset(
				() -> setter.accept(standard),
				() -> Math.abs(getter.get() - standard) <= tol));
			return row(s);
		}

		/** Ganzzahl-Regler mit Standardwert. */
		public Builder intSlider(String id, int min, int max, int step,
		                         Supplier<Integer> getter, Consumer<Integer> setter,
		                         int standard) {
			Slider s = new Slider(label(id), description(id), min, max, step, true,
				() -> getter.get(), v -> setter.accept((int) Math.round(v)));
			resets.put(s, new Reset(
				() -> setter.accept(standard),
				() -> getter.get() == standard));
			return row(s);
		}

		/** Farbwaehler mit Standardwert. */
		public Builder color(String id, Supplier<Integer> getter,
		                     Consumer<Integer> setter, int standard) {
			Colour c = new Colour(label(id), description(id),
				() -> getter.get(), v -> setter.accept(v));
			resets.put(c, new Reset(
				() -> setter.accept(standard),
				() -> getter.get() == standard));
			return row(c);
		}

		/**
		 * Selbstgebaute Zeile mit Standardwert.
		 *
		 * Fuer alles, was die Kurzformen nicht abdecken. atDefault muss
		 * sagen, ob der Standard GERADE gilt - davon haengt ab, ob der
		 * Knopf abgeblendet ist.
		 */
		public Builder row(Row row, Runnable toDefault, BooleanSupplier atDefault) {
			resets.put(row, new Reset(toDefault, atDefault));
			return row(row);
		}

		/** Hinweiszeile. */
		public Builder note(String id) {
			return row(new Note(label(id), description(id), false));
		}

		/** Hinweiszeile in Warnfarbe. */
		public Builder warning(String id) {
			return row(new Note(label(id), description(id), true));
		}

		/** Eine selbstgebaute Zeile, fuer alles, was die Kurzformen nicht abdecken. */
		public Builder row(Row row) {
			if (currentRows == null) {
				// Ohne Abschnitt waere die Zeile heimatlos. Lieber ein
				// namenloser Abschnitt als eine verschluckte Zeile.
				section("");
			}
			currentRows.add(row);
			return this;
		}

		public Builder save(Runnable save) {
			this.save = save;
			return this;
		}

		/**
		 * Seite neu bauen, sobald sich dieser Wert aendert.
		 *
		 * Fuer Seiten, deren AUFBAU vom Zustand abhaengt - etwa eine
		 * Anmeldung, die von "Verbinden" ueber "Code eingeben" zu
		 * "Verbunden als ..." wechselt. Schalter und Regler brauchen das
		 * nicht, die lesen ihren Wert ohnehin in jedem Bild. Der Wert wird
		 * in jedem Bild abgefragt: billig halten, am besten ein Zaehler.
		 * Seit LumiLib 1.3.2.
		 */
		public Builder refreshWhen(IntSupplier version) {
			this.refresh = version;
			return this;
		}

		public SettingsPage build() {
			flush();
			return new SettingsPage(sections, save, resets, refresh);
		}

		private void flush() {
			if (currentRows != null && !currentRows.isEmpty()) {
				sections.add(new Section(currentTitle, List.copyOf(currentRows)));
			}
			currentRows = null;
		}

		private Text label(String id) {
			return Text.translatable(modId + ".option." + id);
		}

		/**
		 * Zweite Zeile, oder null wenn es keine gibt.
		 *
		 * WIE DAS FEHLEN ERKANNT WIRD: Minecraft gibt bei einem unbekannten
		 * Schluessel den Schluessel selbst zurueck. Kommt also genau der Text
		 * heraus, den wir hineingegeben haben, existiert die Uebersetzung
		 * nicht. Das braucht keine zusaetzliche Abfrage und gilt seit jeher.
		 */
		private @Nullable Text description(String id) {
			String key = modId + ".option." + id + ".desc";
			Text t = Text.translatable(key);
			return key.equals(t.getString()) ? null : t;
		}
	}
}
