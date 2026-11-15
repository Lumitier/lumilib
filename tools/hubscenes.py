"""Zeichnet die Hub-Ansichten - Reihenfolge und Zahlen wie LumiHubScreen."""
import numpy as np
from PIL import Image, ImageFilter
import os
from hubpreview import Canvas, M, T, Z, ASSETS

# Schalter fuer die Varianten-Vorschau, Normalfall beide aus
CENTER_SIDEBAR = False
CENTER_TITLE = False

LW, LH = 960, 540                      # logische Groesse bei Skalierung 2
BODY_H, SMALL_H, TITLE_H_CAP = 9, 7, 13
HEAD_H = 11
BODY_D = 4
ADV = BODY_H + BODY_D + 2              # Ui.advance(BODY_H, BODY_D, 2)
ADV3 = BODY_H + BODY_D + 3

# Hoehe des zweizeiligen Blocks - dieselbe Rechnung wie
# SettingsList.descBlockH(). SET_DESC_GAP misst Oberkante zu Oberkante
# und enthaelt BODY_H schon; wer es nochmal addiert, haengt die Luft
# unten an.
DESC_BLOCK = M["SET_DESC_GAP"] + SMALL_H + 3

# Reihenfolge, Namen und Kennungen wie in LumiCatalog.KNOWN. Wer dort
# etwas aendert, muss es hier mitfuehren - die Vorschau liest den Katalog
# nicht ein, weil er Java ist und keine Daten.
MODS = [
    ("LumiLib", "1.5.4", "Shared foundation and hub for Lumitier's mods.", "ok"),
    ("LumiLibControl", "1.1.0", "Lets a server switch single mods off.", "ok"),
    ("Resource Pack Studio", "", "Not installed.", "miss"),
    ("IP Address Hider", "1.2.0", "Hides server addresses on screen.", "ok"),
    ("Perpetual Snow", "1.3.0", "Keeps the world under snow.", "ok"),
    ("Shadow Tweaks", "1.2.0", "Control over entity shadows.", "ok"),
    ("Item Drop Tweaks", "1.2.0", "Control over dropped items.", "ok"),
    ("Experience Orb Tweaks", "", "Not installed.", "miss"),
]

IDS = {
    "LumiLib": "lumilib",
    "LumiLibControl": "lumilibcontrol",
    "Resource Pack Studio": "resourcepackstudio",
    "IP Address Hider": "ipaddresshider",
    "Perpetual Snow": "perpetualsnow",
    "Shadow Tweaks": "shadowtweaks",
    "Item Drop Tweaks": "itemdroptweaks",
    "Experience Orb Tweaks": "xporbtweaks",
}


def backdrop():
    bg = Image.open("/tmp/claude-0/bg_hell.png").convert("RGB")
    bg = bg.resize((LW * Z, LH * Z), Image.LANCZOS).filter(ImageFilter.GaussianBlur(9 * Z))
    return np.asarray(bg).astype(float) / 255.0


