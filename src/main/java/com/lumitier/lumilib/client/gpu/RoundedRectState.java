package com.lumitier.lumilib.client.gpu;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fc;

/**
 * Eine runde Flaeche als GUI-Element.
 *
 * WARUM EIN EIGENES ELEMENT STATT EINFACH ZU ZEICHNEN:
 * Seit 1.21.5 malt Minecraft die Oberflaeche nicht mehr Aufruf fuer
 * Aufruf. Alles wird zuerst gesammelt und am Ende in einem Durchgang
 * gezeichnet. Wer dazwischen selbst etwas an die Grafikkarte schickt,
 * landet in der falschen Reihenfolge - meist hinter allem anderen.
 *
 * Der vorgesehene Weg ist dieses Interface. Es verlangt vier Dinge, und
 * eines davon ist pipeline(). Genau dort reichen wir unsere eigene
 * Pipeline ein, und Minecraft zeichnet sie an der richtigen Stelle mit.
 *
 * ZUR REIHENFOLGE DER KANAELE:
 * VertexConsumer erwartet sie in der Reihenfolge des Eckpunktformats -
 * hier POSITION, UV0, COLOR, UV2. Wer sie vertauscht, bekommt keinen
 * Fehler, sondern Unsinn auf dem Bildschirm.
 *
 * Die Verschiebung uebernimmt vertex(Matrix3x2fc, x, y) - so macht es
 * Vanilla in ColoredQuadGuiElementRenderState auch. Eigene Matrixrechnung
 * waere nicht nur ueberfluessig, sondern eine zweite Fehlerquelle.
 */
public record RoundedRectState(
	RenderPipeline pipeline,
	Matrix3x2fc pose,
	int x,
	int y,
	int w,
	int h,
	int radius,
	int argb,
	@Nullable ScreenRect scissorArea,
	ScreenRect bounds
) implements SimpleGuiElementRenderState {

	/** Groesste Werte, die in die Packung passen - siehe rounded.vsh. */
	public static final int MAX_RADIUS = 31;
	public static final int MAX_HEIGHT = 1023;

	@Override
	public TextureSetup textureSetup() {
		return TextureSetup.empty();
	}

	@Override
	public void setupVertices(VertexConsumer vc) {
		int rr = Math.min(Math.max(radius, 0), MAX_RADIUS);
		int hh = Math.min(Math.max(h, 0), MAX_HEIGHT);
		int packed = (rr << 10) | hh;

		float halfW = w * 0.5F;
		float halfH = h * 0.5F;

		emit(vc, x,     y,     -halfW, -halfH, packed);
		emit(vc, x,     y + h, -halfW,  halfH, packed);
		emit(vc, x + w, y + h,  halfW,  halfH, packed);
		emit(vc, x + w, y,      halfW, -halfH, packed);
	}

	private void emit(VertexConsumer vc, int sx, int sy,
	                  float lx, float ly, int packed) {
		vc.vertex(pose, sx, sy)
			.texture(lx, ly)
			.color(argb)
			.light(w, packed);
	}
}
