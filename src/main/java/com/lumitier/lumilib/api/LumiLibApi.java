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
}
