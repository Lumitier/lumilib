package com.lumitier.lumilib.api;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Verzeichnis der bekannten Mods.
 *
 * WOZU:
 * Eine Mod, die nicht installiert ist, kann sich nicht anmelden - ihr
 * Startcode laeuft ja nie. Ohne dieses Verzeichnis taucht sie im Hub
 * also gar nicht auf. Hier stehen die Mods, die der Hub auch dann zeigen
 * soll, wenn sie fehlen: ausgegraut, mit Hinweis und Link.
 *
 * DREI ZUSTAENDE, die der Hub unterscheidet:
 *   angemeldet              - laeuft und ist an LumiLib angebunden
 *   geladen, nicht angemeldet - installiert, aber eine Fassung ohne
 *                             LumiLib-Anbindung. NICHT als "fehlt"
 *                             anzeigen, das waere schlicht falsch.
 *   nicht geladen           - fehlt wirklich
 *
 * ZUM LINK:
 * Steht dort null, zeigt der Hub nur den Hinweis und keinen Knopf. Ein
 * geratener Link waere schlimmer als keiner - er fuehrt dann ins Leere
 * oder, schlimmer, auf eine fremde Seite.
 */
public final class LumiCatalog {

	private LumiCatalog() {}

	/**
	 * Ein Eintrag im Verzeichnis.
	 *
	 * @param modId    Kennung wie im Loader - MUSS stimmen, sonst gilt
	 *                 eine installierte Mod faelschlich als fehlend
	 * @param name     angezeigter Name
	 * @param modrinth vollstaendige Adresse, oder null wenn unbekannt
	 */
	public record Entry(String modId, String name, @Nullable String modrinth) {}

	/**
	 * Die bekannten Mods.
	 *
	 * ACHTUNG beim Ergaenzen: Die Kennung muss exakt der Mod-ID aus deren
	 * fabric.mod.json entsprechen. Ein Tippfehler zeigt eine installierte
	 * Mod als "nicht installiert" an.
	 */
	// Links fuehren auf die eigene Website, nicht auf Modrinth: Dort steht
	// jede Mod mit Anforderungen und Download, auch solange sie auf
	// Modrinth noch nicht oeffentlich ist.
	public static final List<Entry> KNOWN = List.of(
		// Zuerst das Fundament: LumiLib selbst und das Gegenstueck, mit
		// dem ein Server einzelne Mods abschalten kann. Von beiden haengt
		// ab, ob der Rest ueberhaupt laeuft.
		new Entry("lumilib", "LumiLib",
			"https://lumitier.dev/mods/lumilib"),
		new Entry("lumilibcontrol", "LumiLibControl",
			"https://lumitier.dev/mods/lumilibcontrol"),

		// Dann die Werkzeuge.
		new Entry("resourcepackstudio", "Resource Pack Studio",
			"https://lumitier.dev/mods/resourcepackstudio"),
		new Entry("ipaddresshider", "IP Address Hider",
			"https://lumitier.dev/mods/ipaddresshider"),

		// Dann die Mods, die am Aussehen des Spiels drehen.
		new Entry("perpetualsnow", "Perpetual Snow",
			"https://lumitier.dev/mods/perpetualsnow"),
		new Entry("shadowtweaks", "Shadow Tweaks",
			"https://lumitier.dev/mods/shadowtweaks"),
		new Entry("itemdroptweaks", "Item Drop Tweaks",
			"https://lumitier.dev/mods/itemdroptweaks"),
		new Entry("xporbtweaks", "Experience Orb Tweaks",
			"https://lumitier.dev/mods/xporbtweaks")
	);

	/**
	 * Platz einer Mod in der Liste, oder eine grosse Zahl fuer Fremde.
	 *
	 * DIE REIHENFOLGE OBEN IST DIE ANZEIGEREIHENFOLGE. Alphabetisch zu
	 * sortieren waere einfacher gewesen, haette aber LumiLib mitten
	 * hinein gestellt - und die Bibliothek gehoert nach oben, weil von
	 * ihr alles andere abhaengt.
	 */
	public static int order(String modId) {
		for (int i = 0; i < KNOWN.size(); i++) {
			if (KNOWN.get(i).modId().equals(modId)) {
				return i;
			}
		}
		return Integer.MAX_VALUE;
	}
}
