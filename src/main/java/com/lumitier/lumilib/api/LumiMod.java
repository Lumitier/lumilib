package com.lumitier.lumilib.api;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Ein bei LumiLib angemeldeter Mod-Eintrag.
 *
 * Das ist alles, was der Hub-Screen braucht, um eine Karte zu zeichnen und
 * beim Klick in die Einstellungen der Mod zu springen.
 *
 * ACHTUNG - OEFFENTLICHE API: Dieser Record ist Teil der Schnittstelle, die
 * andere Mods benutzen. Felder duerfen HINZUGEFUEGT werden (dann mit einem
 * zusaetzlichen Konstruktor, der die alte Signatur weiterleitet), aber nie
 * entfernt, umbenannt oder umsortiert. Sonst bricht jede Mod, die gegen eine
 * aeltere LumiLib gebaut wurde - und zwar beim Nutzer, nicht bei uns.
 *
 * @param id            eindeutige Kennung, identisch zur Mod-ID in der
 *                      fabric.mod.json (z. B. "shadowtweaks")
 * @param name          Anzeigename ("Shadow Tweaks")
 * @param version       Versionstext, wie er auf der Karte steht
 * @param description   ein Satz, was die Mod tut
 * @param icon          Textur fuer das Kaertchen-Icon, darf null sein -
 *                      dann zeichnet der Hub einen Platzhalter
 * @param settings      beschreibt die Einstellungen, die der Hub SELBST
 *                      zeichnet - der bevorzugte Weg. Darf null sein.
 * @param screenFactory baut den Einstellungs-Screen der Mod; bekommt den
 *                      Hub als Eltern-Screen, damit Escape zurueckfuehrt.
 *                      Darf null sein fuer eine Mod ohne Einstellungen.
 */
public record LumiMod(
	String id,
	Text name,
	String version,
	Text description,
	Identifier icon,
	Function<Screen, Screen> screenFactory,
	Supplier<SettingsPage> settings
) {
	/**
	 * Die alte Signatur mit sechs Werten.
	 *
	 * MUSS BLEIBEN: Jede Mod, die gegen eine aeltere LumiLib gebaut wurde,
	 * ruft genau diesen Konstruktor. Faellt er weg, bricht sie beim Nutzer
	 * mit einem NoSuchMethodError - und zwar beim Start, nicht beim Bauen.
	 */
	public LumiMod(String id, Text name, String version, Text description,
	               Identifier icon, Function<Screen, Screen> screenFactory) {
		this(id, name, version, description, icon, screenFactory, null);
	}

	/**
	 * Zeichnet der Hub die Einstellungen SELBST?
	 *
	 * Das ist der bessere Weg: Es bleibt alles im Hub, in derselben
	 * Oberflaeche. Ein YACL-Bildschirm reisst den Nutzer heraus.
	 */
	public boolean hasInlineSettings() {
		return settings != null;
	}

	/** Hat diese Mod ueberhaupt Einstellungen - egal auf welchem Weg? */
	public boolean hasSettings() {
		return screenFactory != null || settings != null;
	}
}
