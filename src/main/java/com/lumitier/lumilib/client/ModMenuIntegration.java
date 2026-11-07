package com.lumitier.lumilib.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Zahnrad in Mod Menu.
 *
 * Fuehrt bewusst in den HUB, nicht in LumiLibs eigene Einstellungen - von
 * dort kommt man mit einem Klick ueberall hin. Die reinen LumiLib-Optionen
 * erreicht man ueber dessen eigene Karte im Hub.
 */
public class ModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return LumiHubScreen::new;
	}
}
