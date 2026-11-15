"""
Vorschau des Hubs - mit denselben Formeln wie der Shader und denselben
Konstanten wie der Java-Code.

WARUM DIE KONSTANTEN GELESEN UND NICHT GETIPPT WERDEN:
Die alte Vorschau hatte eigene Zahlen. Sobald im Java-Code eine geaendert
wurde, zeigte sie etwas anderes als das Spiel - und genau daran haben wir
Runden verloren. Hier werden Metrics.java und Theme.java eingelesen. Wer
dort etwas aendert, aendert die Vorschau mit.

Der Text kommt aus demselben Abstandsfeld-Atlas wie im Spiel und wird
mit derselben Rechnung ausgewertet. Die Flaechen benutzen dieselbe
Abstandsfunktion und dasselbe Uebereinanderlegen wie rounded.fsh.
"""
import json
import re
import numpy as np
from PIL import Image

SRC = "/home/claude/lumilib/src/main/java/com/lumitier/lumilib/client"
ASSETS = "/home/claude/lumilib/src/main/resources/assets/lumilib"


def java_consts(text):
    """Liest 'public static final int/float NAME = WERT;' aus Java-Text."""
    out = {}
    for m in re.finditer(
        r"public static final (int|float)\s+(\w+)\s*=\s*([^;]+);", text
    ):
        kind, name, raw = m.group(1), m.group(2), m.group(3).strip()
        try:
            if raw.startswith("0x"):
                out[name] = int(raw, 16)
            elif kind == "float":
                out[name] = float(raw.rstrip("Ff"))
            else:
                out[name] = int(raw)
        except ValueError:
            pass
    return out


def theme_palette(path, which):
    """Liest eine der beiden Paletten aus Theme.java.

    Seit dem Umschalter stehen die Werte nicht mehr bei den Feldern,
    sondern in den Methoden dark() und light(). Gelesen wird genau die
    Methode, die auch im Spiel laufen wuerde - damit die Vorschau nicht
    heimlich eine dritte Palette erfindet.

    which: "dark" oder "light"; ueber die Umgebungsvariable
    LUMILIB_THEME umstellbar, ohne das Skript anzufassen.
    """
    text = open(path, encoding="utf-8").read()
    start = text.index(f"private static void {which}() {{")
    end = text.index("\n\t}", start)
    out = java_consts(text)          # erst die Masse, die noch final sind
    for m in re.finditer(r"(\w+)\s*=\s*([^;]+);", text[start:end]):
        name, raw = m.group(1), m.group(2).strip()
        try:
            if raw.startswith("0x"):
                out[name] = int(raw, 16)
            elif raw.endswith(("F", "f")):
                out[name] = float(raw.rstrip("Ff"))
            else:
                out[name] = int(raw)
        except ValueError:
            pass
    return out


import os

M = java_consts(open(f"{SRC}/Metrics.java", encoding="utf-8").read())
T = theme_palette(f"{SRC}/Theme.java", os.environ.get("LUMILIB_THEME", "dark"))

FONTS = {}
for nm in ("regular", "bold"):
    meta = json.load(open(f"{ASSETS}/sdf/{nm}.json", encoding="utf-8"))
    img = np.asarray(Image.open(f"{ASSETS}/textures/sdf/{nm}.png").convert("RGBA"))
    FONTS[nm] = (meta, img[..., 3].astype(float) / 255.0)

Z = 2                      # GUI-Skalierung
DIST_RANGE = 12.0          # muss zu SdfText.DISTANCE_RANGE passen


