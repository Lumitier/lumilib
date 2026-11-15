package com.lumitier.lumilib.client;

import com.lumitier.lumilib.LumiLib;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/**
 * Die Klaenge des Hubs: die Flaeche im Hintergrund und je ein
 * Einzelklang beim Oeffnen und Schliessen.
 *
 * ALLE DREI SIND AUFNAHMEN, keine Rechnung. Herkunft, Lizenz und jede
 * Aenderung stehen in LICENSE-sounds.txt. Beide Quellen stehen unter
 * CC BY, die Namensnennung ist also Bedingung und darf nicht
 * verschwinden.
 *
 * Oeffnen und Schliessen stammen aus DERSELBEN Aufnahme, das zweite
 * eine Oktave tiefer. Damit gehoeren sie hoerbar zusammen, und es
 * braucht nur eine Lizenz. OneConfig macht es genauso.
 *
 * WAS DIE FLAECHE IST:
 * Eine ruhige Flaeche im Kreis, 29 Sekunden lang. Keine Melodie - der
 * Ton liegt hinter einem Menue und soll nicht mitgesummt werden.
 *
 * Es ist eine Aufnahme von VectorSpace, nicht gerechnet: steht in C
 * mit der Wurzel auf C2. Herkunft, Lizenz und jede Aenderung stehen in
 * LICENSE-sounds.txt - CC BY verlangt die Namensnennung, die darf also
 * nicht verlorengehen. Die Bearbeitung macht tools/pad_fit.py.
 *
 * tools/pad_loop.py erzeugt eine gerechnete Flaeche als Rueckfall. Die
 * bleibt im Projekt, falls die Lizenzfrage je anders beantwortet
 * werden muss.
 *
 * ZUR KATEGORIE:
 * SoundCategory.UI, nicht MUSIC. Dieselbe Wahl trifft OneConfig
 * (SoundSource.UI ab 1.21.6). Damit haengt die Lautstaerke an demselben
 * Regler wie Knopfgeraeusche - wer im Spiel die Musik abschaltet, will
 * meistens trotzdem hoeren, dass ein Knopf gedrueckt wurde.
 *
 * WARUM EIN TICKENDER KLANG UND KEIN EINFACHER:
 * Ein gewoehnlicher SoundInstance liest seine Lautstaerke EINMAL beim
 * Abspielen. Der Regler haette dann erst beim naechsten Oeffnen
 * gewirkt, und man haette beim Schieben nicht gehoert, was man
 * einstellt. Ein tickender wird jedes Spiel-Tick neu abgefragt - also
 * zwanzigmal je Sekunde. Dasselbe erlaubt das Ein- und Ausblenden:
 * ohne das setzt der Ton hart ein und bricht hart ab.
 *
 * WENN DIE DATEI FEHLT, passiert nichts: Minecraft meldet den fehlenden
 * Klang im Protokoll und spielt nichts. Kein Absturz, keine Sonderpruefung.
 */
public final class HubSound {

	private HubSound() {}

	public static final Identifier ID = Identifier.of(LumiLib.MOD_ID, "ui.pad_loop");

	/** Der Klang beim Oeffnen und beim Schliessen des Hubs. */
	public static final Identifier OPEN_ID = Identifier.of(LumiLib.MOD_ID, "ui.open");
	public static final Identifier CLOSE_ID = Identifier.of(LumiLib.MOD_ID, "ui.close");

	/**
	 * Lautstaerke bei 100 Prozent am Regler.
	 *
	 * Nicht 1,0: Die Datei ist auf einen Spitzenwert von 0,17 gemischt,
	 * aber auch damit waere voll aufgedreht lauter als jedes Geraeusch
	 * der Oberflaeche. OneConfig faehrt fuer seine eigene Flaeche
	 * ebenfalls 0,5.
	 */
	private static final float FULL = 0.5F;

	/**
	 * Schritte je Tick beim Ein- und Ausblenden, getrennt.
	 *
	 * Bei 20 Ticks je Sekunde sind das 1,5 Sekunden hinein und 4
	 * Sekunden hinaus. Beides von OneConfig uebernommen, die dort
	 * FADE_IN_TICKS = 30 und FADE_TICKS = 80 stehen haben.
	 *
	 * Vorher waren es 0,6 Sekunden in beide Richtungen, und das war in
	 * beide Richtungen falsch: Hinein muss es langsam genug sein, dass
	 * der Oeffnungsklang die Einblendung verdeckt - man soll nicht
	 * hoeren, wie die Flaeche hochfaehrt. Hinaus muss es langsam genug
	 * sein, dass das Schliessen nicht wie ein Abriss wirkt.
	 */
	private static final float FADE_IN_STEP = 1.0F / 30.0F;
	private static final float FADE_OUT_STEP = 1.0F / 80.0F;

