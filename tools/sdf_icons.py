"""
Erzeugt einen Icon-Atlas mit ABSTANDSFELD statt Pixeln.

WARUM, obwohl icons.py doch schon Icons erzeugt:
icons.py rastert die Lucide-SVGs EINMAL auf 32 Pixel. Minecraft zieht
das Bild danach auf die Groesse, in der es wirklich gezeichnet wird -
bei GUI-Skalierung 3 oder 4 sind das 27 bis 48 echte Pixel aus einer
32-Pixel-Vorlage, und bei einem 9 Pixel grossen Zeichen im Menue
umgekehrt eine Verkleinerung. Beides sieht man: der Strich wird
stufig oder matschig.

Ein Abstandsfeld speichert pro Bildpunkt nicht "hier ist Tinte",
sondern "so weit bin ich vom Rand der Form entfernt". Die Kante
rechnet der Shader in der Zielgroesse neu aus. Damit ist ein Zeichen
bei 9 wie bei 48 Pixeln gleich scharf - dasselbe Verfahren, das
Schrift und runde Ecken hier schon benutzen.

WARUM DIE WERTE ZU sdf_atlas.py PASSEN MUESSEN:
Der Shader bekommt die Reichweite des Felds als Konstante
(DISTANCE_RANGE, dort 12 = 2 * spread bei spread 6). Icons laufen
durch dieselbe Pipeline wie die Schrift, also muss spread hier
derselbe sein. Mit einem anderen Wert waere die Kante entweder
ausgefranst oder hart.

Ausfuehren aus dem Projektordner:  python3 tools/sdf_icons.py
Braucht:                           pip install cairosvg pillow numpy scipy

LIZENZ: Die Vorlagen sind Lucide, ISC - siehe tools/lucide/LICENSE-Lucide.txt
und die NOTICE des Projekts.
"""
import json
import os
import re

import cairosvg
import numpy as np
from PIL import Image
from scipy.ndimage import distance_transform_edt

SRC = "tools/lucide"
TEX = "src/main/resources/assets/lumilib/textures/sdf/icons.png"
META = "src/main/resources/assets/lumilib/sdf/icons.json"

# Kante einer Zelle im Atlas. Wie bei der Schrift: hoeher kostet Platz,
# nicht Qualitaet - die kommt aus dem Abstandsfeld.
CELL = 64
# Muss zu DISTANCE_RANGE im Shader passen: dort 12, also hier 6.
SPREAD = 6
# Ueberabtastung beim Rastern, glaettet die Konturtreppe vor der Messung.
SS = 8
# Spalten im Atlas. 4 x 5 Zellen reichen fuer den Vorrat unten.
COLS = 4

# Strichstaerke. Lucide zeichnet mit 2 auf 24 Einheiten. Hier dicker,
# weil ein Zeichen im Menue nur 9 bis 13 Pixel hoch ist und ein duenner
# Strich dort ausbleicht - beim Abstandsfeld sogar mehr als beim
# Pixelbild, weil die Kante weich gerechnet wird und ein zu schmaler
# Strich sich dabei selbst wegfrisst.
STROKE = 2.6

# Welcher Name im Spiel auf welche Vorlage zeigt. Der Name links ist,
# was der Java-Code anfragt; rechts steht die Lucide-Datei.
ICONS = {
    "search": "search",
    "pin": "pin",
    "undo": "rotate-ccw",
    "check": "check",
    "close": "x",
    "plus": "plus",
    "minus": "minus",
    "expand": "chevron-right",
    "open": "chevron-down",
    "collapse": "chevron-up",
    "trash": "trash",
    "settings": "settings",
    "info": "info",
    "spinner": "loader-circle",
    "up": "arrow-up",
    "down": "arrow-down",
    "grip": "grip-vertical",
    "dot": "circle-small",
}