def argb(v):
    return (((v >> 24) & 0xFF) / 255.0,
            np.array([(v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF]) / 255.0)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


class Canvas:
    def __init__(self, w, h, bg):
        self.w, self.h = w, h
        self.rgb = np.zeros((h, w, 3))
        self.rgb[:] = bg

    def over(self, mask, color):
        a = mask[..., None]
        self.rgb = self.rgb * (1 - a) + np.array(color) * a

    # ---- Flaechen: dieselbe Rechnung wie rounded.fsh -------------------
    def rounded(self, x, y, w, h, radius, fill, style):
        if w <= 0 or h <= 0:
            return
        rim_a = ((T["RIM_CARD"] if style == "card" else T["RIM"]) >> 24 & 0xFF) / 255
        bl_a = ((T["BEVEL_LIGHT_CARD"] if style == "card" else T["BEVEL_LIGHT"]) >> 24 & 0xFF) / 255
        bd_a = ((T["BEVEL_DARK_CARD"] if style == "card" else T["BEVEL_DARK"]) >> 24 & 0xFF) / 255
        in_a = ((T["INNER_CARD"] if style == "card" else T["INNER"]) >> 24 & 0xFF) / 255

        x0, y0 = int(x * Z), int(y * Z)
        pw, ph = int(w * Z), int(h * Z)
        yy, xx = np.mgrid[0:ph, 0:pw]
        lx = (xx + 0.5) / Z - w / 2
        ly = (yy + 0.5) / Z - h / 2
        qx = np.abs(lx) - w / 2 + radius
        qy = np.abs(ly) - h / 2 + radius
        d = (np.minimum(np.maximum(qx, qy), 0)
             + np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) - radius)

        aa = 1.0 / Z
        fillm = 1 - smoothstep(-aa, aa, d)
        gy, gx = np.gradient(d)
        ln = np.hypot(gx, gy) + 1e-12
        ny = gy / ln
        # Vorzeichen wie im Shader nach der Korrektur
        up, down = np.clip(ny, 0, 1), np.clip(-ny, 0, 1)
        ins = -d
        rim = 1 - smoothstep(0, 1.5, ins)
        bev = 1 - smoothstep(0, 3.0, ins)
        inn = 1 - smoothstep(0, 6.0, ins)

        fa, fc = argb(fill)
        col = np.broadcast_to(fc.reshape(1, 1, 3), (ph, pw, 3)).copy()
        a = np.full((ph, pw), fa)

        def layer(c, a, sc, sa):
            na = sa + a * (1 - sa)
            c = (np.array(sc) * sa[..., None] + c * (a * (1 - sa))[..., None]) \
                / np.maximum(na, 1e-5)[..., None]
            return c, na

        col, a = layer(col, a, [0, 0, 0], inn * in_a)
        col, a = layer(col, a, [1, 1, 1], bev * up * bl_a)
        col, a = layer(col, a, [0, 0, 0], bev * down * bd_a)
        col, a = layer(col, a, [1, 1, 1], rim * (0.35 + 0.65 * up) * rim_a)
        a = a * fillm

        sl = (slice(max(0, y0), min(self.h, y0 + ph)),
              slice(max(0, x0), min(self.w, x0 + pw)))
        oy, ox = max(0, -y0), max(0, -x0)
        hh = sl[0].stop - sl[0].start
        ww = sl[1].stop - sl[1].start
        if hh <= 0 or ww <= 0:
            return
        aa_ = a[oy:oy + hh, ox:ox + ww][..., None]
        cc = col[oy:oy + hh, ox:ox + ww]
        self.rgb[sl] = self.rgb[sl] * (1 - aa_) + cc * aa_

    _ICONS = None

    def _sdf_icon(self, name, x, y, w, h, color, opacity):
        """Ein Zeichen aus dem Abstandsfeld-Atlas - wie SdfIcons im Spiel.

        Die Kante wird aus dem Abstand gerechnet, nicht aus dem Bild
        skaliert. Deshalb kommt hier dasselbe heraus wie auf der
        Grafikkarte und nicht eine weichgezeichnete Verkleinerung."""
        import json
        from PIL import Image as _I
        if Canvas._ICONS is None:
            try:
                meta = json.load(open(f"{ASSETS}/sdf/icons.json", encoding="utf-8"))
                tex = np.asarray(_I.open(
                    f"{ASSETS}/textures/sdf/icons.png").convert("RGBA"))
                Canvas._ICONS = (meta, tex[..., 3].astype(np.float32) / 255.0)
            except Exception:
                Canvas._ICONS = (None, None)
        meta, field = Canvas._ICONS
        if meta is None or name not in meta["icons"]:
            return False

        cell = meta["icons"][name]
        # Zelle ist groesser als das Zeichen - siehe SdfIcons.
        box = w * meta["cell"] / meta["inner"]
        off = (box - w) / 2.0
        px = max(1, int(round(box * Z)))
        sub = field[cell["y"]:cell["y"] + cell["h"], cell["x"]:cell["x"] + cell["w"]]
        img = _I.fromarray((sub * 255).astype("uint8")).resize((px, px), _I.BILINEAR)
        d = np.asarray(img).astype(np.float32) / 255.0 - 0.5
        # Kantenbreite: ein Zielpixel, wie DISTANCE_RANGE im Shader.
        rng = 2.0 * meta["spread"] * px / cell["h"]
        alpha = np.clip(d * rng + 0.5, 0.0, 1.0) * opacity
        a_, c_ = argb(color) if color is not None else (1.0, np.array([1.0, 1.0, 1.0]))
        alpha = alpha * a_
        rgb = np.broadcast_to(c_.reshape(1, 1, 3), alpha.shape + (3,))
        x0, y0 = int((x - off) * Z), int((y - off) * Z)
        dy1, dx1 = min(self.h, y0 + alpha.shape[0]), min(self.w, x0 + alpha.shape[1])
        if dy1 <= y0 or dx1 <= x0 or x0 < 0 or y0 < 0:
            return True
        a2 = alpha[:dy1 - y0, :dx1 - x0][..., None]
        c2 = rgb[:dy1 - y0, :dx1 - x0]
        reg = self.rgb[y0:dy1, x0:dx1]
        self.rgb[y0:dy1, x0:dx1] = reg * (1 - a2) + c2 * a2
        return True

    def sprite(self, name, x, y, w, h, color=None, radius=None, opacity=1.0):
        """Echtes Sprite aus dem Projekt, bei Bedarf eingefaerbt.

        radius rundet die Ecken wie Sdf.roundedTexture im Spiel."""
        from PIL import Image as _I
        im = None
        # Erst die GUI-Sprites, dann die mitgelieferten Katalog-Icons -
        # dieselben zwei Orte, aus denen der Hub zieht.
        if "/" in name:
            folders, name = (name.rsplit("/", 1)[0],), name.rsplit("/", 1)[1]
            folders = (f"textures/{folders[0]}",)
        else:
            folders = ("textures/gui/sprites", "textures/mod_catalog")
        for folder in folders:
            try:
                im = _I.open(f"{ASSETS}/{folder}/{name}.png").convert("RGBA")
                break
            except FileNotFoundError:
                continue
        if im is None:
            # Kein PNG - dann liegt das Zeichen im Abstandsfeld-Atlas.
            # Seit SdfIcons ist das der Normalfall fuer alle Lucide-Zeichen;
            # die PNGs daneben sind nur noch Rueckfall fuer alte Spielstaende.
            if self._sdf_icon(name, x, y, w, h, color, opacity):
                return
            return
        im = im.resize((max(1, int(w * Z)), max(1, int(h * Z))), _I.LANCZOS)
        arr = np.asarray(im).astype(float) / 255.0
        alpha = arr[..., 3]
        rgb = arr[..., :3]
        if color is not None:
            a_, c_ = argb(color)
            alpha = alpha * a_
            rgb = np.broadcast_to(c_.reshape(1, 1, 3), rgb.shape)
        if opacity != 1.0:
            alpha = alpha * opacity
        if radius is not None:
            # Abstand zum naechsten Punkt des eingerueckten Rechtecks -
            # dieselbe Abstandsfunktion wie im Shader, nur in numpy.
            r = max(0.0, radius * Z)
            hh, ww = alpha.shape
            yy, xx = np.mgrid[0:hh, 0:ww]
            qx = np.abs(xx + 0.5 - ww / 2.0) - (ww / 2.0 - r)
            qy = np.abs(yy + 0.5 - hh / 2.0) - (hh / 2.0 - r)
            d = (np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2)
                 + np.minimum(np.maximum(qx, qy), 0) - r)
            alpha = alpha * np.clip(0.5 - d, 0.0, 1.0)
        x0, y0 = int(x * Z), int(y * Z)
        dy1, dx1 = min(self.h, y0 + arr.shape[0]), min(self.w, x0 + arr.shape[1])
        if dy1 <= y0 or dx1 <= x0:
            return
        a2 = alpha[:dy1 - y0, :dx1 - x0][..., None]
        c2 = rgb[:dy1 - y0, :dx1 - x0]
        reg = self.rgb[y0:dy1, x0:dx1]
        self.rgb[y0:dy1, x0:dx1] = reg * (1 - a2) + c2 * a2

    def rect(self, x, y, w, h, color):
        a, c = argb(color)
        x0, y0 = int(x * Z), int(y * Z)
        sl = (slice(max(0, y0), min(self.h, y0 + max(1, int(h * Z)))),
              slice(max(0, x0), min(self.w, x0 + int(w * Z))))
        self.rgb[sl] = self.rgb[sl] * (1 - a) + c * a

    # ---- Text: dieselbe Rechnung wie sdf_text.fsh ----------------------
    def ink_top_offset(self, px, bold=False):
        """SdfFont.inkTopOffset - Rand des Abstandsfeldes in Pixeln."""
        meta, _ = FONTS["bold" if bold else "regular"]
        pad = meta.get("pad", meta["spread"] + 2)
        return pad * px / meta["size"]

    def descent_of(self, s, px, bold=False):
        """SdfFont.descentOf - gemessene Unterlaenge dieser Zeichenkette."""
        meta, _ = FONTS["bold" if bold else "regular"]
        g = meta["glyphs"]
        h = g.get("H")
        if not h:
            return 0.0
        baseline = h["by"] + h["h"]
        deepest = 0.0
        for ch in s:
            gl = g.get(ch)
            if gl:
                deepest = max(deepest, gl["by"] + gl["h"] - baseline)
        return deepest * px / meta["size"]

    def cap_centre_of(self, center_y, cap, descender, px, bold=False):
        """Ui.capCentreOf - sichtbare Mitte der Versalien einer Zeile, die
        mit drawCenteredLine auf center_y gesetzt wird."""
        top = center_y - (cap + descender + 1) // 2
        return top + self.ink_top_offset(px, bold) + cap / 2.0

    def x_height(self, px, bold=False):
        """SdfFont.xHeight - Tintenhoehe des x."""
        meta, _ = FONTS["bold" if bold else "regular"]
        g = meta["glyphs"].get("x")
        if not g:
            return self.cap_height(px, bold)
        pad = meta.get("pad", meta["spread"] + 2)
        return (g["h"] - 2 * pad) * px / meta["size"]

    def wrap(self, s, max_width, px, bold=False):
        """Ui.wrap - Umbruch an Wortgrenzen, gemessen in derselben Staffel."""
        out, line = [], ""
        if not s or max_width <= 0:
            return out
        for word in s.split(" "):
            if not line:
                line = word
                continue
            probe = line + " " + word
            if self.width(probe, px, bold) <= max_width:
                line = probe
            else:
                out.append(line)
                line = word
        if line:
            out.append(line)
        return out

    def cap_height(self, px, bold=False):
        """SdfFont.capHeight - Tintenhoehe des H."""
        meta, _ = FONTS["bold" if bold else "regular"]
        g = meta["glyphs"].get("H")
        pad = meta.get("pad", meta["spread"] + 2)
        return (g["h"] - 2 * pad) * px / meta["size"]

    def cap_block_centre_of(self, center_y, cap, descender, px, bold=False):
        """Ui.capBlockCentreOf - Mitte Oberkante Versalien bis Schreiblinie."""
        top = center_y - (cap + descender + 1) // 2
        return top + self.ink_top_offset(px, bold) + self.cap_height(px, bold) / 2.0

    def optical_centre_of(self, center_y, cap, descender, px, bold=False):
        """Ui.opticalCentreOf - Mitte des x-Bandes, nicht des Versalblocks."""
        c = self.cap_centre_of(center_y, cap, descender, px, bold)
        return c + (self.cap_height(px, bold) - self.x_height(px, bold)) / 4.0

    def line_centre_in(self, top, height, cap, descender, px, bold=False):
        """Ui.lineCentreIn - Versalblock mittig im Rechteck."""
        ch = self.cap_height(px, bold)
        capTop = top + (height - ch) / 2.0
        return round(capTop - self.ink_top_offset(px, bold) + (cap + descender + 1) // 2)

    def text_centered_in(self, s, x, top, height, px, color, cap, bold=False):
        """Ui.drawCenteredIn - eine Zeile mittig im Rechteck."""
        below = self.descent_of(s, px, bold)
        t = top + height / 2.0 - self.ink_top_offset(px, bold) - (cap + below) / 2.0
        self.text(s, x, round(t), px, color, bold=bold)

    def text(self, s, x, top, px, color, bold=False):
        meta, atlas = FONTS["bold" if bold else "regular"]
        sc = px / meta["size"]
        a_, c_ = argb(color)
        h_glyph = meta["glyphs"].get("H")
        origin = top - (h_glyph["by"] if h_glyph else 0) * sc
        pen = x
        prev = None
        for ch in s:
            g = meta["glyphs"].get(ch)
            if g is None:
                continue
            if prev:
                pen += meta["kern"].get(prev, {}).get(ch, 0.0) * sc
            if g["w"] > 0:
                sub = atlas[g["y"]:g["y"] + g["h"], g["x"]:g["x"] + g["w"]]
                tw = max(1, round(g["w"] * sc * Z))
                th = max(1, round(g["h"] * sc * Z))
                samp = np.asarray(
                    Image.fromarray((sub * 255).astype(np.uint8))
                    .resize((tw, th), Image.BILINEAR)).astype(float) / 255.0
                rng = DIST_RANGE * sc * Z / 2.0
                alpha = np.clip((samp - 0.5) * 2.0 * rng + 0.5, 0, 1) * a_
                gx = int(round((pen + g["bx"] * sc) * Z))
                gy = int(round((origin + g["by"] * sc) * Z))
                sx, sy = max(0, -gx), max(0, -gy)
                dx0, dy0 = max(0, gx), max(0, gy)
                dx1, dy1 = min(self.w, gx + tw), min(self.h, gy + th)
                if dx1 > dx0 and dy1 > dy0:
                    sub_a = alpha[sy:sy + dy1 - dy0, sx:sx + dx1 - dx0][..., None]
                    reg = self.rgb[dy0:dy1, dx0:dx1]
                    self.rgb[dy0:dy1, dx0:dx1] = reg * (1 - sub_a) + c_ * sub_a
            pen += g["adv"] * sc
            prev = ch
        return pen - x

    def width(self, s, px, bold=False):
        meta, _ = FONTS["bold" if bold else "regular"]
        sc = px / meta["size"]
        pen, prev = 0.0, None
        for ch in s:
            g = meta["glyphs"].get(ch)
            if g is None:
                continue
            if prev:
                pen += meta["kern"].get(prev, {}).get(ch, 0.0) * sc
            pen += g["adv"] * sc
            prev = ch
        return pen

    def save(self, path):
        Image.fromarray((np.clip(self.rgb, 0, 1) * 255).astype(np.uint8)).save(path)
