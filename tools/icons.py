"""
Erzeugt die kleinen GUI-Zeichen aus den Lucide-SVGs in tools/lucide/.

WARUM NICHT MEHR SELBST GEZEICHNET:
Die erste Fassung hat Lupe, Stecknadel und Pfeil mit Kreisen, Strecken
und Dreiecken zusammengesetzt. Bei 9 bis 13 Pixeln entscheidet aber
nicht die Idee, sondern wie ein Strich in eine Ecke laeuft und wo er
abgerundet ist. Lucide loest genau das seit Jahren besser.

LIZENZ: Lucide steht unter ISC - benutzen, aendern und weitergeben ist
erlaubt, solange der Copyright-Hinweis mitlaeuft. Der liegt daneben in
LICENSE-Lucide.txt und ist in der NOTICE des Projekts genannt.

STRICHSTAERKE: Lucide zeichnet mit 2 auf 24 Einheiten. Hier etwas
dicker, weil die Zeichen bei GUI-Skalierung 3 bis 4 auf 27 bis 48 echte
Pixel hochgezogen werden und ein duenner Strich dabei ausbleicht.

ZIELGROESSE 32 statt 16 Pixel: siehe oben - aus 16 wird dabei Matsch.

Ausfuehren aus dem Projektordner:  python3 tools/icons.py
Braucht cairosvg:                  pip install cairosvg
"""
import re

import cairosvg
from PIL import Image

SRC = "tools/lucide"
DST = "src/main/resources/assets/lumilib/textures/gui/sprites"
OUT = 32
SUPER = 8

# Lucide-Datei -> (Name im Spiel, Strichstaerke)
JOBS = {
    "rotate-ccw": ("undo", 2.35),
    "pin":        ("pin", 2.30),
    "search":     ("search", 2.50),
}

MCMETA = '{\n  "gui": {\n    "scaling": {\n      "type": "stretch"\n    }\n  }\n}'


def build(source, name, stroke):
    svg = open(f"{SRC}/{source}.svg").read()
    # Weiss, damit LumiLib die Farbe beim Zeichnen bestimmen kann.
    svg = svg.replace('stroke="currentColor"', 'stroke="#ffffff"')
    svg = re.sub(r'stroke-width="2"', f'stroke-width="{stroke}"', svg)

    big = f"/tmp/_lumilib_{name}.png"
    cairosvg.svg2png(bytestring=svg.encode(), write_to=big,
                     output_width=OUT * SUPER, output_height=OUT * SUPER,
                     background_color="transparent")
    Image.open(big).resize((OUT, OUT), Image.LANCZOS).save(f"{DST}/{name}.png")
    with open(f"{DST}/{name}.png.mcmeta", "w") as f:
        f.write(MCMETA)


if __name__ == "__main__":
    for source, (name, stroke) in JOBS.items():
        build(source, name, stroke)
    print(", ".join(n for n, _ in JOBS.values()), "geschrieben")
