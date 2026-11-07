package com.lumitier.lumilib.client;

import com.lumitier.lumilib.LumiLib;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Icons INSTALLIERTER Mods, zur Laufzeit geladen.
 *
 * DER PUNKT:
 * Jede Mod bringt ohnehin ein Icon mit - es steht in ihrer
 * fabric.mod.json und wird von Mod Menu und Modrinth angezeigt. LumiLib
 * liest genau dieses. Dadurch muss keine Mod ein zusaetzliches Sprite
 * mitliefern, und auch fremde Mods, die sich spaeter anmelden, haben
 * sofort ihr richtiges Icon.
 *
 * Vorher musste jede Mod ein eigenes GUI-Sprite bauen. Das war
 * ueberfluessige Arbeit und hat ausserdem jedes Mal anders ausgesehen.
 *
 * GEPRUEFT GEGEN 1.21.11:
 *     ModMetadata.getIconPath(int)   liefert den Pfad im Mod-Jar
 *     ModContainer.findPath(String)  loest ihn zu einer Datei auf
 *     NativeImage.read(InputStream)
 *     TextureManager.registerTexture(Identifier, AbstractTexture)
 *
 * ZUM ZWISCHENSPEICHER:
 * Jede Kennung wird genau EINMAL versucht, Fehlschlag inbegriffen. Ohne
 * das wuerde bei einer Mod ohne Icon in jedem Bild erneut im Jar gesucht.
 *
 * WARUM DAS TROTZDEM IMMER AKTUELL IST:
 * Hier liegt kein einziges fremdes Icon als Kopie. Gelesen wird bei jedem
 * Start aus dem Jar, das gerade im Ordner liegt - taucht nach einem Update
 * ein neues Icon darin auf, zeigt der Hub von selbst das neue. Waehrend das
 * Spiel laeuft, kann sich ein geladenes Jar nicht aendern; es gibt also
 * nichts, was der Zwischenspeicher verpassen koennte.
 *
 * ANNAHME, UNGEPRUEFT: Icons sind quadratisch. Modrinth verlangt das
 * ohnehin. Ein 128x64-Icon wuerde beim Zeichnen auf ein Quadrat gezerrt.
 */
public final class ModIcons {

	private ModIcons() {}

	/** Kennung -> Textur, null bedeutet "versucht und nichts gefunden". */
	private static final Map<String, Identifier> CACHE = new HashMap<>();

	/**
	 * Textur des Icons dieser Mod, oder null wenn keines zu finden war.
	 *
	 * @param modId Kennung wie im Loader, etwa "shadowtweaks"
	 */
	public static Identifier get(String modId) {
		if (CACHE.containsKey(modId)) {
			return CACHE.get(modId);
		}
		Identifier id = load(modId);
		CACHE.put(modId, id);
		if (id == null) {
			// Debug: Ein Mod ohne Icon ist voellig normal, dafuer gibt
			// es den Platzhalter. Auf info waere das eine Warnung, die
			// keine ist.
			LumiLib.LOGGER.debug("[LumiLib] No icon found for " + modId
				+ ", using the placeholder.");
		}
		return id;
	}

	private static Identifier load(String modId) {
		try {
			ModContainer container = FabricLoader.getInstance()
				.getModContainer(modId).orElse(null);
			if (container == null) {
				return null;
			}
			// DIE ZAHL IST EIN WUNSCH, KEIN ZWANG: Der Loader sucht die
			// naechstpassende der Groessen, die in der fabric.mod.json
			// stehen, und gibt sonst die groesste her. Bringt eine Mod
			// also spaeter ein feineres Icon mit, wird genau das genommen,
			// ohne dass hier etwas geaendert werden muesste.
			//
			// WARUM 128 UND NICHT 64: Gezeichnet werden hoechstens 24
			// logische Pixel, bei GUI-Skalierung 4 also 96 echte. Mit 64
			// waere das Bild vergroessert und damit matschig. Groesser als
			// 128 waere nur Speicher - ein 512er Icon belegt 1 MB.
			//
			// Ohne Angabe in der fabric.mod.json gilt die uebliche Stelle
			// assets/<modid>/icon.png.
			String iconPath = container.getMetadata()
				.getIconPath(128)
				.orElse("assets/" + modId + "/icon.png");
			Path file = container.findPath(iconPath).orElse(null);
			if (file == null) {
				return null;
			}
			NativeImage image;
			try (InputStream in = Files.newInputStream(file)) {
				image = NativeImage.read(in);
			}
			Identifier texture = Identifier.of(LumiLib.MOD_ID,
				"mod_icon/" + modId.replaceAll("[^a-z0-9_.-]", "_"));
			MinecraftClient.getInstance().getTextureManager().registerTexture(
				texture, new NativeImageBackedTexture(
					() -> "lumilib/icon/" + modId, image));
			return texture;
		} catch (Throwable t) {
			return null;
		}
	}
}
