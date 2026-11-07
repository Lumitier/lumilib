package com.lumitier.lumilib;

import com.lumitier.lumilib.api.JsonConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Zustand und Config von LumiLib selbst.
 *
 * Nutzt bewusst die eigene JsonConfig-API statt einer Sonderloesung - wenn
 * die Bibliothek ihr eigenes Werkzeug nicht benutzen kann, taugt das Werkzeug
 * nichts.
 */
public final class LumiLib {

	public static final String MOD_ID = "lumilib";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private LumiLib() {}

	// ------------------------------------------------------------------
	// Config
	// ------------------------------------------------------------------

	/**
	 * Oeffentliche Felder, direkt von GSON serialisiert. Kein Data-Objekt,
	 * kein zeilenweises Kopieren - genau das Muster, auf das die anderen
	 * Mods beim Umbau wechseln.
	 */
	public static final class Config {
		/**
		 * Kontoblock oben im Hub anzeigen.
		 *
		 * Aus fuer alle, die beim Streamen oder im Video nicht ihren
		 * Kontonamen im Bild haben wollen.
		 */
		public boolean showSession = true;

		/**
		 * Nicht installierte Mods in der Liste zeigen.
		 *
		 * An: Das Verzeichnis steht vollstaendig da, Fehlendes ausgegraut
		 * mit Link. Aus: Es steht nur, was wirklich laeuft.
		 *
		 * Standard an, weil ein Hub, der nur eine Zeile zeigt, aussieht
		 * wie ein Fehler.
		 */
		public boolean showMissing = true;

		/**
		 * Angepinnte Mods, in der Reihenfolge des Anpinnens.
		 *
		 * Eine Liste, kein Set: Die Reihenfolge ist sichtbar, also muss
		 * sie auch gespeichert werden. Ein Set wuerde sie bei jedem
		 * Laden neu wuerfeln.
		 */
		public java.util.List<String> pinned = new java.util.ArrayList<>();
	}

	public static volatile Config cfg = new Config();

	public static void load() {
		cfg = JsonConfig.load(MOD_ID, Config.class, Config::new);
		// Fehlt "pinned" in der Datei, laeuft der Feldinitialisierer und die
		// Liste ist leer. Steht dort aber ausdruecklich null - von Hand
		// editiert oder aus einer kaputten Datei -, setzt GSON null ein, und
		// jeder Frame liefe in eine NullPointerException. Eine Zeile gegen
		// einen Absturz, der sich sonst erst beim Nutzer zeigt.
		if (cfg.pinned == null) {
			cfg.pinned = new java.util.ArrayList<>();
		}
		save();
	}

	public static void save() {
		JsonConfig.save(MOD_ID, cfg);
	}

	// ------------------------------------------------------------------
	// Eigene Version, fuer die Fusszeile im Hub
	// ------------------------------------------------------------------

	public static String version() {
		return modVersion(MOD_ID);
	}

	/**
	 * Minecraft-Version, wie der Loader sie kennt.
	 *
	 * Nicht ueber SharedConstants: Minecraft ist als Mod beim Loader
	 * registriert, und dessen Angabe ist dieselbe, die Mod Menu und
	 * Fabric selbst anzeigen. Eine zweite Quelle koennte abweichen.
	 */
	public static String minecraftVersion() {
		return modVersion("minecraft");
	}

	private static String modVersion(String id) {
		return FabricLoader.getInstance()
			.getModContainer(id)
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("?");
	}

	// ------------------------------------------------------------------
	// Sitzung
	//
	// WICHTIG: Der Zugriffstoken der Session wird nirgends angezeigt,
	// geloggt oder gespeichert. Hier werden nur Name, UUID und die Frage
	// "Microsoft-Konto oder nicht" ausgelesen.
	//
	// Und zur Ehrlichkeit: Der Client kann eine Session nicht pruefen. Er
	// haelt, was der Launcher ihm gegeben hat. Das hier ist eine Anzeige,
	// kein Nachweis - deshalb heisst es im Text auch "Microsoft-Konto" und
	// nicht "verifiziert".
	// ------------------------------------------------------------------

	private static Session session() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc != null ? mc.getSession() : null;
	}

	public static String sessionName() {
		Session s = session();
		return s != null ? s.getUsername() : "?";
	}

	public static UUID sessionUuid() {
		Session s = session();
		return s != null ? s.getUuidOrNull() : null;
	}

	/**
	 * Echte Microsoft-Anmeldung?
	 *
	 * Die Xbox-User-ID wird nur bei einer echten Anmeldung ueber Microsoft
	 * gesetzt. Eine Offline-Session hat keine. Das ist ein tatsaechliches
	 * Unterscheidungsmerkmal und kein Raten anhand der UUID-Form.
	 */
	public static boolean sessionIsMicrosoft() {
		Session s = session();
		return s != null && s.getXuid().filter(x -> !x.isBlank()).isPresent();
	}
}
