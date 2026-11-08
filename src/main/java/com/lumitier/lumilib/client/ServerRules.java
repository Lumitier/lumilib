package com.lumitier.lumilib.client;

import com.lumitier.lumilib.LumiLib;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Regeln, die ein Server mit LumiLibControlPlugin schickt.
 *
 * WAS DAS IST: Der Server sagt, welche von Lumitiers Mods auf ihm erlaubt
 * sind. LumiLib merkt sich das fuer die Dauer der Verbindung und meldet es
 * den Mods, die sich dafuer angemeldet haben. Beim Verlassen ist alles
 * wieder wie vorher.
 *
 * WAS DAS NICHT IST: eine Durchsetzung. Der Server kann nicht in den Client
 * schauen. Er schickt eine Bitte, und die unveraenderten Mods halten sich
 * daran. Wer eine veraenderte Fassung baut, ignoriert sie.
 *
 * DIE EINSTELLUNGEN DES NUTZERS BLEIBEN UNANGETASTET. Eine vom Server
 * abgeschaltete Mod wird nicht in ihrer Config auf "aus" gesetzt, sondern
 * nur fuer diese Verbindung uebersteuert. Sonst waere sie nach dem Besuch
 * eines einzigen Servers dauerhaft aus - und der Nutzer wuesste nicht warum.
 *
 * Ohne das Plugin auf dem Server passiert hier nichts: Dann kommt nie ein
 * Paket, und alles bleibt erlaubt.
 */
public final class ServerRules {

	private ServerRules() {}

	/** Kanal, auf dem das Plugin sendet. Muss mit dem Plugin uebereinstimmen. */
	public static final Identifier CHANNEL = Identifier.of(LumiLib.MOD_ID, "control");

	/**
	 * Fassung des Formats. Ein Paket mit einer anderen Zahl wird ignoriert,
	 * statt falsch gelesen - ein neueres Plugin bricht so keinen alten
	 * Client, es bleibt nur wirkungslos.
	 */
	public static final int PROTOCOL = 1;

	/** Obergrenzen, damit ein kaputtes Paket keinen Speicher frisst. */
	private static final int MAX_MODS = 64;
	private static final int MAX_ID = 64;

	/**
	 * Inhalt des Pakets.
	 *
	 * @param valid   false, wenn das Paket nicht gelesen werden konnte oder
	 *                eine unbekannte Fassung hat - dann wird es verworfen
	 * @param admin   darf dieser Spieler die Regeln aendern? Fuer die
	 *                Verwaltung in LumiLibControl; LumiLib selbst nutzt es
	 *                nur zur Anzeige.
	 * @param allowed Mod-ID zu erlaubt/verboten, wie der Server sie sieht
	 */
	public record Payload(boolean valid, boolean admin, Map<String, Boolean> allowed)
		implements CustomPayload {

		public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);