	/**
	 * Kleinster Wert, auf den die Blende herunterdarf. NICHT NULL, und
	 * das ist der Grund, warum die Flaeche vorher ueberhaupt nicht lief.
	 *
	 * In SoundSystem.play() steht:
	 *
	 *     if (h == 0.0F) {
	 *         if (!sound.shouldAlwaysPlay() && category != MUSIC) {
	 *             LOGGER.debug("Skipped playing sound {}, volume was zero.");
	 *             return PlayResult.NOT_STARTED;
	 *         }
	 *     }
	 *
	 * Der Konstruktor setzte die Lautstaerke auf 0, damit das Einblenden
	 * bei Null anfaengt - und genau das hat Minecraft dazu gebracht, den
	 * Klang beim Abspielen sofort wieder wegzuwerfen. Er hat nie
	 * angefangen. Die Einzelklaenge beim Oeffnen und Schliessen liefen,
	 * weil die mit echter Lautstaerke starten; das war der Hinweis.
	 *
	 * Die Pruefung passiert NUR beim Abspielen. Im Tick setzt
	 * SoundSystem danach nur noch source.setVolume(), ohne weitere
	 * Pruefung - ein laufender Klang wird bei Null also nicht beendet.
	 * Der Boden ist trotzdem auch dort gesetzt, damit die Sperre bei
	 * einem spaeteren erneuten Abspielen nicht wieder zuschlaegt.
	 *
	 * 0,0001 sind -80 dB. Unhoerbar.
	 *
	 * OneConfig hat dafuer FADE_IN_FLOOR = 0.0001 - dieselbe Zahl aus
	 * demselben Grund.
	 */
	private static final float FADE_FLOOR = 0.0001F;

	private static SoundEvent event;
	private static SoundEvent openEvent;
	private static SoundEvent closeEvent;
	private static Loop running;

	/** Ob die Spielmusik gerade von uns stummgeschaltet ist. */
	private static boolean ducked;

	/**
	 * Schaltet die Spielmusik stumm, solange der Hub zu sehen ist.
	 *
	 * WIE DAS GEHT, OHNE DIE EINSTELLUNGEN DES SPIELERS ANZUFASSEN:
	 * SoundManager.setVolume(Kategorie, Wert) schreibt NICHT in die
	 * Optionen, sondern in eine zweite Zahl, die SoundSystem zusaetzlich
	 * multipliziert:
	 *
	 *     clamp(volume) * clamp(options.getSoundVolume(cat))
	 *                   * volumes.getFloat(cat)
	 *
	 * Der Schieberegler des Spielers bleibt also unberuehrt, und nach
	 * dem Zuruecksetzen auf 1 steht alles wieder wie vorher. setVolume
	 * ruft ausserdem refreshSoundVolumes auf, das heisst bereits
	 * laufende Musik wird sofort leiser und nicht erst das naechste
	 * Stueck.
	 *
	 * WARUM DAS JEDEN TICK LAEUFT UND NICHT BEIM OEFFNEN UND SCHLIESSEN:
	 * Der Multiplikator bleibt stehen, bis ihn jemand zuruecksetzt.
	 * Haengte das Zuruecksetzen an close(), waere die Musik fuer immer
	 * stumm, sobald der Hub auch nur einmal auf einem anderen Weg
	 * verlassen wird - und solche Wege gibt es immer. So wie es hier
	 * steht, richtet sich der Zustand jeden Tick nach dem, was wirklich
	 * auf dem Bildschirm ist. Der Fehler kann damit nicht entstehen.
	 *
	 * Die beiden Abfragen je Tick kosten nichts, und gesetzt wird nur
	 * bei einem echten Wechsel.
	 *
	 * ZUM ZURUECKSETZEN AUF 1: Falls eine andere Mod denselben
	 * Multiplikator benutzt, wird deren Wert dabei ueberschrieben.
	 * Minecraft bietet keinen Weg, den vorherigen Wert auszulesen -
	 * volumes ist privat und hat keinen Lesezugriff.
	 */
	public static void updateMusic(MinecraftClient client) {
		boolean inHub = client.currentScreen instanceof LumiHubScreen;
		if (inHub == ducked) {
			return;
		}
		client.getSoundManager().setVolume(SoundCategory.MUSIC, inHub ? 0.0F : 1.0F);
		ducked = inHub;
	}

	/** Einmal beim Start - ohne Eintrag im Verzeichnis kennt Minecraft den Klang nicht. */
	public static void init() {
		event = Registry.register(Registries.SOUND_EVENT, ID, SoundEvent.of(ID));
		openEvent = Registry.register(Registries.SOUND_EVENT, OPEN_ID, SoundEvent.of(OPEN_ID));
		closeEvent = Registry.register(Registries.SOUND_EVENT, CLOSE_ID, SoundEvent.of(CLOSE_ID));
	}

