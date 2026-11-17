package com.lumitier.lumilib.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Einstiegspunkt fuer Mods, die im LumiLib-Hub erscheinen wollen.
 *
 * Benutzung im Client-Entrypoint der jeweiligen Mod:
 *
 * <pre>
 * LumiLibApi.register(new LumiMod(
 *     "shadowtweaks",
 *     Text.translatable("shadowtweaks.title"),
 *     "1.1.0",
 *     Text.translatable("shadowtweaks.summary"),
 *     Identifier.of("shadowtweaks", "icon.png"),
 *     ShadowTweaksScreen::create));
 * </pre>
 *
 * Eine Mod, die sich NICHT anmeldet, taucht im Hub nicht auf. Das ist Absicht:
 * der Hub listet, was sich anmeldet, nicht alles Installierte.
 *
 * ACHTUNG - OEFFENTLICHE API: Methoden duerfen hinzugefuegt, aber nie entfernt
 * oder in ihrer Signatur veraendert werden.
 */
public final class LumiLibApi {

	private LumiLibApi() {}

	/**
	 * Reihenfolge der Anmeldung bleibt erhalten; sortiert wird erst beim
	 * Ausliefern. LinkedHashMap statt List, damit eine doppelte Anmeldung
	 * (etwa nach einem Reload) den alten Eintrag ersetzt statt ihn zu
	 * verdoppeln.
	 */
	private static final Map<String, LumiMod> MODS = new LinkedHashMap<>();

	/**
	 * Meldet eine Mod beim Hub an. Mehrfache Anmeldung derselben ID
	 * ueberschreibt den vorherigen Eintrag.
	 *
	 * @throws IllegalArgumentException wenn mod oder dessen ID null ist
	 */
	public static synchronized void register(LumiMod mod) {
		if (mod == null || mod.id() == null || mod.id().isBlank()) {
			throw new IllegalArgumentException("LumiMod und dessen id duerfen nicht leer sein");
		}
		MODS.put(mod.id(), mod);
		// Eine Zeile pro Anmeldung, dauerhaft. Sie kostet nichts und
		// beantwortet beim naechsten Mal sofort die Frage, die sonst
		// nur durch Raten zu klaeren ist: Ist die Mod ueberhaupt
		// angekommen, und bringt sie eine eigene Seite mit?
		// AUF DEBUG, nicht info: Das ist eine Diagnosezeile fuer die
		// Entwicklung. In einer veroeffentlichten Fassung schreibt sie
		// pro Mod eine Zeile in jedes Log, die niemanden interessiert,
		// bis etwas kaputt ist - und dann holt man sie mit -Dlog.
		com.lumitier.lumilib.LumiLib.LOGGER.debug(
			"[LumiLib] registered: " + mod.id()
			+ " | own page: " + (mod.settings() != null)
			+ " | YACL: " + (mod.screenFactory() != null));
	}

	/** Alle angemeldeten Mods, alphabetisch nach Anzeigename. */
	public static synchronized List<LumiMod> mods() {
		List<LumiMod> out = new ArrayList<>(MODS.values());
		out.sort(Comparator.comparing(m -> m.name().getString(), String.CASE_INSENSITIVE_ORDER));
		return Collections.unmodifiableList(out);
	}

	/** Ist eine Mod mit dieser ID angemeldet? */
	public static synchronized boolean isRegistered(String id) {
		return MODS.containsKey(id);
	}

	// ------------------------------------------------------------------
	// Server-Regeln (LumiLibControlPlugin)
	//
	// Ein Server mit dem Plugin kann einzelne Mods fuer die Dauer der
	// Verbindung abschalten. Die Einstellungen des Nutzers bleiben dabei
	// unangetastet - die Mod soll nur so lange so tun, als waere sie aus.
	// Ohne das Plugin auf dem Server liefert alles hier "erlaubt".
	//
	// Seit LumiLib 1.3.0. Eine Mod, die das benutzt, muss darauf achten,
	// dass LumiLib optional ist: Aufrufe nur hinter einem
	// FabricLoader.isModLoaded("lumilib").
	// ------------------------------------------------------------------

