package com.lumitier.lumilib.client.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.lumitier.lumilib.LumiLib;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;

/**
 * Eine Kopie des Bildschirms, wie er VOR der Oberflaeche aussah.
 *
 * WOZU: Echtes Glas bricht das Licht. Dafuer muss der Shader wissen, was
 * HINTER der Flaeche liegt - und das weiss er nur, wenn es ihm als
 * Textur gereicht wird. Ohne diese Kopie bleibt jede "Glasflaeche" eine
 * eingefaerbte Scheibe mit huebschen Kanten.
 *
 * WARUM DAS GENAU HIER FUNKTIONIERT:
 * Seit 1.21.5 sammelt Minecraft die Oberflaeche erst und zeichnet sie
 * am Ende. Zu Beginn von Screen.render ist die WELT fertig gezeichnet
 * und von der Oberflaeche noch nichts da. Genau dann wird kopiert. Der
 * Hub bekommt damit die Welt hinter sich - mehr braucht er nicht.
 *
 * WAS DAS NICHT KANN, und das ist die Grenze dieser Fassung:
 * Alles bricht DIESELBE Kopie. Eine Karte auf dem Panel bricht also die
 * Welt, nicht das Panel darunter. Fuer das grosse Panel ueber der Welt
 * stimmt es; fuer Flaechen auf Flaechen braeuchte es eine zweite Kopie
 * nach dem Panel, und damit eine Reihenfolge, die Minecrafts Stapel von
 * sich aus nicht hergibt.
 *
 * ALLE AUFRUFE HIER sind gegen die Yarn-Zuordnung 1.21.11+build.6 aus
 * dem Loom-Cache geprueft, nicht geraten:
 *     MinecraftClient.getFramebuffer()      method_1522
 *     Framebuffer.getColorAttachment()      method_30277
 *     Framebuffer.textureWidth/-Height      field_1482 / field_1481
 * Die blaze3d-Namen bleiben unzugeordnet und heissen ueberall gleich.
 */
public final class Backdrop {

	private Backdrop() {}

	private static GpuTexture texture;
	private static GpuTextureView view;
	private static int width;
	private static int height;

	/** Einmal fehlgeschlagen heisst: nicht in jedem Bild erneut versuchen. */
	private static boolean broken;

	/**
	 * Kopiert den aktuellen Bildschirm. Zu Beginn von render() aufrufen.
	 *
	 * @return true, wenn eine brauchbare Kopie vorliegt
	 */
	public static boolean capture() {
		if (broken) {
			return false;
		}
		try {
			Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
			int fw = fb.textureWidth;
			int fh = fb.textureHeight;
			if (fw <= 0 || fh <= 0) {
				return false;
			}
			if (texture == null || width != fw || height != fh) {
				close();
				texture = RenderSystem.getDevice().createTexture(
					() -> "lumilib/backdrop",
					GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
					TextureFormat.RGBA8, fw, fh, 1, 1);
				view = RenderSystem.getDevice().createTextureView(texture);
				width = fw;
				height = fh;
			}
			RenderSystem.getDevice().createCommandEncoder()
				.copyTextureToTexture(fb.getColorAttachment(), texture,
					0, 0, 0, 0, 0, fw, fh);
			return true;
		} catch (Throwable t) {
			// Keine Kopie heisst: kein Glas, aber auch kein Absturz. Der
			// Hub faellt auf die bisherige Kantenoptik zurueck.
			LumiLib.LOGGER.warn("[LumiLib] Screen capture not possible - "
				+ "glass without refraction.", t);
			broken = true;
			close();
			return false;
		}
	}

	public static GpuTextureView view() {
		return view;
	}

	public static boolean ready() {
		return !broken && view != null;
	}

	private static void close() {
		try {
			if (view != null) {
				view.close();
			}
			if (texture != null) {
				texture.close();
			}
		} catch (Throwable ignored) {
			// Beim Aufraeumen ist ein Fehler folgenlos.
		}
		view = null;
		texture = null;
		width = 0;
		height = 0;
	}
}
