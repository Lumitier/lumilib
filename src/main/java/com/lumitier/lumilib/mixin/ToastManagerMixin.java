package com.lumitier.lumilib.mixin;

import com.lumitier.lumilib.client.ServerToast;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.ToastManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;

/**
 * Haengt LumiLibs Server-Hinweise an Minecrafts Toast-Zeichnung.
 *
 * WARUM HIER: Minecraft malt seine Toasts ueber dem Spiel UND ueber jedem
 * Menue, und blendet sie mit F1 aus. Genau das sollen die Server-Hinweise
 * auch. Ein HUD-Element von Fabric verschwaende hinter jedem Menue, also
 * auch hinter dem Hub.
 *
 * WARUM TAIL: Bei ausgeblendetem HUD kehrt draw() vorzeitig zurueck - TAIL
 * trifft nur das Ende, die Hinweise sind dann ebenfalls aus. Und am Ende
 * sind Minecrafts Toasts schon gemalt; die Hinweise liegen darunter.
 *
 * occupiedSpaces: welche der fuenf 32-Pixel-Plaetze Minecrafts Toasts
 * gerade belegen. length() ist der unterste belegte plus eins - darunter
 * beginnt der Stapel, damit sich nichts ueberlappt.
 */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {

	@Shadow
	@Final
	private BitSet occupiedSpaces;

	@Inject(method = "draw(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("TAIL"))
	private void lumilib$drawServerNotices(DrawContext ctx, CallbackInfo ci) {
		ServerToast.draw(ctx, this.occupiedSpaces.length());
	}
}