	/**
	 * Hat der aktuelle Server diese Mod abgeschaltet?
	 *
	 * Billig genug fuer jedes Bild. Ausserhalb eines Servers immer false.
	 */
	public static boolean isDisabledByServer(String modId) {
		return com.lumitier.lumilib.client.ServerRules.disabled(modId);
	}

	/** Laeuft auf dem aktuellen Server LumiLibControlPlugin? */
	public static boolean isServerControlled() {
		return com.lumitier.lumilib.client.ServerRules.controlled();
	}

	/**
	 * Alle Regeln des aktuellen Servers: Mod-ID zu erlaubt. Leer ausserhalb
	 * eines Servers mit dem Plugin. Fuer Verwaltungswerkzeuge wie
	 * LumiLibControl - eine normale Mod braucht nur isDisabledByServer.
	 */
	public static java.util.Map<String, Boolean> serverRules() {
		return com.lumitier.lumilib.client.ServerRules.rules();
	}

	/** Darf der Spieler auf diesem Server die Regeln aendern? */
	public static boolean canControlServer() {
		return com.lumitier.lumilib.client.ServerRules.admin();
	}

	/**
	 * Wird aufgerufen, sobald sich die Regeln aendern: neue Regeln vom
	 * Server, Betreten eines Servers, Verlassen. Laeuft auf dem
	 * Render-Thread.
	 */
	/**
	 * Oeffnet den Item-Waehler von LumiLib.
	 *
	 * WARUM HIER UND NICHT DIREKT ueber die Klasse im client-Paket:
	 * Eine Mod, die LumiLib benutzt, soll NUR gegen das api-Paket bauen.
	 * Das ist die Grenze, auf die sie sich verlassen kann - alles andere
	 * darf sich jederzeit aendern. Beim ersten Versuch stand in Item Drop
	 * Tweaks ein Import auf com.lumitier.lumilib.client.ItemPicker, und
	 * damit haette jede Umbenennung dort fremde Mods zerbrochen.
	 *
	 * @param parent  wohin es nach Auswahl oder Abbruch zurueckgeht;
	 *                null nimmt das gerade offene Fenster
	 * @param onPick  bekommt das gewaehlte Item
	 * @param blocked welche Items blass und unantastbar sind; null heisst
	 *                keine Sperre
	 */
	public static void openItemPicker(net.minecraft.client.gui.screen.Screen parent,
	                                  java.util.function.Consumer<net.minecraft.item.Item> onPick,
	                                  java.util.function.Predicate<net.minecraft.item.Item> blocked) {
		net.minecraft.client.gui.screen.Screen from = parent != null
			? parent
			: net.minecraft.client.MinecraftClient.getInstance().currentScreen;
		com.lumitier.lumilib.client.ItemPicker.open(from, onPick,
			blocked == null ? item -> false : blocked);
	}

	/**
	 * Oeffnet den Verzauberungswaehler von LumiLib.
	 *
	 * Verzauberungen sind datengesteuert und haengen an der geladenen
	 * Welt. Im Hauptmenue zeigt das Fenster deshalb nur einen Hinweis
	 * statt einer Liste - das ist kein Fehler, sondern der normale Fall.
	 *
	 * @param parent  wohin es zurueckgeht; null nimmt das offene Fenster
	 * @param onPick  bekommt Kennung und Stufe
	 * @param blocked welche Verzauberungen schon vergeben sind; null
	 *                heisst keine Sperre
	 */
	public static void openEnchantPicker(net.minecraft.client.gui.screen.Screen parent,
	                                     java.util.function.BiConsumer<net.minecraft.util.Identifier, Integer> onPick,
	                                     java.util.function.Predicate<net.minecraft.util.Identifier> blocked) {
		net.minecraft.client.gui.screen.Screen from = parent != null
			? parent
			: net.minecraft.client.MinecraftClient.getInstance().currentScreen;
		com.lumitier.lumilib.client.EnchantPicker.open(from, onPick, blocked);
	}

