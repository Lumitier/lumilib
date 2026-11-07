package com.lumitier.lumilib.client;

import com.lumitier.lumilib.LumiLib;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Die einzige Stelle in LumiLib, die weiss, dass es YACL geben koennte.
 *
 * WARUM DIESE KLASSE UEBERHAUPT EXISTIERT:
 * YACL war bis zu dieser Fassung eine harte Abhaengigkeit. Das hiess: Wer
 * nur Shadow Tweaks wollte, musste YACL mitinstallieren - obwohl der Hub
 * seine Einstellungen laengst selbst zeichnet und YACL nie zu sehen bekam.
 * Fuer eine Bibliothek, die unter fuenf Mods liegt, ist das zu viel
 * verlangt.
 *
 * WARUM NICHT EINFACH "LumiLibScreen::create" HINSCHREIBEN:
 * Eine Methodenreferenz wird beim ERSTEN DURCHLAUF der Stelle aufgeloest,
 * und dafuer muss die Klasse geladen werden - samt ihrer YACL-Importe.
 * Stuende die Referenz direkt in LumiLibClient, knallte es beim Anmelden
 * mit NoClassDefFoundError, noch bevor irgendjemand auf etwas geklickt
 * hat. Hier steht sie in einer Methode, die nur betreten wird, wenn YACL
 * wirklich da ist.
 *
 * Aus demselben Grund darf keine andere Klasse in LumiLib LumiLibScreen
 * oder api/Options anfassen. Options bleibt im Jar, weil die vier Mods
 * dagegen gebaut sind - geladen wird es nur von ihnen, und die haengen
 * selbst an YACL.
 */
final class Yacl {

	/** Mod-ID von YACL 3, wie sie in dessen fabric.mod.json steht. */
	private static final String MOD_ID = "yet_another_config_lib_v3";

	private Yacl() {}

	static boolean present() {
		return FabricLoader.getInstance().isModLoaded(MOD_ID);
	}

	/**
	 * LumiLibs eigener YACL-Screen - oder null, wenn YACL fehlt.
	 *
	 * Der Hub zeichnet LumiLibs Einstellungen ohnehin selbst. Das hier ist
	 * nur die Rueckfallebene fuer den Fall, dass die Inline-Seite nicht
	 * gebaut werden kann.
	 */
	static @Nullable Function<Screen, Screen> screenFactory() {
		if (!present()) {
			LumiLib.LOGGER.debug("[LumiLib] YACL not installed - "
				+ "the hub draws its own settings page instead.");
			return null;
		}
		return LumiLibScreen::create;
	}
}