class Hub:
    def __init__(self):
        self.c = Canvas(LW * Z, LH * Z, 0)
        self.c.rgb = backdrop()
        self.c.rect(0, 0, LW, LH, T["SCRIM"])

        self.pw = max(280, int(LW * M["PANEL_W"]))
        self.ph = max(180, int(LH * M["PANEL_H"]))
        self.px = (LW - self.pw) // 2
        self.py = (LH - self.ph) // 2
        pad = M["PAD"]
        self.sw = max(110, int(self.pw * M["SIDEBAR_W"]))
        self.sx, self.sy = self.px + pad, self.py + pad
        self.sh = self.ph - pad * 2
        self.cx = self.sx + self.sw + pad
        self.cy = self.py + pad
        self.cw = self.px + self.pw - pad - self.cx
        self.ch = self.sh
        self.listTop = self.cy + M["TITLE_H"] + 6
        self.listBottom = self.cy + self.ch

        self.c.rounded(self.px, self.py, self.pw, self.ph, 9, T["PANEL"], "panel")
        self.c.rounded(self.sx, self.sy, self.sw, self.sh, 6, T["SIDEBAR"], "card")

    # ---------------- Sidebar ----------------
    def account_h(self):
        return max(M["ACCOUNT_H"], max(M["HEAD"], ADV + SMALL_H))

    def mods_top(self):
        return (self.sy + M["PAD"] + (M["ACCOUNT_H"] + 10 + 9)
                + M["ROW_H"] + 9)

    def footer_h(self):
        return max(M["ACCOUNT_H"], max(M["HEAD"], ADV + SMALL_H))

    def sidebar(self, selected):
        c = self.c
        x = self.sx + M["PAD"]
        w = self.sw - M["PAD"] * 2
        y = self.sy + M["PAD"]

        # Kontoblock: Kopf und Name auf einer Mittellinie
        rowH = self.account_h()
        mid = y + rowH // 2
        c.rect(x, mid - M["HEAD"] // 2, M["HEAD"], M["HEAD"], 0xFF8C6650)
        c.text("Player887", x + M["HEAD"] + 8, mid - (BODY_H + 4 + 1) // 2, 12.5,
               T["TEXT"], bold=True)
        y += rowH + 10
        c.rect(x, y, w, 1, T["DIVIDER"])
        y += 9

        c.text("No updates", x, y + 3, 12.5, T["TEXT_FAINT"])
        y += M["ROW_H"] + 4
        c.rect(x, y, w, 1, T["DIVIDER"])

        # All-Mods-Knopf, darunter der Trennstrich
        ay = self.mods_top()
        self.nav_row(x, ay, w, "All Mods", selected is None, bold=True,
                     center=CENTER_SIDEBAR)
        div = ay + M["ROW_H"] + 4
        c.rect(x, div, w, 1, T["DIVIDER"])

        y = div + 9
        limit = self.sy + self.sh - M["PAD"] - self.footer_h() - 13
        for i, (name, _, _, st) in enumerate(MODS):
            if y + M["ROW_H"] > limit:
                break
            self.nav_row(x, y, w, name, selected == i, bold=False,
                         dimmed=(st == "miss"))
            y += M["ROW_H"]

        # Fussblock
        fh = self.footer_h()
        fy = self.sy + self.sh - M["PAD"] - fh
        c.rect(x, fy - 10, w, 1, T["DIVIDER"])
        fmid = fy + fh // 2
        c.sprite("loader", x, fmid - M["HEAD"] // 2, M["HEAD"], M["HEAD"], T["TEXT_MUTED"])
        ttop = fmid - (ADV + SMALL_H + 3) // 2
        c.text("Fabric 1.21.11", x + M["HEAD"] + 8, ttop, 12.5, T["TEXT"], bold=True)
        c.text("LumiLib 1.5.4", x + M["HEAD"] + 8, ttop + ADV, 10, T["TEXT_FAINT"])

    def mod_icon(self, name, x, y, size, missing):
        """Wie LumiHubScreen.drawModIcon: mitgeliefertes Icon mit runden
        Ecken, fehlende Mods leicht durchscheinend, sonst der Wuerfel."""
        tex = IDS.get(name)
        radius = min(6, max(2, size // 4))
        if tex and os.path.exists(
                f"{ASSETS}/textures/mod_catalog/{tex}.png"):
            self.c.sprite(tex, x, y, size, size, radius=radius,
                          opacity=0.85 if missing else 1.0)
        else:
            self.default_icon(x, y, size, missing)

    def default_icon(self, x, y, size, faint):
        """Standard-Icon wie in LumiHubScreen.drawDefaultIcon - Kaestchen
        plus Wuerfel, Groesse 58 % auf gerade Pixel gerundet."""
        c = self.c
        c.rounded(x, y, size, size, min(6, max(2, size // 4)), T["ICON_BG"], "card")
        inner = max(6, int(size * 0.58) & ~1)
        off = (size - inner) // 2
        c.sprite("mod_default", x + off, y + off, inner, inner,
                 T["TEXT_FAINT"] if faint else T["TEXT_MUTED"])

    def nav_row(self, x, y, w, label, selected, bold, dimmed=False,
                center=False):
        c = self.c
        if selected:
            c.rounded(x - 4, y, w + 8, M["ROW_H"], 4, T["ROW_SELECTED"], "card")
        mid = y + M["ROW_H"] // 2
        isz = M["ROW_ICON"]
        ix = x
        if center:
            # Symbol UND Beschriftung als ein Block mittig - nicht nur der
            # Text, sonst sitzt das Symbol daneben und nichts ist zentriert.
            blockW = isz + M["ROW_ICON_GAP"] + c.width(label, 12.5, bold)
            ix = x + (w - blockW) // 2
        # Erst der Text (Versalblock mittig in der Zeile), dann die
        # Kachel auf die OPTISCHE Mitte - wie LumiHubScreen.drawNavRow.
        line_y = (mid if label == "All Mods"
                  else c.line_centre_in(y, M["ROW_H"], BODY_H, BODY_D, 12.5, bold))
        # Mitte des Versalblocks, mit halbem Pixel - wie drawNavRow.
        iy = (mid - isz / 2.0 if label == "All Mods"
              else c.cap_block_centre_of(line_y, BODY_H, BODY_D, 12.5, bold)
                   - isz / 2.0)
        if label == "All Mods":
            c.sprite("loader", ix, iy, isz, isz,
                     T["TEXT"] if selected else T["TEXT_MUTED"])
        else:
            self.mod_icon(label, ix, iy, isz, dimmed)
        col = T["TEXT_FAINT"] if dimmed else (T["TEXT"] if selected else T["TEXT_MUTED"])
        tx = ix + isz + M["ROW_ICON_GAP"]
        if label == "All Mods":
            # Einzelne Zeile zwischen zwei Trennstrichen: nach der
            # sichtbaren Tinte mitten, wie Ui.drawCenteredIn.
            c.text_centered_in(label, tx, y, M["ROW_H"], 12.5, col, BODY_H,
                               bold=bold)
        else:
            # Liste: gemeinsame Schreiblinie, wie Ui.drawCenteredLine.
            c.text(label, tx, line_y - (BODY_H + BODY_D + 1) // 2, 12.5, col,
                   bold=bold)

    # ---------------- Inhalt ----------------
    def overview(self):
        c = self.c
        headY = self.cy + 10
        titleX = self.cx
        if CENTER_TITLE:
            # Nur ueber dem Platz LINKS vom Suchfeld zentrieren - ueber der
            # vollen Breite liefe die Ueberschrift ins Feld.
            room = self.cw - min(M["SEARCH_W"], max(80, self.cw // 3)) - 12
            titleX = self.cx + max(0, (room - c.width("All Mods", 19, True)) // 2)
        c.text("All Mods", titleX, headY, 19, T["TEXT"], bold=True)

        fieldW = min(M["SEARCH_W"], max(80, self.cw // 3))
        fieldX = self.cx + self.cw - fieldW
        fieldY = self.cy + (M["TITLE_H"] - M["SEARCH_H"]) // 2
        c.rounded(fieldX, fieldY, fieldW, M["SEARCH_H"], 6, T["FIELD"], "card")
        c.sprite("search", fieldX + 7,
                 fieldY + M["SEARCH_H"] // 2 - M["SEARCH_ICON"] // 2,
                 M["SEARCH_ICON"], M["SEARCH_ICON"], T["TEXT"])
        c.text("Search", fieldX + 7 + M["SEARCH_ICON"] + 5,
               fieldY + M["SEARCH_H"] // 2 - BODY_H // 2, 12.5, T["TEXT_FAINT"])

        cols = M["CARD_COLS"]
        while cols > 1 and (self.cw - M["CARD_GAP"] * (cols - 1)) // cols < M["CARD_MIN_W"]:
            cols -= 1
        colW = (self.cw - M["CARD_GAP"] * (cols - 1)) // cols
        for i, (name, ver, desc, st) in enumerate(MODS):
            bx = self.cx + (i % cols) * (colW + M["CARD_GAP"])
            by = self.listTop + (i // cols) * (M["CARD_H"] + M["CARD_GAP"])
            self.card(bx, by, colW, name, ver, desc, hover=(i == 1), dimmed=(st=="miss"))

    def card(self, x, y, w, name, ver, desc, hover, dimmed=False):
        c = self.c
        c.rounded(x, y, w, M["CARD_H"], 6,
                  T["CARD_HOVER"] if hover else T["CARD"], "card")
        p, isz = M["CARD_PAD"], M["CARD_ICON"]
        rowH = max(isz, ADV + SMALL_H)
        mid = y + p + rowH // 2
        self.mod_icon(name, x + p, mid - isz // 2, isz, dimmed)
        tx = x + p + isz + 9
        tw = w - (tx - x) - p
        ntop = mid - (ADV + SMALL_H) // 2
        col = T["TEXT_FAINT"] if dimmed else T["TEXT"]
        if ver:
            c.text(self.fit(name, tw, 12.5, True), tx, ntop, 12.5, col, bold=True)
            c.text("v" + ver, tx, ntop + ADV, 10, T["TEXT_FAINT"])
        else:
            # Ohne Version nur eine Zeile, und die mittig zum Symbol.
            c.text_centered_in(self.fit(name, tw, 12.5, True), tx,
                               mid - isz // 2, isz, 12.5, col, BODY_H, bold=True)
        c.text(self.fit(desc, w - p * 2, 12.5, False), x + p,
               y + p + rowH + 6, 12.5, T["TEXT_FAINT"] if dimmed else T["TEXT_MUTED"])

    # ---------------- Einstellungen ----------------
    def settings(self, x, y, w, sections):
        """Spiegelt SettingsList: Abschnitt, Gruppe, Zeilen, Schalter."""
        c = self.c
        first = True
        for title, rows in sections:
            if not first:
                y += M["SET_SECTION_GAP"]
            first = False
            if title:
                c.text(title, x, y, 15, T["TEXT"], bold=True)
                y += HEAD_H + M["SET_HEAD_GAP"]
            gh = sum(self.row_h(r, w) for r in rows)
            c.rounded(x, y, w, gh, 6, T["CARD"], "card")
            dy = y
            for i, r in enumerate(rows[:-1]):
                dy += self.row_h(r, w)
                if r[0] == "note" or rows[i + 1][0] == "note":
                    continue
                c.rect(x, dy, w, 1, T["DIVIDER"])
            ry = y
            for r in rows:
                self.set_row(x, ry, w, self.row_h(r, w), r)
                ry += self.row_h(r, w)
            y += gh
        return y

    # Hinweis: Zeile wie jede andere, nur getoent - siehe SettingsList.
    NOTE_LEAD, NOTE_ICON, NOTE_GAP = 13, 12, 7

    def note_lines(self, label, w):
        return self.c.wrap(label, w - M["SET_INSET"] * 2 - self.NOTE_ICON
                           - self.NOTE_GAP, 12.5) or [""]

    def row_h(self, r, w=None):
        if r[0] == "note":
            return M["SET_ROW_H"] + (len(self.note_lines(r[1], w)) - 1) * self.NOTE_LEAD
        return (M["SET_ROW_H"] if r[2] is None
                else M["SET_PAD"] * 2 + DESC_BLOCK)

    def draw_note(self, x, y, w, h, label, warn):
        c = self.c
        col = T["WARN"] if warn else T["TEXT_MUTED"]
        c.rounded(x, y, w, h, 6, (col & 0xFFFFFF) | (0x24 << 24), "card")
        lines = self.note_lines(label, w)
        block = BODY_H + BODY_D + (len(lines) - 1) * self.NOTE_LEAD
        top = y + (h - block) // 2
        iy = (top + c.ink_top_offset(12.5) + c.cap_height(12.5) / 2.0
              - self.NOTE_ICON / 2.0)
        c.sprite("info", x + M["SET_INSET"], iy, self.NOTE_ICON, self.NOTE_ICON, col)
        tx = x + M["SET_INSET"] + self.NOTE_ICON + self.NOTE_GAP
        for i, ln in enumerate(lines):
            c.text(ln, tx, top + i * self.NOTE_LEAD, 12.5, col)

    def set_row(self, x, y, w, h, r):
        """r = (art, label, desc, an)"""
        kind, label, desc, on = r
        c = self.c
        if kind == "note":
            self.draw_note(x, y, w, h, label, bool(on))
            return
        tx = x + M["SET_INSET"]
        room = w - M["SET_INSET"] * 2 - (M["TOGGLE_W"] + 12 if kind == "toggle" else 0)
        # Hinweiszeile: nicht fett, Warnung rot - wie in SettingsList.
        note = kind == "note"
        col = T["WARN"] if (note and on) else (T["TEXT_MUTED"] if note else T["TEXT"])
        if desc is None:
            c.text_centered_in(self.fit(label, room, 12.5, not note), tx, y, h,
                               12.5, col, BODY_H, bold=not note)
        else:
            top = y + (h - DESC_BLOCK) // 2
            c.text(self.fit(label, room, 12.5, not note), tx, top, 12.5, col, bold=not note)
            c.text(self.fit(desc, room, 10, False), tx, top + M["SET_DESC_GAP"],
                   10, T["TEXT_FAINT"])
        if kind == "toggle":
            self.toggle(x + w - M["SET_INSET"] - M["TOGGLE_W"],
                        y + (h - M["TOGGLE_H"]) // 2, on)
        elif kind == "slider":
            self.slider(x, y, w, h, on)      # on = Anteil 0..1 plus Text

    def slider(self, x, y, w, h, val):
        """Wie SettingsList.drawSlider - Bahn, gefuellt, Knopf, Zahl."""
        frac, text = val
        c = self.c
        r = M["SLIDER_KNOB"]
        tx = x + w - M["SET_INSET"] - M["VALUE_W"] - 6 - M["SLIDER_W"]
        ty = y + (h - M["SLIDER_TRACK"]) // 2
        usable = M["SLIDER_W"] - r * 2
        c.rounded(tx, ty, M["SLIDER_W"], M["SLIDER_TRACK"],
                  M["SLIDER_TRACK"] // 2, T["TOGGLE_OFF"], "card")
        knob = tx + r + round(usable * frac)
        c.rounded(tx, ty, knob - tx, M["SLIDER_TRACK"],
                  M["SLIDER_TRACK"] // 2, T["TOGGLE_ON"], "card")
        c.rounded(knob - r, y + h // 2 - r, r * 2, r * 2, r, T["TOGGLE_KNOB"], "card")
        c.text_centered_in(text, x + w - M["SET_INSET"] - c.width(text, 10, False),
                           y, h, 10, T["TEXT_MUTED"], SMALL_H)

    def toggle(self, x, y, on):
        c = self.c
        h, w = M["TOGGLE_H"], M["TOGGLE_W"]
        c.rounded(x, y, w, h, h // 2, T["TOGGLE_ON"] if on else T["TOGGLE_OFF"], "card")
        r = (h - M["TOGGLE_INSET"] * 2) // 2
        cx = (x + w - M["TOGGLE_INSET"] - r) if on else (x + M["TOGGLE_INSET"] + r)
        c.rounded(cx - r, y + h // 2 - r, r * 2, r * 2, r, T["TOGGLE_KNOB"], "card")

    def mod_page(self, idx):
        c = self.c
        name, ver, desc, st = MODS[idx]
        headY = self.cy + 10
        c.text(name, self.cx, headY, 19, T["TEXT"], bold=True)
        if ver:
            c.text("v" + ver, self.cx + c.width(name, 19, True) + 8,
                   headY + TITLE_H_CAP - SMALL_H, 10, T["TEXT_FAINT"])

        x, w, y = self.cx, self.cw, self.listTop
        isz, pad = 32, M["CARD_PAD"]
        lines = self.wrap(desc, w - pad * 2 - isz - 12)
        boxH = max(isz, len(lines) * ADV3) + pad * 2
        c.rounded(x, y, w, boxH, 6, T["CARD"], "card")
        self.mod_icon(name, x + pad, y + pad, isz, st == "miss")
        ty = y + pad + max(0, (isz - len(lines) * ADV3) // 2)
        for ln in lines:
            c.text(ln, x + pad + isz + 12, ty, 12.5, T["TEXT_MUTED"])
            ty += ADV3
        y += boxH + M["CARD_GAP"]

        label = "Auf Modrinth ansehen" if st == "miss" else "Einstellungen öffnen"
        bw = min(w, int(c.width(label, 12.5, True)) + 28)
        bh = 22
        c.rounded(x, y, bw, bh, 6, T["CARD_HOVER"], "card")
        c.text(label, x + (bw - c.width(label, 12.5, True)) / 2,
               y + bh // 2 - (BODY_H + 4 + 1) // 2, 12.5, T["TEXT"], bold=True)

    # ---------------- Hilfen ----------------
    def fit(self, s, maxw, px, bold):
        if self.c.width(s, px, bold) <= maxw:
            return s
        room = maxw - self.c.width("...", px, bold)
        end = len(s)
        while end > 0 and self.c.width(s[:end], px, bold) > room:
            end -= 1
        return s[:end].rstrip() + "..."

    def wrap(self, s, maxw):
        out, line = [], ""
        for word in s.split(" "):
            probe = word if not line else line + " " + word
            if self.c.width(probe, 12.5, False) <= maxw or not line:
                line = probe
            else:
                out.append(line)
                line = word
        if line:
            out.append(line)
        return out


def settings_scene():
    h = Hub(); h.sidebar(5)          # Index von Shadow Tweaks in MODS
    name = "Shadow Tweaks"
    c = h.c
    headY = h.cy + 10
    c.text(name, h.cx, headY, 19, T["TEXT"], bold=True)
    c.text("v1.2.0", h.cx + c.width(name, 19, True) + 8,
           headY + TITLE_H_CAP - SMALL_H, 10, T["TEXT_FAINT"])

    x, w, y = h.cx, h.cw, h.listTop
    isz, pad = 32, M["CARD_PAD"]
    desc = "Client-side control over entity shadows: size, opacity, render distance."
    lines = h.wrap(desc, w - pad * 2 - isz - 12)
    boxH = max(isz, len(lines) * ADV3) + pad * 2
    c.rounded(x, y, w, boxH, 6, T["CARD"], "card")
    h.mod_icon(name, x + pad, y + pad, isz, False)
    ty = y + pad + max(0, (isz - len(lines) * ADV3) // 2)
    for ln in lines:
        c.text(ln, x + pad + isz + 12, ty, 12.5, T["TEXT_MUTED"])
        ty += ADV3
    y += boxH + M["CARD_GAP"]

    h.settings(x, y, w, [
        ("General", [
            ("note", "Minecrafts own entity shadows are off - this mod does nothing right now.", None, True),
            ("toggle", "Enable Mod", None, True),
        ]),
        ("Appearance", [
            ("slider", "Shadow Size", "Scales every shadow.", (0.33, "1")),
            ("slider", "Opacity", None, (1.0, "1")),
            ("slider", "Render Distance", "Blocks.", (0.2, "16")),
        ]),
        # Nur zwei Zeilen: Die Vorschau schneidet nicht ab und rollt
        # nicht. Im Spiel tut die Liste beides, hier wuerde ein dritter
        # Eintrag einfach ueber den Fensterrand laufen und so aussehen,
        # als waere das ein Fehler.
        ("Categories", [
            ("toggle", "Players", None, True),
            ("toggle", "Hostile Mobs", None, True),
        ]),
    ])
    return h


if __name__ == "__main__":
    h = Hub(); h.sidebar(None); h.overview()
    h.c.save("/home/claude/vorschau_uebersicht.png")

    h = Hub(); h.sidebar(1); h.mod_page(1)
    h.c.save("/home/claude/vorschau_fehlt.png")

    settings_scene().c.save("/home/claude/vorschau_einstellungen.png")
    print("drei Ansichten geschrieben")
