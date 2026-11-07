"""
Erzeugt einen Zeichensatz-Atlas mit ABSTANDSFELD statt Pixeln.

WARUM UEBERHAUPT:
Minecraft rastert eine TTF EINMAL in fester Groesse und skaliert danach
das fertige Bild. Deshalb sieht die Schrift neben geglaetteten Kanten
aus wie aufgeklebt - das ist kein Einstellungsfehler, sondern das
Verfahren.

Ein Abstandsfeld speichert pro Bildpunkt nicht "hier ist Tinte", sondern
"so weit bin ich vom Rand des Buchstabens entfernt". Der Shader rechnet
die Kante dann in der Groesse neu aus, in der wirklich gezeichnet wird -
genau wie bei den runden Ecken, die schon laufen.

WARUM VORGEBACKEN STATT ZUR LAUFZEIT:
Zur Laufzeit zu rechnen hiesse stb_truetype im Spiel aufzurufen und das
Ergebnis nicht pruefen zu koennen. Hier kann ich den Atlas messen, bevor
irgendetwas gebaut wird. Der Preis ist ein fester Zeichenvorrat - fuer
eine Oberflaeche reicht das.
"""
import json
import numpy as np
from PIL import Image, ImageFont
from scipy.ndimage import distance_transform_edt

# Groesse einer Zelle im Atlas. Hoeher kostet Platz, nicht Qualitaet -
# die Qualitaet kommt aus dem Abstandsfeld, nicht aus der Aufloesung.
GLYPH_PX = 48
# Reichweite des Abstandsfelds in Atlas-Pixeln. Zu klein -> die Kante
# reisst bei starker Vergroesserung ab. Zu gross -> Praezision geht
# verloren, weil 256 Stufen auf mehr Strecke verteilt werden.
SPREAD = 6
# Ueberabtastung beim Rastern. Die Maske wird so viel groesser erzeugt
# und danach verkleinert - das glaettet die Konturtreppe.
SS = 8
PAD = SPREAD + 2

CHARSET = (
    "".join(chr(c) for c in range(0x20, 0x7F))
    + "äöüÄÖÜß"
    + "–—·…×→✓"
)


def build(ttf_path, out_png, out_json, name):
    big = ImageFont.truetype(ttf_path, GLYPH_PX * SS)
    asc_big, desc_big = big.getmetrics()

    glyphs = {}
    cells = []
    for ch in CHARSET:
        adv = big.getlength(ch) / SS
        bbox = big.getbbox(ch)
        if bbox is None or bbox[2] <= bbox[0] or bbox[3] <= bbox[1]:
            glyphs[ch] = dict(adv=adv, w=0, h=0, bx=0, by=0)
            continue
        x0, y0, x1, y1 = bbox
        mask = big.getmask(ch, mode="L")
        mw, mh = mask.size
        arr = np.asarray(mask, dtype=np.uint8).reshape(mh, mw)

        # Rand fuer das Abstandsfeld, in GROSSEN Pixeln
        p = PAD * SS
        padded = np.zeros((mh + 2 * p, mw + 2 * p), dtype=np.uint8)
        padded[p:p + mh, p:p + mw] = arr

        inside = padded > 127
        # Abstand nach aussen und nach innen, dann vorzeichenbehaftet
        d_out = distance_transform_edt(~inside)
        d_in = distance_transform_edt(inside)
        signed = (d_in - d_out) / SS          # in Zielpixeln
        # auf 0..1 abbilden: 0.5 ist genau die Kontur
        field = np.clip(signed / (2.0 * SPREAD) + 0.5, 0.0, 1.0)

        tw = max(1, round(field.shape[1] / SS))
        th = max(1, round(field.shape[0] / SS))
        small = np.asarray(
            Image.fromarray((field * 255).astype(np.uint8)).resize((tw, th), Image.BILINEAR)
        )
        cells.append((ch, small))
        glyphs[ch] = dict(
            adv=adv,
            w=tw, h=th,
            bx=x0 / SS - PAD,
            by=y0 / SS - PAD,
        )

    # Packen: einfache Zeilen, Breite 512
    AW = 512
    x = y = rowh = 0
    placements = {}
    for ch, img in cells:
        h, w = img.shape
        if x + w > AW:
            x = 0
            y += rowh + 1
            rowh = 0
        placements[ch] = (x, y)
        x += w + 1
        rowh = max(rowh, h)
    AH = 1
    while AH < y + rowh:
        AH *= 2

    atlas = np.zeros((AH, AW), dtype=np.uint8)
    for ch, img in cells:
        px, py = placements[ch]
        h, w = img.shape
        atlas[py:py + h, px:px + w] = img
        g = glyphs[ch]
        g["x"], g["y"] = px, py

    rgba = np.zeros((AH, AW, 4), dtype=np.uint8)
    rgba[..., 0] = rgba[..., 1] = rgba[..., 2] = 255
    rgba[..., 3] = atlas
    Image.fromarray(rgba, "RGBA").save(out_png)

    # Unterschneidung: nur Paare mit spuerbarer Wirkung. Ohne sie laeuft
    # Text 2-3 Prozent zu breit, bei Paaren wie "AV" acht Prozent.
    single = {c: big.getlength(c) for c in CHARSET}
    kern = {}
    for a in CHARSET:
        for b in CHARSET:
            k = (big.getlength(a + b) - single[a] - single[b]) / SS
            if abs(k) > 0.02:
                kern.setdefault(a, {})[b] = round(k, 3)

    meta = dict(
        name=name,
        kern=kern,
        size=GLYPH_PX,
        spread=SPREAD,
        # Der Rand um jedes Zeichen. Steht mit in der Datei, damit der
        # Zeichner ihn nicht nachrechnen muss - er braucht ihn, um Text
        # senkrecht mittig zu setzen.
        pad=PAD,
        atlasW=AW, atlasH=AH,
        ascent=asc_big / SS,
        descent=desc_big / SS,
        glyphs={ch: glyphs[ch] for ch in CHARSET if ch in glyphs},
    )
    with open(out_json, "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, separators=(",", ":"))
    return meta, AW, AH


if __name__ == "__main__":
    base = "/home/claude/lumilib/src/main/resources/assets/lumilib"
    for src, nm in [("ui_regular", "regular"), ("ui_bold", "bold")]:
        meta, w, h = build(
            f"{base}/font/{src}.ttf",
            f"/tmp/claude-0/atlas_{nm}.png",
            f"/tmp/claude-0/atlas_{nm}.json",
            nm,
        )
        print(f"{nm:8s} Atlas {w}x{h}  {len(meta['glyphs'])} Zeichen  "
              f"Oberlaenge {meta['ascent']:.1f}  Unterlaenge {meta['descent']:.1f}")
