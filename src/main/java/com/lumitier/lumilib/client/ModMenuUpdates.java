package com.lumitier.lumilib.client;

import com.terraformersmc.modmenu.ModMenu;
import com.terraformersmc.modmenu.gui.ModsScreen;
import com.terraformersmc.modmenu.util.mod.Mod;
import net.minecraft.client.gui.screen.Screen;

/**
 * Bruecke zu Mod Menus Update-Pruefung.
 *
 * WARUM EINE EIGENE KLASSE:
 * Mod Menu ist OPTIONAL. Steht ein Verweis darauf mitten in
 * LumiHubScreen, versucht die Java-Laufzeit beim ersten Oeffnen des Hubs,
 * die Klasse aufzuloesen - und stuerzt ab, wenn Mod Menu fehlt. Liegt der
 * Verweis dagegen in einer eigenen Klasse, wird diese erst geladen, wenn
 * sie tatsaechlich angefasst wird. Vorher fragt der Aufrufer ab, ob Mod
 * Menu ueberhaupt da ist.
 *
 * Deshalb: HIER stehen alle Mod-Menu-Verweise, und nur hier.
 *
 * GEGEN MOD MENU 17.0.1 FUER 1.21.11 GEPRUEFT:
 *     ModMenu.ROOT_MODS           Map<String, Mod>, oeffentlich
 *     Mod.hasUpdate()             Standardmethode im Interface
 *     Mod.getChildHasUpdate()     fuer Mods mit Unterpunkten
 *     new ModsScreen(Screen)      Konstruktor nimmt den Vorgaenger
 *
 * ROOT_MODS statt MODS: MODS enthaelt auch die Unterpunkte grosser
 * Sammelmods. Wer die mitzaehlt, meldet fuer Fabric API allein ein
 * Dutzend Updates. Mod Menu selbst zaehlt ebenfalls ueber die
 * Haupteintraege und nimmt Unterpunkte nur ueber getChildHasUpdate mit.
 */
final class ModMenuUpdates {

	private ModMenuUpdates() {}

	/** Anzahl der Mods mit verfuegbarem Update. */
	static int count() {
		int n = 0;
		for (Mod mod : ModMenu.ROOT_MODS.values()) {
			if (mod.hasUpdate() || mod.getChildHasUpdate()) {
				n++;
			}
		}
		return n;
	}

	/** Mod Menus eigener Bildschirm, mit dem Hub als Rueckweg. */
	static Screen screen(Screen parent) {
		return new ModsScreen(parent);
	}
}
