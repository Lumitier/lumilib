package com.lumitier.lumilib.client;

import net.minecraft.util.Identifier;

/**
 * Das Logo des Mod-Laders.
 *
 * Nur noch eine Vorauswahl: Das Laden uebernimmt ModIcons, das ohnehin
 * jedes installierte Mod-Icon holen kann. Vorher stand hier eine zweite,
 * fast gleiche Ladefunktion - zwei Stellen, die dasselbe tun, gehen
 * irgendwann auseinander.
 *
 * Fabrics Logo wird NICHT mitgeliefert, sondern aus der installierten
 * Fassung gelesen. Es ist deren Markenzeichen; anzeigen ist etwas anderes
 * als weitergeben.
 */
final class LoaderIcon {

	private LoaderIcon() {}

	/** Kandidaten in der Reihenfolge, in der sie am besten passen. */
	private static final String[] CANDIDATES = { "fabricloader", "fabric-api", "fabric" };

	private static Identifier cached;
	private static boolean tried;

	static Identifier get() {
		if (tried) {
			return cached;
		}
		tried = true;
		for (String id : CANDIDATES) {
			cached = ModIcons.get(id);
			if (cached != null) {
				break;
			}
		}
		return cached;
	}
}
