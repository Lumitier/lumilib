"""Standard-Icon fuer Mods ohne eigenes Bild.

WARUM EIN WUERFEL und kein Umriss:
Das Symbol wird in der Sidebar auf 13 logische Pixel gezeichnet, das
Innere davon auf etwa 8. Ein Strichzeichen hat dort keine Chance - es
wird zu Grau. Also eine gefuellte Form, Tiefe nur ueber drei
Deckkraftstufen. Die Textur ist WEISS, die Farbe kommt zur Laufzeit.
"""
from PIL import Image, ImageDraw

SS = 8                      # Ueberabtastung
OUT = 64

def cube(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def p(x, y):
        return (x * size, y * size)

    T  = p(0.50, 0.095)
    R1 = p(0.925, 0.325)
    R2 = p(0.925, 0.700)
    B  = p(0.50, 0.930)
    L2 = p(0.075, 0.700)
    L1 = p(0.075, 0.325)
    C  = p(0.50, 0.527)

    # Deckel am hellsten, rechte Wand am dunkelsten - so liest sich der
    # Koerper auch dann noch als Koerper, wenn er nur acht Pixel hat.
    d.polygon([T, R1, C, L1], fill=(255, 255, 255, 255))
    d.polygon([L1, C, B, L2], fill=(255, 255, 255, 198))
    d.polygon([R1, R2, B, C], fill=(255, 255, 255, 142))
    return img

big = cube(OUT * SS)
small = big.resize((OUT, OUT), Image.LANCZOS)
small.save("/home/claude/lumilib/src/main/resources/assets/lumilib/textures/gui/sprites/mod_default.png")

with open("/home/claude/lumilib/src/main/resources/assets/lumilib/textures/gui/sprites/mod_default.png.mcmeta", "w") as f:
    f.write('{\n  "gui": {\n    "scaling": {\n      "type": "stretch"\n    }\n  }\n}')

print("geschrieben", small.size)
