package com.lumitier.lumilib.client;

import com.lumitier.lumilib.LumiLib;
import com.lumitier.lumilib.api.LumiCatalog;
import com.lumitier.lumilib.api.LumiLibApi;
import com.lumitier.lumilib.api.LumiMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ein Eintrag in der Hub-Liste - angemeldet oder nicht.
 *
 * WARUM NICHT EINFACH LumiMod:
 * LumiMod entsteht erst, wenn eine Mod sich anmeldet. Eine fehlende Mod
 * kann das nicht. Der Hub braucht aber auch fuer sie einen Eintrag, sonst
 * liesse sich "nicht installiert" gar nicht anzeigen.
 *
 * DREI ZUSTAENDE statt zwei:
 * Eine Mod kann installiert sein, ohne angemeldet zu sein - naemlich in
 * einer Fassung ohne LumiLib-Anbindung. Die als "fehlt" anzuzeigen waere
 * schlicht falsch, deshalb gibt es State.PRESENT dazwischen.
 */
record HubEntry(
	String id,
	Text name,
	String version,
	Text description,
	State state,
	@Nullable String modrinth,
	@Nullable LumiMod mod
) {

	enum State {
		/** Laeuft und ist angebunden - volle Seite mit Einstellungen. */
		REGISTERED,
		/** Installiert, aber ohne LumiLib-Anbindung. */
		PRESENT,
		/** Fehlt. */
		MISSING
	}

	boolean selectable() {
		return true;
	}

	/**
	 * Alle Eintraege: angemeldete zuerst, danach die uebrigen aus dem
	 * Verzeichnis. Innerhalb der Gruppen nach Namen.
	 */
	static List<HubEntry> all() {
		List<HubEntry> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		for (LumiMod m : LumiLibApi.mods()) {
			out.add(new HubEntry(m.id(), m.name(), m.version(), m.description(),
				State.REGISTERED, modrinthFor(m.id()), m));
			seen.add(m.id());
		}

		List<HubEntry> rest = new ArrayList<>();
		for (LumiCatalog.Entry e : LumiCatalog.KNOWN) {
			if (seen.contains(e.modId())) {
				continue;
			}
			boolean loaded = FabricLoader.getInstance().isModLoaded(e.modId());
			// Wer das Verzeichnis nicht sehen will, bekommt nur, was laeuft.
			if (!loaded && !com.lumitier.lumilib.LumiLib.cfg.showMissing) {
				continue;
			}
			rest.add(new HubEntry(
				e.modId(),
				Text.literal(e.name()),
				loaded ? installedVersion(e.modId()) : "",
				Text.translatable(loaded
					? "lumilib.entry.present" : "lumilib.entry.missing"),
				loaded ? State.PRESENT : State.MISSING,
				e.modrinth(),
				null));
		}
		out.addAll(rest);

		// EINE Sortierung am Ende statt zwei Gruppen.
		//
		// Vorher standen angemeldete Mods vor dem Verzeichnis, innerhalb
		// alphabetisch. Das hiess: Die Liste sprang um, sobald sich eine
		// Mod anmeldete - und LumiLib landete zwischen Item Drop und
		// Perpetual Snow. Jetzt entscheidet allein die Reihenfolge im
		// Katalog, und Angepinnte stehen davor.
		out.sort(Comparator
			.comparingInt((HubEntry h) -> LumiLib.cfg.pinned.contains(h.id()) ? 0 : 1)
			.thenComparingInt(h -> LumiCatalog.order(h.id()))
			.thenComparing(h -> h.name().getString(), String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	private static @Nullable String modrinthFor(String modId) {
		for (LumiCatalog.Entry e : LumiCatalog.KNOWN) {
			if (e.modId().equals(modId)) {
				return e.modrinth();
			}
		}
		return null;
	}

	private static String installedVersion(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("");
	}
}
