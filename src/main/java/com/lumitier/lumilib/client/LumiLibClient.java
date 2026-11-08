package com.lumitier.lumilib.client;

import com.lumitier.lumilib.LumiLib;
import com.lumitier.lumilib.api.LumiLibApi;
import com.lumitier.lumilib.api.LumiMod;
import com.lumitier.lumilib.api.SettingsPage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client-Entrypoint von LumiLib.
 *
 * Aufgaben: Config laden, Taste registrieren, sich selbst beim eigenen Hub
 * anmelden.
 *
 * Die Taste laeuft ueber Fabric API (KeyBindingHelper, ClientTickEvents).
 * Das ist der Grund, warum LumiLib - anders als die einzelnen Mods - Fabric
 * API wirklich braucht und nicht nur zum Bauen.
 */
public class LumiLibClient implements ClientModInitializer {

	/** Kategorie, damit die Taste in den Steuerungen gruppiert steht. */
	public static final KeyBinding.Category CATEGORY =
		KeyBinding.Category.create(Identifier.of(LumiLib.MOD_ID, "main"));

	public static KeyBinding openHub;

	@Override
	public void onInitializeClient() {
		LumiLib.load();

		// Empfang fuer LumiLibControlPlugin. Kostet nichts, solange kein
		// Server das Plugin hat - dann kommt einfach nie ein Paket.
		ServerRules.init();

		openHub = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.lumilib.open_hub",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_L,
			CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// while statt if: wurde die Taste in einem Tick mehrfach
			// gedrueckt, leert die Schleife den Puffer sauber.
			while (openHub.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new LumiHubScreen(null));
				}
			}
		});

		// LumiLib meldet sich selbst an. Ohne das waere der Hub leer, solange
		// keine andere Mod umgebaut ist - und ein leerer Hub sieht aus wie
		// ein Fehler, auch wenn er korrekt ist.
		LumiLibApi.register(new LumiMod(
			LumiLib.MOD_ID,
			Text.translatable("lumilib.title"),
			LumiLib.version(),
			Text.translatable("lumilib.summary"),
			// null, und zwar bewusst: Der Hub liest das Icon jetzt aus dem
			// Jar - dieselbe icon.png, die auch Mod Menu und Modrinth
			// zeigen. Damit laeuft LumiLib durch denselben Weg wie jede
			// andere Mod und bekommt runde Ecken statt eines Quadrats.
			// Ein Sprite hier wuerde den Weg ueberspringen.
			null,
			// YACL bleibt als Rueckfallebene angemeldet - aber NUR, wenn
			// es ueberhaupt installiert ist. Seit YACL keine harte
			// Abhaengigkeit mehr ist, wuerde eine direkte Referenz auf
			// LumiLibScreen hier beim Anmelden einen NoClassDefFoundError
			// ausloesen. Warum das so ist, steht in Yacl.
			Yacl.screenFactory(),
			LumiLibClient::settings));

		LumiLib.LOGGER.info("[LumiLib] Client init done. Hub is on the L key.");
	}

	/**
	 * Die eigenen Einstellungen, wie der Hub sie zeichnet.
	 *
	 * WARUM LUMILIB DAS SELBST BENUTZT: Dieselbe Regel wie bei JsonConfig
	 * und Options - was die Bibliothek anbietet, muss sie selbst benutzen
	 * koennen. Faellt hier etwas unangenehm auf, faellt es auf, bevor vier
	 * andere Mods darauf gebaut haben.
	 *
	 * Die Texte stehen in der Sprachdatei unter lumilib.group.* und
	 * lumilib.option.*, nicht hier.
	 */
	private static SettingsPage settings() {
		SettingsPage.Builder b = SettingsPage.builder(LumiLib.MOD_ID)
			.section("hub")
				// Die Standardwerte stehen hier GENAUSO wie im Config-Feld.
				// Zwei Quellen fuer dieselbe Zahl - aber die Alternative
				// waere gewesen, LumiLib ein frisches Config-Objekt bauen
				// zu lassen, und das kann es bei fremden Mods ohnehin
				// nicht. Lieber ein ehrliches Duplikat als eine Mechanik,
				// die nur im eigenen Haus funktioniert.
				.toggle("show_session",
					() -> LumiLib.cfg.showSession,
					v -> LumiLib.cfg.showSession = v, true)
				.toggle("show_missing",
					() -> LumiLib.cfg.showMissing,
					v -> LumiLib.cfg.showMissing = v, true);

		// Die Zeile entsteht gar nicht erst, wenn Mod Menu fehlt. Ein
		// Knopf, der nichts tun kann, ist schlimmer als keiner.
		if (FabricLoader.getInstance().isModLoaded("modmenu")) {
			b.section("tools").action("open_mod_menu", LumiLibClient::openModMenu);
		}
		return b.save(LumiLib::save).build();
	}

	/**
	 * Oeffnet Mod Menu mit dem Hub als Rueckweg.
	 *
	 * Der Verweis auf ModMenuUpdates steht im RUMPF, nicht im Kopf: So
	 * wird die Klasse erst angefasst, wenn jemand wirklich klickt - und
	 * ohne Mod Menu gibt es die Zeile ohnehin nicht.
	 */
	private static void openModMenu() {
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.setScreen(ModMenuUpdates.screen(mc.currentScreen));
	}
}
