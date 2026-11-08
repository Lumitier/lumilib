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
	public static void onServerRulesChanged(Runnable listener) {
		com.lumitier.lumilib.client.ServerRules.onChanged(listener);
	}
}
