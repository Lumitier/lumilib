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
		 * Dunkles Thema statt des hellen Glases.
		 *
		 * Als WAHRHEITSWERT und nicht als Name der Palette: Es gibt genau
		 * zwei, und ein Schalter laesst sich nicht auf einen Wert stellen,
		 * den es nicht gibt. Ein Name in der Datei haette beim naechsten
		 * Start eine Behandlung fuer Tippfehler gebraucht.
		 */
		public boolean darkTheme = true;

		/**
		 * Lautstaerke des Hintergrundtons im Hub, in Prozent.
		 *
		 * ALS PROZENT UND NICHT ALS KOMMAZAHL: In der Datei steht dann 50
		 * und nicht 0.5, und ein Regler mit ganzen Schritten braucht
		 * keine Behandlung fuer 0.30000000000000004.
		 *
		 * NULL IST AUS: Ein zweiter Schalter daneben waere eine zweite
		 * Stelle, an der dasselbe steht - und die Frage, was gilt, wenn
		 * der Schalter an ist und die Lautstaerke null.
		 */
		public int hubSoundVolume = 50;

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

		/**
		 * Sprache fuer LumiLib und die Mods darauf: "game" (wie Minecraft),
		 * "en_us" oder "de_de". Der Rest des Spiels bleibt, wie er ist.
		 * Siehe LangOverride.
		 */
		public String language = "game";

		/** LumiLib-Knopf im Haupt- und Pausemenue. */
		public boolean menuButton = true;

		/**
		 * Bewegung im Hub: Oeffnen, Schliessen, Seitenwechsel, Hover,
		 * Schalter.
		 *
		 * Abschaltbar, weil Bewegung nicht jedem guttut - und weil man
		 * beim Aufnehmen von Videos manchmal lieber harte Schnitte hat.
		 */
		public boolean animations = true;

		/**
		 * Eigene Reihenfolge der Mods in der Seitenleiste, per Ziehen
		 * festgelegt.
		 *
		 * Nur die nicht angepinnten. Angepinnte stehen in "pinned" in ihrer
		 * eigenen Reihenfolge - so bleibt "Anpinnen heisst: steht oben"
		 * wahr, egal wie man den Rest sortiert. Fehlt eine Mod hier, steht
		 * sie hinter allen, die hier stehen, in der Reihenfolge des
		 * Katalogs.
		 */
		public java.util.List<String> order = new java.util.ArrayList<>();

		/** Akzentfarbe: Index in client.Accent.PRESETS, 0 = Standard der Palette. */
		public int accentPreset = 0;

		/**
		 * GUI-Stufe fuer alle Bildschirme von LumiLib, unabhaengig von der
		 * in Minecrafts Optionen. 0 = automatisch (an die Fenstergroesse
		 * angepasst, das alte Verhalten), 1 bis 4 = feste Stufe.
		 *
		 * Standard 2: So ist der Hub gestaltet, und so sieht er auf jedem
		 * Bildschirm gleich gross aus, egal was im Spiel eingestellt ist.
		 */
		public int guiScale = 2;
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
		if (cfg.order == null) {
			cfg.order = new java.util.ArrayList<>();
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
