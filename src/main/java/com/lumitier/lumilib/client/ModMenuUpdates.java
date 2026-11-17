package com.lumitier.lumilib.client;

import com.terraformersmc.modmenu.ModMenu;
import com.terraformersmc.modmenu.api.UpdateChannel;
import com.terraformersmc.modmenu.api.UpdateInfo;
import com.terraformersmc.modmenu.gui.ModsScreen;
import com.terraformersmc.modmenu.util.mod.Mod;
import com.terraformersmc.modmenu.util.mod.ModrinthUpdateInfo;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;

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
 *     Mod.getUpdateInfo()         UpdateInfo, null solange nicht geprueft
 *     UpdateInfo.getDownloadLink(), getUpdateChannel()
 *     ModrinthUpdateInfo.versionNumber()   die neue Versionsnummer -
 *                                 nur bei Updates von Modrinth, darum
 *                                 instanceof statt Annahme
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

	/**
	 * Eine Zeile der Update-Seite.
	 *
	 * @param latest neue Version, oder "" wenn Mod Menu sie nicht nennt
	 *               (etwa bei Unterpunkten der Fabric API)
	 * @param channel "RELEASE", "BETA" oder "ALPHA"
	 * @param link    Download-Seite, oder "" wenn keine bekannt
	 */
	record Item(String id, String name, String installed, String latest,
	            String channel, String link) {}

	/**
	 * Alle Mods mit Update, Lumitier-Mods zuerst, dann alphabetisch.
	 *
	 * Neu gebaut bei jedem Aufruf - der Hub ruft das hoechstens alle zwei
	 * Sekunden auf, nicht in jedem Bild.
	 */
	static List<Item> list() {
		List<Item> out = new ArrayList<>();
		for (Mod mod : ModMenu.ROOT_MODS.values()) {
			if (!mod.hasUpdate() && !mod.getChildHasUpdate()) {
				continue;
			}
			UpdateInfo info = mod.getUpdateInfo();
			String latest = info instanceof ModrinthUpdateInfo m && m.versionNumber() != null
				? m.versionNumber() : "";
			UpdateChannel ch = info == null ? null : info.getUpdateChannel();
			String link = info == null || info.getDownloadLink() == null ? "" : info.getDownloadLink();
			out.add(new Item(mod.getId(), mod.getTranslatedName(), mod.getVersion(), latest,
				ch == null ? "RELEASE" : ch.name(), link));
		}
		out.sort((a, b) -> {
			boolean la = isLumitier(a.id());
			boolean lb = isLumitier(b.id());
			if (la != lb) {
				return la ? -1 : 1;
			}
			return a.name().compareToIgnoreCase(b.name());
		});
		return out;
	}

	private static boolean isLumitier(String id) {
		for (com.lumitier.lumilib.api.LumiCatalog.Entry e : com.lumitier.lumilib.api.LumiCatalog.KNOWN) {
			if (e.modId().equals(id)) {
				return true;
			}
		}
		return "lumilib".equals(id);
	}

	/** Mod Menus eigener Bildschirm, mit dem Hub als Rueckweg. */
	static Screen screen(Screen parent) {
		return new ModsScreen(parent);
	}
}
