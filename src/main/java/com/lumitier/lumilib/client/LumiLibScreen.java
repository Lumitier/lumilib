package com.lumitier.lumilib.client;

import com.lumitier.lumilib.LumiLib;
import com.lumitier.lumilib.api.Options;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Einstellungen von LumiLib selbst.
 *
 * Benutzt die eigenen Options-Bausteine - wie bei JsonConfig gilt: was die
 * Bibliothek anbietet, muss sie selbst benutzen koennen.
 *
 * Das Layout steht hier, nicht in der Bibliothek. Genau diese Trennung soll
 * verhindern, dass ein Fehler im geteilten Code alle Screens gleichzeitig
 * verunstaltet.
 */
public final class LumiLibScreen {

	private LumiLibScreen() {}

	public static Screen create(Screen parent) {
		return YetAnotherConfigLib.createBuilder()
			.title(Text.translatable("lumilib.title"))

			.category(ConfigCategory.createBuilder()
				.name(Text.translatable("lumilib.category.hub"))

				.group(OptionGroup.createBuilder()
					.name(Text.translatable("lumilib.group.privacy"))
					.option(Options.toggle(LumiLib.MOD_ID, "show_session", true,
						() -> LumiLib.cfg.showSession,
						v -> LumiLib.cfg.showSession = v))
					.build())

				.build())

			.save(LumiLib::save)
			.build()
			.generateScreen(parent);
	}
}
