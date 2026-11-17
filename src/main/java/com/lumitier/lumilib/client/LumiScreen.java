package com.lumitier.lumilib.client;

/**
 * Markiert ein Fenster als zu LumiLib gehoerig.
 *
 * WOFUER: Die Spielmusik soll stumm sein, solange man in LumiLib ist -
 * nicht nur im Hub. Vorher fragte HubSound auf LumiHubScreen ab, und
 * damit kam die Musik zurueck, sobald der Item-Waehler aufging.
 *
 * Eine Schnittstelle statt einer Aufzaehlung von Klassennamen, damit ein
 * neues Fenster nur sie einbinden muss und nicht zusaetzlich irgendwo
 * eingetragen werden will - so ein Eintrag wird beim naechsten Fenster
 * vergessen.
 */
public interface LumiScreen {
}
