"""
Zieht aus den Mod-Logos die blosse Zeichnung heraus, ohne Kachel.

WOZU:
Die Logos sind eine goldene Zeichnung auf cremefarbenem Grund - als
App-Symbol richtig so, und in den Karten des Hubs sehen sie auch genau
so aus wie auf Modrinth. In der SIDEBAR sind sie 13 Pixel gross und
stehen neben Text. Dort ist die Kachel kein Symbol mehr, sondern ein
heller Klotz: Auf dunklem Grund zieht das Auge zuerst auf acht leuchtende
Quadrate und erst danach auf die Namen daneben. Genau das sah falsch aus.

Hier wird der Grund entfernt und nur die Zeichnung behalten. In der
Sidebar steht dann ein goldenes Zeichen auf dem Fenstergrund - wie die
Lucide-Zeichen daneben, nur eben das Logo der jeweiligen Mod.

WIE DAS GEHT, OHNE ZU RATEN:
Die Logos haben genau zwei Farben. Jeder Bildpunkt ist eine Mischung aus
beiden, auch die weichen Kanten. Der Mischanteil laesst sich deshalb
ausrechnen, statt mit einem Schwellwert zu schneiden - und genau dadurch
bleiben die Kanten weich statt ausgefranst.

Gemessen statt angenommen: Die Werte unten stammen aus den Dateien
selbst (haeufigste und zweithaeufigste Farbe), nicht aus dem Logo-Rezept.

Ausfuehren aus dem Projektordner:  python3 tools/mod_glyphs.py
"""
import glob
import os

import numpy as np
from PIL import Image

SRC = "src/main/resources/assets/lumilib/textures/mod_catalog"
DST = "src/main/resources/assets/lumilib/textures/mod_glyph"

# Hintergrund und Zeichnung, wie in den Dateien gemessen.
BG = np.array([243, 238, 235], dtype=float)
FG = np.array([232, 163, 61], dtype=float)


def glyph(path):
    img = Image.open(path).convert("RGBA")
    a = np.asarray(img).astype(float)
    rgb, alpha = a[:, :, :3], a[:, :, 3] / 255.0

    # Mischanteil: Wo liegt der Bildpunkt auf der Strecke BG -> FG?
    # Projektion auf diese Strecke, damit auch Bildpunkte zaehlen, die
    # durch Kantenglaettung leicht daneben liegen.
    d = FG - BG
    t = ((rgb - BG) @ d) / float(d @ d)
    t = np.clip(t, 0.0, 1.0)

    out = np.zeros_like(a, dtype=np.uint8)
    out[:, :, 0] = int(FG[0])
    out[:, :, 1] = int(FG[1])
    out[:, :, 2] = int(FG[2])
    out[:, :, 3] = np.round(t * alpha * 255.0).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def main():
    os.makedirs(DST, exist_ok=True)
    n = 0
    for path in sorted(glob.glob(os.path.join(SRC, "*.png"))):
        name = os.path.basename(path)
        g = glyph(path)
        # Deckkraft messen: Ein leeres Ergebnis hiesse, dass die Datei
        # nicht die erwarteten zwei Farben hat - dann lieber auffallen
        # als eine unsichtbare Datei schreiben.
        ink = int((np.asarray(g)[:, :, 3] > 8).sum())
        if ink < 50:
            raise SystemExit(f"{name}: keine Zeichnung gefunden ({ink} Punkte)")
        g.save(os.path.join(DST, name))
        print(f"{name:26s} {ink:6d} Punkte")
        n += 1
    print(f"{n} Zeichen nach {DST}")


if __name__ == "__main__":
    main()