	/**
	 * Fragt nur die Stufe einer bereits feststehenden Verzauberung ab.
	 *
	 * WOFUER: Eine gesetzte Verzauberung aendern. Die Liste dazwischen
	 * waere ein Umweg - die Verzauberung steht ja schon fest.
	 *
	 * Erlaubt sind 1 bis 255. Das ist nicht ausgedacht, sondern die
	 * Grenze des Spiels: ItemEnchantmentsComponent speichert die Stufe
	 * mit Codec.intRange(1, 255). Die uebliche Hoechststufe steht im
	 * Fenster nur als Hinweis, denn im Kreativmodus und ueber Befehle
	 * gibt es hoehere - und eine Regel soll auch darauf passen koennen.
	 *
	 * @param parent  wohin es zurueckgeht; null nimmt das offene Fenster
	 * @param id      die Verzauberung
	 * @param current die bisherige Stufe, steht gleich im Feld
	 * @param onPick  bekommt Kennung und neue Stufe
	 */
	public static void openEnchantLevel(net.minecraft.client.gui.screen.Screen parent,
	                                    net.minecraft.util.Identifier id, int current,
	                                    java.util.function.BiConsumer<net.minecraft.util.Identifier, Integer> onPick) {
		if (id == null) {
			return;
		}
		net.minecraft.client.gui.screen.Screen from = parent != null
			? parent
			: net.minecraft.client.MinecraftClient.getInstance().currentScreen;
		com.lumitier.lumilib.client.EnchantPicker.openLevel(from, id, current, onPick);
	}

	/** Hoechste Stufe, die Minecraft auf einem Gegenstand speichern kann. */
	public static int enchantLevelLimit() {
		return com.lumitier.lumilib.client.EnchantPicker.LEVEL_MAX;
	}

	/**
	 * Anzeigename einer Verzauberung.
	 *
	 * Mit geladener Welt aus den Daten - dann stimmt er auch fuer
	 * Verzauberungen aus Mods. Ohne Welt aus dem Uebersetzungsschluessel,
	 * den Minecraft nach festem Muster bildet. So steht auch im
	 * Hauptmenue ein lesbarer Name da und nicht die rohe Kennung.
	 */
	public static String enchantName(net.minecraft.util.Identifier id) {
		if (id == null) {
			return "";
		}
		net.minecraft.client.MinecraftClient client =
			net.minecraft.client.MinecraftClient.getInstance();
		if (client.world != null) {
			var found = client.world.getRegistryManager()
				.getOptional(net.minecraft.registry.RegistryKeys.ENCHANTMENT)
				.flatMap(reg -> reg.getOptionalValue(id))
				.map(e -> e.description().getString());
			if (found.isPresent()) {
				return found.get();
			}
		}
		return com.lumitier.lumilib.client.EnchantPicker.vanillaName(id);
	}

	/**
	 * Hoechste Stufe einer Verzauberung.
	 *
	 * Mit geladener Welt der echte Wert. Ohne Welt aus einer Tabelle der
	 * Vanilla-Werte, weil die Stufen nur in den Daten stehen; was dort
	 * nicht steht, bekommt 5.
	 */
	public static int enchantMaxLevel(net.minecraft.util.Identifier id) {
		if (id == null) {
			return 5;
		}
		net.minecraft.client.MinecraftClient client =
			net.minecraft.client.MinecraftClient.getInstance();
		if (client.world != null) {
			var found = client.world.getRegistryManager()
				.getOptional(net.minecraft.registry.RegistryKeys.ENCHANTMENT)
				.flatMap(reg -> reg.getOptionalValue(id))
				.map(net.minecraft.enchantment.Enchantment::getMaxLevel);
			if (found.isPresent()) {
				return found.get();
			}
		}
		return com.lumitier.lumilib.client.EnchantPicker.VANILLA_MAX
			.getOrDefault(id.getPath(), 5);
	}

	public static void onServerRulesChanged(Runnable listener) {
		com.lumitier.lumilib.client.ServerRules.onChanged(listener);
	}
}
