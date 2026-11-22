package com.lumitier.lumilib.mixin;

import com.lumitier.lumilib.client.LangOverride;
import net.minecraft.client.resource.language.TranslationStorage;
import net.minecraft.resource.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Eigene Sprache fuer LumiLib und die Mods darauf, siehe LangOverride.
 *
 * load: Jedes Mal, wenn Minecraft die Sprachdateien laedt (Start, Wechsel
 * der Sprache, F3+T, neues Resource Pack), wird auch die eigene Tabelle
 * neu gebaut - mit denselben Packs.
 *
 * get / hasTranslation: Steht der Schluessel in der eigenen Tabelle, gilt
 * er; sonst laeuft alles wie immer. Bei "Sprache des Spiels" ist die
 * Tabelle leer und es bleibt bei einem Blick in eine leere Map.
 */
@Mixin(TranslationStorage.class)
public abstract class TranslationStorageMixin {

	@Inject(method = "load(Lnet/minecraft/resource/ResourceManager;Ljava/util/List;Z)Lnet/minecraft/client/resource/language/TranslationStorage;",
		at = @At("HEAD"))
	private static void lumilib$rebuild(ResourceManager rm, List<String> definitions, boolean rtl,
	                                    CallbackInfoReturnable<TranslationStorage> cir) {
		LangOverride.rebuild(rm);
	}

	@Inject(method = "get(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
	private void lumilib$get(String key, String fallback, CallbackInfoReturnable<String> cir) {
		String v = LangOverride.get(key);
		if (v != null) {
			cir.setReturnValue(v);
		}
	}

	@Inject(method = "hasTranslation(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
	private void lumilib$has(String key, CallbackInfoReturnable<Boolean> cir) {
		if (LangOverride.get(key) != null) {
			cir.setReturnValue(true);
		}
	}
}