def raster(path, px):
    """SVG auf px x px rastern und nur den Deckkraftkanal zurueckgeben."""
    with open(path, "r", encoding="utf-8") as fh:
        svg = fh.read()
    # Strichstaerke anheben - siehe STROKE.
    svg = re.sub(r'stroke-width="[^"]*"', f'stroke-width="{STROKE}"', svg)
    # Lucide zeichnet mit currentColor. Ohne Festlegung bleibt das
    # durchsichtig, und dann ist die Maske leer.
    svg = svg.replace('stroke="currentColor"', 'stroke="#000000"')
    png = cairosvg.svg2png(bytestring=svg.encode("utf-8"),
                           output_width=px, output_height=px)
    import io
    img = Image.open(io.BytesIO(png)).convert("RGBA")
    return np.asarray(img)[:, :, 3].astype(np.float32) / 255.0


def sdf(alpha, cell, spread, ss):
    """
    Vorzeichenbehafteter Abstand, in Atlaspixeln, auf 0..1 abgebildet.

    Gemessen wird im ueberabgetasteten Bild und danach verkleinert -
    nicht umgekehrt. Wer erst verkleinert, misst den Abstand zu einer
    schon verwaschenen Kante.
    """
    mask = alpha > 0.5
    if not mask.any():
        return np.full((cell, cell), 0.0, dtype=np.float32)

    out = distance_transform_edt(~mask)
    ins = distance_transform_edt(mask)
    # Innen positiv, aussen negativ, in ueberabgetasteten Pixeln.
    signed = ins - out

    # Auf Atlasauflösung mitteln: Blockmittel ueber ss x ss.
    big = signed.reshape(cell, ss, cell, ss).mean(axis=(1, 3))
    # Von ueberabgetasteten in Atlaspixel.
    big = big / ss

    # 0,5 ist die Kontur - genauso liest es der Shader.
    return np.clip(0.5 + big / (2.0 * spread), 0.0, 1.0).astype(np.float32)


def main():
    names = sorted(ICONS)
    rows = (len(names) + COLS - 1) // COLS
    atlas = np.zeros((rows * CELL, COLS * CELL), dtype=np.float32)
    # inner ist die Kante, auf die das 24er-Feld der Vorlage gerastert
    # wurde. Der Rest der Zelle ist Rand fuer die Reichweite des Feldes.
    # Java braucht den Wert, um die Zelle so zu zeichnen, dass die
    # ANGEFRAGTE Groesse dem Vorlagenfeld entspricht und nicht der Zelle -
    # sonst waere jedes Zeichen rund ein Fuenftel zu klein.
    meta = {"spread": SPREAD, "cell": CELL, "inner": CELL - 2 * SPREAD,
            "atlasW": COLS * CELL, "atlasH": rows * CELL,
            "icons": {}}

    for i, name in enumerate(names):
        path = os.path.join(SRC, ICONS[name] + ".svg")
        if not os.path.exists(path):
            raise SystemExit(f"fehlt: {path}")
        # Platz fuer die Reichweite des Feldes am Rand lassen, sonst
        # reisst der Abstand an der Zellkante ab.
        inner = CELL - 2 * SPREAD
        a = raster(path, inner * SS)
        # In die Zelle einbetten, mittig.
        full = np.zeros((CELL * SS, CELL * SS), dtype=np.float32)
        off = SPREAD * SS
        full[off:off + inner * SS, off:off + inner * SS] = a

        field = sdf(full, CELL, SPREAD, SS)
        cx = (i % COLS) * CELL
        cy = (i // COLS) * CELL
        atlas[cy:cy + CELL, cx:cx + CELL] = field
        meta["icons"][name] = {"x": cx, "y": cy, "w": CELL, "h": CELL}

    # Das Feld steht im Deckkraftkanal - so liest es der Shader.
    rgba = np.zeros((atlas.shape[0], atlas.shape[1], 4), dtype=np.uint8)
    rgba[:, :, 0:3] = 255
    rgba[:, :, 3] = np.round(atlas * 255.0).astype(np.uint8)

    os.makedirs(os.path.dirname(TEX), exist_ok=True)
    os.makedirs(os.path.dirname(META), exist_ok=True)
    Image.fromarray(rgba, "RGBA").save(TEX)
    with open(META, "w", encoding="utf-8") as fh:
        json.dump(meta, fh, indent=1, sort_keys=True)

    print(f"{len(names)} Zeichen, Atlas {COLS * CELL}x{rows * CELL}")
    print(TEX)
    print(META)


if __name__ == "__main__":
    main()