	/**
	 * Lautstaerke der Einzelklaenge, 0 bis 1.
	 *
	 * Ohne den Faktor 0,5 der Flaeche: Ein Klang, der einmal kurz
	 * auftritt, darf lauter sein als einer, der die ganze Zeit laeuft.
	 * OneConfig macht dieselbe Unterscheidung - deren Flaeche bekommt
	 * 0,5 mal die Einstellung, deren Oeffnungsklang die volle.
	 */
	private static float shotGain() {
		return Math.max(0, Math.min(100, LumiLib.cfg.hubSoundVolume)) / 100.0F;
	}

	/**
	 * Spielt den Oeffnungsklang.
	 *
	 * Ein gewoehnlicher Klang ohne Ort, keine tickende Instanz: Er
	 * dauert zwei Sekunden und muss seine Lautstaerke nicht nachfuehren.
	 *
	 * Beides laeuft GLEICHZEITIG mit dem Start der Flaeche, nicht davor.
	 * Das ist bei OneConfig genauso: play(OPEN) und startAmbience() sind
	 * zwei getrennte Aufrufe ohne Wartezeit dazwischen. Der Einzelklang
	 * verdeckt dabei die Einblendung der Flaeche.
	 */
	public static void playOpen() {
		play(openEvent);
	}

	/** Spielt den Schliessklang. */
	public static void playClose() {
		play(closeEvent);
	}

	private static void play(SoundEvent sound) {
		float gain = shotGain();
		if (sound == null || gain <= 0.0F) {
			return;
		}
		MinecraftClient.getInstance().getSoundManager()
			.play(PositionedSoundInstance.ui(sound, 1.0F, gain));
	}

	/** Lautstaerke aus der Einstellung, 0 bis 1. */
	private static float gain() {
		int percent = Math.max(0, Math.min(100, LumiLib.cfg.hubSoundVolume));
		return FULL * percent / 100.0F;
	}

	/**
	 * Startet den Ton, falls er nicht schon laeuft.
	 *
	 * Laeuft noch eine Instanz, die gerade ausblendet, wird sie einfach
	 * wieder nach oben gedreht statt beendet und neu gestartet. Sonst
	 * haette ein kurzes Schliessen und Wiederoeffnen den Ton an einer
	 * anderen Stelle der Schleife fortgesetzt - hoerbar als Sprung.
	 */
	public static void start() {
		if (event == null || LumiLib.cfg.hubSoundVolume <= 0) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (running != null && !running.isDone()
			&& client.getSoundManager().isPlaying(running)) {
			running.fadeIn();
			return;
		}
		running = new Loop(event);
		client.getSoundManager().play(running);
	}

	/** Blendet den Ton aus, falls einer laeuft. */
	public static void stop() {
		if (running != null) {
			running.fadeOut();
		}
	}

	/**
	 * Nach dem Umschalten in den Einstellungen.
	 *
	 * Die Lautstaerke selbst braucht hier nichts: Der tickende Klang
	 * fragt sie von sich aus ab. Nur der Fall "von 0 hochgedreht" muss
	 * behandelt werden, denn bei 0 laeuft gar keine Instanz.
	 */
	public static void settingChanged() {
		if (LumiLib.cfg.hubSoundVolume <= 0) {
			stop();
		} else if (MinecraftClient.getInstance().currentScreen instanceof LumiHubScreen) {
			start();
		}
	}

	/**
	 * Eine Schleife ohne Ort, die ihre Lautstaerke selbst nachfuehrt.
	 *
	 * relative = true und Daempfung NONE: Der Ton haengt nicht an einer
	 * Stelle in der Welt, sondern am Zuhoerer. Ohne das wuerde er leiser,
	 * sobald sich die Spielfigur bewegt - und im Hauptmenue, wo es keine
	 * Spielfigur gibt, waere die Lautstaerke dem Zufall ueberlassen.
	 */
	private static final class Loop extends MovingSoundInstance {

		/** 0 bis 1, der Stand der Blende. */
		private float fade;
		private boolean leaving;

		Loop(SoundEvent sound) {
			super(sound, SoundCategory.UI, Random.create());
			this.repeat = true;
			this.repeatDelay = 0;
			this.relative = true;
			this.attenuationType = SoundInstance.AttenuationType.NONE;
			this.pitch = 1.0F;
			this.x = 0.0;
			this.y = 0.0;
			this.z = 0.0;
			// Fast bei Null anfangen, sonst ist der erste Tick schon
			// laut - aber eben NICHT bei Null, siehe FADE_FLOOR.
			this.volume = FADE_FLOOR;
		}

		void fadeIn() {
			this.leaving = false;
		}

		void fadeOut() {
			this.leaving = true;
		}

		@Override
		public void tick() {
			fade = leaving
				? Math.max(0.0F, fade - FADE_OUT_STEP)
				: Math.min(1.0F, fade + FADE_IN_STEP);
			this.volume = Math.max(FADE_FLOOR, gain() * fade);
			if (leaving && fade <= 0.0F) {
				// Erst wenn wirklich nichts mehr zu hoeren ist. Ein
				// setDone beim Beginn der Blende waere ein harter Abriss
				// mit zusaetzlichen Schritten davor.
				setDone();
			}
		}
	}
}