		public static final PacketCodec<PacketByteBuf, Payload> CODEC =
			CustomPayload.codecOf(Payload::write, Payload::read);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}

		/** Gegenstueck zum Plugin. Nur der Vollstaendigkeit halber - der Client sendet das nie. */
		private void write(PacketByteBuf buf) {
			buf.writeVarInt(PROTOCOL);
			buf.writeBoolean(this.admin);
			buf.writeVarInt(this.allowed.size());
			for (Map.Entry<String, Boolean> e : this.allowed.entrySet()) {
				buf.writeString(e.getKey(), MAX_ID);
				buf.writeBoolean(e.getValue());
			}
		}

		/**
		 * Liest das Paket - und wirft dabei NIE.
		 *
		 * Eine Ausnahme beim Lesen eines Pakets trennt die Verbindung. Ein
		 * fehlerhaftes Plugin oder eine neuere Fassung des Formats wuerde
		 * den Spieler also vom Server werfen. Stattdessen: Rest
		 * ueberspringen, Paket als ungueltig markieren, weiterspielen.
		 */
		private static Payload read(PacketByteBuf buf) {
			try {
				int protocol = buf.readVarInt();
				if (protocol != PROTOCOL) {
					buf.skipBytes(buf.readableBytes());
					return new Payload(false, false, Map.of());
				}
				boolean admin = buf.readBoolean();
				int n = buf.readVarInt();
				if (n < 0 || n > MAX_MODS) {
					buf.skipBytes(buf.readableBytes());
					return new Payload(false, false, Map.of());
				}
				Map<String, Boolean> allowed = new LinkedHashMap<>();
				for (int i = 0; i < n; i++) {
					String id = buf.readString(MAX_ID);
					allowed.put(id, buf.readBoolean());
				}
				return new Payload(true, admin, Collections.unmodifiableMap(allowed));
			} catch (RuntimeException e) {
				buf.skipBytes(buf.readableBytes());
				LumiLib.LOGGER.warn("[LumiLib] Ignored a malformed server control packet.", e);
				return new Payload(false, false, Map.of());
			}
		}
	}

	// ------------------------------------------------------------------
	// Zustand der aktuellen Verbindung
	//
	// Geschrieben nur auf dem Render-Thread (Paketempfang und
	// Verbindungsereignisse laufen dort), gelesen auch aus Mods. volatile
	// und unveraenderliche Mengen: Wer liest, sieht entweder den alten
	// oder den neuen Stand, nie einen halben.
	// ------------------------------------------------------------------

	private static volatile boolean controlled;
	private static volatile boolean admin;
	private static volatile Set<String> disabled = Set.of();
	private static volatile Map<String, Boolean> rules = Map.of();

	private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

	/** Laeuft auf diesem Server LumiLibControlPlugin? */
	public static boolean controlled() {
		return controlled;
	}

	/** Darf der Spieler laut Server die Regeln aendern? */
	public static boolean admin() {
		return admin;
	}

	/**
	 * Hat der Server diese Mod abgeschaltet?
	 *
	 * Billig genug fuer jedes Bild: ein Feldzugriff und eine Mengenabfrage,
	 * keine Zuweisung von Speicher.
	 */
	public static boolean disabled(String modId) {
		return modId != null && disabled.contains(modId);
	}

	/** Alle Regeln, wie der Server sie geschickt hat - fuer die Verwaltung. */
	public static Map<String, Boolean> rules() {
		return rules;
	}

	public static void onChanged(Runnable listener) {
		if (listener != null) {
			LISTENERS.add(listener);
		}
	}

	// ------------------------------------------------------------------
	// Einrichtung
	// ------------------------------------------------------------------

	public static void init() {
		PayloadTypeRegistry.playS2C().register(Payload.ID, Payload.CODEC);

		ClientPlayNetworking.registerGlobalReceiver(Payload.ID, (payload, context) -> {
			if (!payload.valid()) {
				return;
			}
			Set<String> off = new HashSet<>();
			for (Map.Entry<String, Boolean> e : payload.allowed().entrySet()) {
				if (!e.getValue()) {
					off.add(e.getKey());
				}
			}
			Set<String> before = disabled;
			boolean first = fresh;
			fresh = false;
			apply(true, payload.admin(), Set.copyOf(off), payload.allowed());
			LumiLib.LOGGER.info("[LumiLib] Server rules received. Disabled by this server: "
				+ (off.isEmpty() ? "none" : String.join(", ", off)));
			announce(first, before, off);
		});

		// INIT statt JOIN: INIT kommt, bevor das erste Spielpaket verarbeitet
		// wird. Bei JOIN koennte die Antwort des Servers schon da sein und
		// wuerde dann sofort wieder geloescht.
		ClientPlayConnectionEvents.INIT.register((handler, client) -> reset());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	private static void reset() {
		fresh = true;
		if (controlled || !disabled.isEmpty()) {
			apply(false, false, Set.of(), Map.of());
		}
	}

	private static void apply(boolean isControlled, boolean isAdmin,
	                          Set<String> off, Map<String, Boolean> all) {
		controlled = isControlled;
		admin = isAdmin;
		disabled = off;
		rules = all;
		for (Runnable r : LISTENERS) {
			try {
				r.run();
			} catch (Throwable t) {
				// Eine fehlerhafte Mod soll die anderen nicht mitreissen.
				LumiLib.LOGGER.warn("[LumiLib] A server-rules listener failed.", t);
			}
		}
	}

	// ------------------------------------------------------------------
	// Hinweise oben rechts
	// ------------------------------------------------------------------

	/**
	 * Erstes Paket dieser Verbindung? Dann heisst es "ist abgeschaltet",
	 * danach "ist JETZT abgeschaltet" bzw. "wieder an". Ohne die
	 * Unterscheidung saehe ein Betreten aus wie eine Aenderung.
	 */
	private static boolean fresh = true;

	/**
	 * Ein Hinweis pro Mod, deren Zustand sich geaendert hat.
	 *
	 * NUR FUER ANGEMELDETE MODS: Schaltet der Server eine Mod ab, die der
	 * Spieler gar nicht hat, gibt es nichts zu melden. Angemeldet heisst
	 * zugleich: installiert, und sie hoert auf LumiLib.
	 */
	private static void announce(boolean first, Set<String> before, Set<String> now) {
		for (com.lumitier.lumilib.api.LumiMod mod : com.lumitier.lumilib.api.LumiLibApi.mods()) {
			String id = mod.id();
			boolean wasOff = before.contains(id);
			boolean isOff = now.contains(id);
			String key;
			if (first) {
				if (!isOff) {
					continue;
				}
				key = "lumilib.server.toast.disabled";
			} else if (isOff && !wasOff) {
				key = "lumilib.server.toast.now_disabled";
			} else if (!isOff && wasOff) {
				key = "lumilib.server.toast.enabled";
			} else {
				continue;
			}
			toast(id, mod.name().getString(), net.minecraft.text.Text.translatable(key), isOff);
		}
	}

	private static void toast(String id, String name, net.minecraft.text.Text line, boolean warn) {
		try {
			net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
			mc.getToastManager().add(new ServerToast(id, name, line, warn));
		} catch (Throwable t) {
			// Ein fehlender Hinweis ist kein Grund, das Spiel zu stoeren.
			LumiLib.LOGGER.warn("[LumiLib] Could not show the server notice for " + id + ".", t);
		}
	}
}
