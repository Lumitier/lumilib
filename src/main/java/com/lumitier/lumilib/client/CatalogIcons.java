package com.lumitier.lumilib.client;

import com.lumitier.lumilib.LumiLib;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Mitgelieferte Icons fuer Mods aus dem Verzeichnis.
 *
 * WOZU, obwohl ModIcons doch schon Icons liest:
 * ModIcons liest aus dem Jar der installierten Mod. Eine Mod, die NICHT
 * installiert ist, hat kein Jar - fuer sie gibt es dort nichts zu holen.
 * Im Hub soll sie aber trotzdem ihr richtiges Bild tragen und nicht bloss
 * einen Platzhalter. Also liegt hier eine Kopie.
 *
 * WELCHE ICONS HIER LIEGEN DUERFEN:
 * Nur eigene. Das Icon einer fremden Mod waere fremde Grafik, und die
 * weiterzugeben ist etwas anderes, als sie aus einem installierten Jar
 * anzuzeigen. Deshalb liegen hier genau die fuenf eigenen - alles Weitere
 * kommt zur Laufzeit aus dem Jar oder gar nicht.
 *
 * STAND DER KOPIE:
 * Es ist das Icon der Fassung, die bei der Erstellung von LumiLib aktuell
 * war. Sobald die Mod installiert ist, zieht ohnehin das Icon aus deren
 * Jar - die Kopie hier gilt also nur, solange die Mod fehlt.
 *
 * WARUM TEXTUR UND NICHT GUI-SPRITE:
 * Als Sprite landet ein Bild im Atlas, und aus dem Atlas kann der eigene
 * Shader es nicht lesen - die runden Ecken fielen weg. Als eigene Textur
 * laeuft es durch denselben Weg wie die Jar-Icons und sieht damit genauso
 * aus. Der Preis: Der Pfad muss vollstaendig angegeben werden, mit
 * "textures/" und ".png".
 */
final class CatalogIcons {

	private CatalogIcons() {}

	/** Kennung -> Textur, null bedeutet "nachgesehen und nichts da". */
	private static final Map<String, Identifier> CACHE = new HashMap<>();

	/**
	 * Mitgeliefertes Icon dieser Mod, oder null wenn keines dabei ist.
	 *
	 * @param modId Kennung wie im Verzeichnis, etwa "shadowtweaks"
	 */
	static Identifier get(String modId) {
		if (CACHE.containsKey(modId)) {
			return CACHE.get(modId);
		}
		Identifier id = resolve(modId);
		CACHE.put(modId, id);
		return id;
	}

	private static Identifier resolve(String modId) {
		// Kennungen aus dem Verzeichnis sind Mod-IDs und damit schon
		// harmlos. Der Filter steht trotzdem da: Ein Punkt oder Schraegstrich
		// in einem Identifier wirft eine Ausnahme, und die faenge hier
		// niemand ab.
		String safe = modId.replaceAll("[^a-z0-9_.-]", "_");
		Identifier tex = Identifier.of(LumiLib.MOD_ID,
			"textures/mod_catalog/" + safe + ".png");
		// NACHSEHEN IST PFLICHT: getTexture liefert bei einem unbekannten
		// Pfad nicht null, sondern Minecrafts Ersatztextur - ein deckendes
		// Quadrat. Genau dieser Fehler hat schon einmal schwarze Kaesten
		// statt Buchstaben erzeugt.
		if (MinecraftClient.getInstance().getResourceManager()
				.getResource(tex).isEmpty()) {
			return null;
		}
		return tex;
	}
}
