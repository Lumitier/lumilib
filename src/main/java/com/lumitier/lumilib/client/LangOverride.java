package com.lumitier.lumilib.client;

import com.lumitier.lumilib.LumiLib;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Sprache NUR fuer LumiLib und die Mods, die darauf aufbauen - der Rest
 * des Spiels bleibt in der Sprache, die in Minecraft eingestellt ist.
 *
 * WIE: Beim Laden der Sprachdateien (TranslationStorageMixin) liest diese
 * Klasse fuer jede betroffene Mod deren eigene Datei in der gewaehlten
 * Sprache - erst Englisch als Grundlage, darueber die gewaehlte. Der
 * Mixin schaut bei jeder Uebersetzung zuerst hier nach.
 *
 * WELCHE MODS: LumiLib selbst und jede Mod, die LumiLib als Abhaengigkeit
 * nennt (depends, recommends oder suggests). So gilt die Einstellung fuer
 * Resource Pack Studio, die Tweaks-Mods und auch fremde Mods, die den Hub
 * nutzen - ohne dass hier eine Liste gepflegt werden muss. Gelesen werden
 * nur deren eigene Dateien (assets/<mod-id>/lang/), also nur ihre Texte.
 *
 * "game" ist der Standard: Dann ist die Tabelle leer und es aendert sich
 * nichts.
 */
public final class LangOverride {

	private LangOverride() {}

	/** Die Werte der Einstellung, in der Reihenfolge der Auswahl. */
	public static final String[] VALUES = {"game", "en_us", "de_de"};

	/** Ersetzt bei jedem Neuladen als Ganzes - nie veraendert, also ohne Sperre lesbar. */
	private static volatile Map<String, String> table = Map.of();

	/** Uebersetzung aus der gewaehlten Sprache, oder null, wenn diese Mods den Schluessel nicht haben. */
	public static String get(String key) {
		Map<String, String> t = table;
		return t.isEmpty() ? null : t.get(key);
	}

	/** Index der aktuellen Einstellung in VALUES. */
	public static int index() {
		String v = LumiLib.cfg.language;
		for (int i = 0; i < VALUES.length; i++) {
			if (VALUES[i].equals(v)) {
				return i;
			}
		}
		return 0;
	}

	/** Neue Einstellung setzen und sofort anwenden - ohne Neustart. */
	public static void set(int index) {
		LumiLib.cfg.language = VALUES[index < 0 || index >= VALUES.length ? 0 : index];
		MinecraftClient mc = MinecraftClient.getInstance();
		// Minecraft merkt sich uebersetzte Texte, bis die Sprache neu
		// geladen wird. Dieses Neuladen ruft rebuild() ueber den Mixin
		// auf - danach zeigt jeder Text die neue Sprache.
		mc.getLanguageManager().reload(mc.getResourceManager());
	}

	/** Vom Mixin gerufen, jedes Mal wenn Minecraft seine Sprachdateien laedt. */
	public static void rebuild(ResourceManager rm) {
		String lang = LumiLib.cfg.language;
		if (lang == null || lang.equals("game")) {
			table = Map.of();
			return;
		}
		Map<String, String> out = new HashMap<>();
		for (String ns : namespaces()) {
			read(rm, ns, "en_us", out);
			if (!lang.equals("en_us")) {
				read(rm, ns, lang, out);
			}
		}
		table = Map.copyOf(out);
	}

	private static void read(ResourceManager rm, String ns, String code, Map<String, String> out) {
		Identifier id = Identifier.tryParse(ns + ":lang/" + code + ".json");
		if (id == null) {
			return;
		}
		// Alle Packs in Reihenfolge - ein Resource Pack darf die Texte
		// einer Mod ueberschreiben, genau wie sonst auch.
		for (Resource r : rm.getAllResources(id)) {
			try (InputStream in = r.getInputStream()) {
				Language.load(in, out::put);
			} catch (Exception e) {
				LumiLib.LOGGER.warn("[LumiLib] Could not read {} from {}", id, r.getPackId(), e);
			}
		}
	}

	private static Set<String> namespaces() {
		Set<String> out = new LinkedHashSet<>();
		out.add(LumiLib.MOD_ID);
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			for (ModDependency d : mod.getMetadata().getDependencies()) {
				if (d.getModId().equals(LumiLib.MOD_ID) && d.getKind() != ModDependency.Kind.BREAKS
						&& d.getKind() != ModDependency.Kind.CONFLICTS) {
					out.add(mod.getMetadata().getId());
					break;
				}
			}
		}
		return out;
	}
}
