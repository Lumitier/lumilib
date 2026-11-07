package com.lumitier.lumilib.api;

import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * YACL-Bausteine.
 *
 * Diese vier Bauformen - Schalter, Gleitkomma-Regler, Ganzzahl-Regler,
 * Farbwaehler - standen bisher in jeder Mod wortgleich. Hier einmal.
 *
 * WAS HIER BEWUSST NICHT STEHT: der Aufbau eines Screens. Welche Tabs es
 * gibt, welche Gruppen, welche Option an welcher Stelle - das bleibt in
 * jeder Mod in ihrer eigenen Datei.
 *
 * Grund: Ein Fehler in einem Baustein faellt beim Kompilieren auf. Ein
 * Fehler in einem geteilten Layout macht fuenf Screens gleichzeitig
 * haesslich, und das merkt man erst im Spiel.
 *
 * Alle Methoden erwarten die Mod-ID und eine Options-Kennung und bauen daraus
 * die Uebersetzungsschluessel:
 *     &lt;modId&gt;.option.&lt;id&gt;        - Name
 *     &lt;modId&gt;.option.&lt;id&gt;.desc   - Beschreibung
 *
 * ACHTUNG - OEFFENTLICHE API: nur hinzufuegen, nie entfernen oder aendern.
 */
public final class Options {

	private Options() {}

	/** Uebersetzungsschluessel fuer den Namen einer Option. */
	public static Text key(String modId, String id) {
		return Text.translatable(modId + ".option." + id);
	}

	/** Uebersetzungsschluessel fuer die Beschreibung einer Option. */
	public static OptionDescription desc(String modId, String id) {
		return OptionDescription.createBuilder()
			.text(Text.translatable(modId + ".option." + id + ".desc"))
			.build();
	}

	/** Ja/Nein-Schalter, eingefaerbt. */
	public static Option<Boolean> toggle(String modId, String id, boolean def,
	                                     Supplier<Boolean> getter, Consumer<Boolean> setter) {
		return Option.<Boolean>createBuilder()
			.name(key(modId, id))
			.description(desc(modId, id))
			.binding(def, getter, setter)
			.controller(opt -> BooleanControllerBuilder.create(opt).yesNoFormatter().coloured(true))
			.build();
	}

	/** Gleitkomma-Regler. */
	public static Option<Float> slider(String modId, String id,
	                                   float def, float min, float max, float step,
	                                   Supplier<Float> getter, Consumer<Float> setter) {
		return Option.<Float>createBuilder()
			.name(key(modId, id))
			.description(desc(modId, id))
			.binding(def, getter, setter)
			.controller(opt -> FloatSliderControllerBuilder.create(opt).range(min, max).step(step))
			.build();
	}

	/** Ganzzahl-Regler. */
	public static Option<Integer> intSlider(String modId, String id,
	                                        int def, int min, int max, int step,
	                                        Supplier<Integer> getter, Consumer<Integer> setter) {
		return Option.<Integer>createBuilder()
			.name(key(modId, id))
			.description(desc(modId, id))
			.binding(def, getter, setter)
			.controller(opt -> IntegerSliderControllerBuilder.create(opt).range(min, max).step(step))
			.build();
	}

	/**
	 * Farbwaehler mit Hex-Feld und Alphakanal.
	 *
	 * Nach aussen wird mit ARGB-Integern gearbeitet, nicht mit java.awt.Color -
	 * die Mods halten ihre Farben ohnehin als int, und so steht die
	 * awt-Umwandlung nur an einer Stelle.
	 */
	public static Option<java.awt.Color> colorPicker(String modId, String id, int defArgb,
	                                                 Supplier<Integer> getter, Consumer<Integer> setter) {
		return Option.<java.awt.Color>createBuilder()
			.name(key(modId, id))
			.description(desc(modId, id))
			.binding(
				new java.awt.Color(defArgb, true),
				() -> new java.awt.Color(getter.get(), true),
				v -> setter.accept(v.getRGB()))
			.controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(true))
			.build();
	}
}
