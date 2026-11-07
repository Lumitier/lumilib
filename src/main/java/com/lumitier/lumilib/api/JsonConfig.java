package com.lumitier.lumilib.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Laden und Speichern einer Config als JSON unter config/&lt;modId&gt;.json.
 *
 * Stand bisher in jeder Mod einzeln, inklusive derselben try/catch-Bloecke
 * und derselben Entscheidung "bei Fehler Defaults statt Absturz".
 *
 * GEDACHT FUER EIN CONFIG-OBJEKT, nicht fuer statische Felder: Die Mod haelt
 * eine Instanz mit oeffentlichen Feldern, GSON serialisiert sie direkt. Damit
 * entfaellt die Data-Klasse und das zeilenweise Hin- und Herkopieren, das
 * bisher pro Mod vierzig Zeilen gekostet hat.
 *
 * ACHTUNG - OEFFENTLICHE API: nur hinzufuegen, nie entfernen oder aendern.
 */
public final class JsonConfig {

	private JsonConfig() {}

	private static final Logger LOGGER = LoggerFactory.getLogger("lumilib");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** config/&lt;modId&gt;.json */
	public static Path pathFor(String modId) {
		return FabricLoader.getInstance().getConfigDir().resolve(modId + ".json");
	}

	/**
	 * Laedt die Config. Fehlt die Datei oder ist sie beschaedigt, kommt ein
	 * frisches Objekt aus fallback zurueck - nie null, nie eine Exception
	 * nach aussen.
	 *
	 * Felder, die in der Datei fehlen, behalten den Wert aus dem
	 * Standardkonstruktor der Klasse. Dadurch ist eine Config aus einer
	 * aelteren Version automatisch aufwaertskompatibel.
	 */
	public static <T> T load(String modId, Class<T> type, Supplier<T> fallback) {
		Path p = pathFor(modId);
		try {
			if (Files.exists(p)) {
				try (Reader r = Files.newBufferedReader(p)) {
					T loaded = GSON.fromJson(r, type);
					if (loaded != null) {
						return loaded;
					}
				}
			}
		} catch (Exception e) {
			LOGGER.warn("[LumiLib] Could not load config for {}, falling back to defaults.", modId, e);
		}
		return fallback.get();
	}

	/**
	 * Schreibt die Config. Schlaegt das fehl, wird geloggt und weitergemacht -
	 * eine nicht speicherbare Config ist aergerlich, aber kein Grund, das
	 * Spiel anzuhalten.
	 */
	public static void save(String modId, Object data) {
		Path p = pathFor(modId);
		try {
			Files.createDirectories(p.getParent());
			try (Writer w = Files.newBufferedWriter(p)) {
				GSON.toJson(data, w);
			}
		} catch (Exception e) {
			LOGGER.warn("[LumiLib] Could not save config for {}.", modId, e);
		}
	}
}
