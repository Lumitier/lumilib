package com.lumitier.lumilib.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Eine Spielerfigur ohne Spieler - fuer die Vorschau, wenn keiner geladen
 * ist: im Hauptmenue, oder fuer jemanden, der offline oder weit weg ist.
 *
 * Statt eines echten Spielers wird hier nur der Zustand gebaut, den
 * Minecraft zum Zeichnen braucht: Skin, Groesse, welche Skin-Ebenen an
 * sind. Der laeuft durch denselben Spieler-Renderer wie ein echter Spieler -
 * samt allem, was andere Mods dort anhaengen. Genau deshalb sieht man ihre
 * Aenderungen auch hier.
 *
 * WIE MODS DIE FIGUR ERKENNEN: id ist Integer.MIN_VALUE (kein Objekt in der
 * Welt hat diese Nummer), und playerName traegt den Namen.
 *
 * DER SKIN kommt von Mojangs Servern und ist beim ersten Mal eventuell
 * noch nicht da. Bis dahin steht Minecrafts Standard-Skin da, danach der
 * richtige - ohne dass man etwas tun muss.
 */
final class MenuPlayer {

	private MenuPlayer() {}

	static final int ID = Integer.MIN_VALUE;

	private static Supplier<SkinTextures> ownSkin;
	/** Skins anderer Spieler, nach Name - einmal angefragt, dann gemerkt. */
	private static final Map<String, Supplier<SkinTextures>> SKINS = new HashMap<>();
	private static final PlayerEntityRenderState STATE = new PlayerEntityRenderState();

	/** @param name Spielername, oder null fuer einen selbst */
	static PlayerEntityRenderState state(String name) {
		MinecraftClient client = MinecraftClient.getInstance();
		boolean self = name == null || name.equalsIgnoreCase(client.getSession().getUsername());
		PlayerEntityRenderState s = STATE;

		s.entityType = EntityType.PLAYER;
		s.id = ID;
		s.skinTextures = self ? ownSkin(client) : skinOf(client, name);
		s.playerName = Text.literal(self ? client.getSession().getUsername() : name);
		s.width = 0.6F;
		s.height = 1.8F;
		s.standingEyeHeight = 1.62F;
		s.baseScale = 1.0F;
		s.ageScale = 1.0F;
		s.invisible = false;
		s.spectator = false;
		s.displayName = null;
		s.nameLabelPos = null;
		// Laufende Zeit in Ticks - davon haengt das leichte Armschwingen ab.
		s.age = (System.currentTimeMillis() % 1_000_000L) / 50.0F;

		// Die Skin-Ebenen: bei einem selbst so, wie man sie in den Optionen
		// eingestellt hat. Bei anderen weiss man es nicht - dann alle an,
		// so wie die meisten spielen.
		GameOptions o = client.options;
		s.hatVisible = !self || o.isPlayerModelPartEnabled(PlayerModelPart.HAT);
		s.jacketVisible = !self || o.isPlayerModelPartEnabled(PlayerModelPart.JACKET);
		s.leftSleeveVisible = !self || o.isPlayerModelPartEnabled(PlayerModelPart.LEFT_SLEEVE);
		s.rightSleeveVisible = !self || o.isPlayerModelPartEnabled(PlayerModelPart.RIGHT_SLEEVE);
		s.leftPantsLegVisible = !self || o.isPlayerModelPartEnabled(PlayerModelPart.LEFT_PANTS_LEG);
		s.rightPantsLegVisible = !self || o.isPlayerModelPartEnabled(PlayerModelPart.RIGHT_PANTS_LEG);
		s.capeVisible = s.skinTextures.cape() != null
			&& (!self || o.isPlayerModelPartEnabled(PlayerModelPart.CAPE));
		s.mainArm = self ? o.getMainArm().getValue() : net.minecraft.util.Arm.RIGHT;
		s.preferredArm = s.mainArm;
		return s;
	}

	private static SkinTextures ownSkin(MinecraftClient client) {
		if (ownSkin == null) {
			ownSkin = client.getSkinProvider().supplySkinTextures(client.getGameProfile(), false);
		}
		return ownSkin.get();
	}

	private static SkinTextures skinOf(MinecraftClient client, String name) {
		Supplier<SkinTextures> supplier = SKINS.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> {
			// Dieselbe Abfrage wie bei einem Spielerkopf mit diesem Namen:
			// Name -> Profil -> Skin, alles von Minecraft zwischengespeichert.
			var entry = client.getPlayerSkinCache().getSupplier(ProfileComponent.ofDynamic(name));
			return () -> entry.get().getTextures();
		});
		try {
			return supplier.get();
		} catch (Throwable t) {
			return DefaultSkinHelper.getSteve();
		}
	}
}
