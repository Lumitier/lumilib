"""
Erzeugt den Oeffnungs- und Schliessklang des Hubs.

KEIN GESPIELTES MATERIAL. DAS IST NACH SIEBEN VERSUCHEN DER KERN.
Die Fassungen davor hatten alle eine Akkordfolge - erst zwei Akkorde,
dann vier, dann drei. Die Urteile dazu, der Reihe nach:

  "hoert sich an wie die Melodie von einem Weihnachtslied"
  "wirklich nur eine Note"
  "hoert sich noch so weird nach Klavier an"
  "hoert sich an als wuerde man das mit einem Schlagzeug spielen"

Das letzte trifft den Punkt, den ich siebenmal verfehlt habe: Eine
Folge von Anschlaegen IST gespieltes Material. Egal wie synthetisch
die Klangfarbe ist - wenn mehrere Toene nacheinander angeschlagen
werden, hoert das Ohr jemanden, der etwas spielt. Es gibt keine
Klangfarbe, die das aufhebt.

Deshalb hier: KEINE Anschlaege und KEINE Folge, sondern EIN Vorgang.
Eine Frequenz faehrt hoch, ein Klang oeffnet sich, steht und faellt
wieder zu. So etwas kann niemand spielen; so etwas kann nur ein Geraet
tun, und genau danach soll es klingen.

DIE TECHNIK IST FREQUENZMODULATION.
Ein Traeger wird von einem zweiten Ton in seiner Frequenz moduliert.
Je staerker die Modulation, desto mehr Seitenbaender entstehen - und
wenn das Verhaeltnis der beiden Frequenzen KRUMM ist, liegen diese
Seitenbaender nicht auf der Obertonreihe. Das ergibt einen Klang, den
kein schwingender Koerper erzeugen kann: Jedes Instrument, das aus
Saite, Luftsaeule oder Fell besteht, bringt eine harmonische Reihe
hervor. Ein krummes Verhaeltnis gibt es in der Natur nicht.

Dazu aendert sich die Modulationsstaerke ueber die Zeit. Der Klang
wird dadurch waehrend seines Verlaufs heller und wieder dunkler, ohne
dass jemand etwas anschlaegt. Auch das kennt das Ohr nur von Geraeten.

ZUR TONART:
Was stehenbleibt, ist ein Ton auf C - dem Grundton der Flaeche, die im
Hub laeuft (Aufnahme von VectorSpace). Darueber eine Quinte. Keine
Terz: Mit Terz waere es ein Akkord und damit wieder Musik, ohne Terz
ist es ein offener Klang ohne Tongeschlecht.

Die vorige Fassung mit Akkorden liegt nicht mehr im Projekt. Falls sie
je wieder gebraucht wird, steht ihre Bauart in der Versionsgeschichte.

Ausfuehren aus dem Projektordner:
    python3 tools/sting.py [zielordner]
Braucht: numpy und ffmpeg
"""
import os
import subprocess
import sys
import wave

import numpy as np

RATE = 44100
PEAK = 0.40
SECONDS = 2.0

A4 = 440.0


def note(semi):
    return A4 * (2.0 ** (semi / 12.0))


# Je Fassung:
#   ziel      Halbtoene ueber a, wo der Vorgang stehenbleibt
#   verh      Verhaeltnis Modulator zu Traeger. KRUMM, sonst entsteht
#             eine harmonische Reihe und es klingt wieder nach
#             Instrument. 1,414 ist die Wurzel aus zwei.
#   index     Modulationsstaerke am Anfang und am Ende des Vorgangs
#   anfahrt   Sekunden, die die Frequenz zum Ziel hinauffaehrt
#   oktaven   um wieviele Oktaven sie dabei steigt
#   rausch    Anteil des schmalen Rauschbands
FASSUNGEN = {
    "glatt":  dict(ziel=-9, verh=1.414, index=(7.0, 0.6), anfahrt=0.34,
                   oktaven=1.5, rausch=0.10),
    "scharf": dict(ziel=-9, verh=2.374, index=(11.0, 1.1), anfahrt=0.28,
                   oktaven=2.0, rausch=0.16),
    "weit":   dict(ziel=-21, verh=1.414, index=(6.0, 0.5), anfahrt=0.42,
                   oktaven=2.2, rausch=0.08),
}


def huelle(t, anstieg, abfall):
    e = np.exp(-t / abfall)
    if anstieg > 0:
        e = e * np.sin(np.clip(t / anstieg, 0.0, 1.0) * np.pi / 2.0)
    return np.maximum(e, 0.0)


def fahrt(t, ziel_f, anfahrt, oktaven):
    """Frequenzverlauf: faehrt von unten auf die Zielfrequenz und bleibt.

    Der Anstieg laeuft ueber eine Kurve, die am Ende flacher wird - so
    kommt die Bewegung zur Ruhe, statt in die Zielfrequenz
    hineinzuknallen.
    """
    w = np.clip(t / anfahrt, 0.0, 1.0)
    w = 1.0 - (1.0 - w) ** 2.4
    return ziel_f * 2.0 ** (oktaven * (w - 1.0))


def fm(t, freq, verh, index):
    """Frequenzmodulation. freq und index sind Verlaeufe, keine Zahlen.

    Der Modulator laeuft mit der Traegerfrequenz mit, damit das
    Verhaeltnis waehrend der Anfahrt gleich bleibt - sonst wandert die
    Klangfarbe unkontrolliert.

    DIE PHASE ENTSTEHT UEBER DIE SUMME DER FREQUENZEN, nicht ueber
    freq*t. Bei einer Frequenz, die sich aendert, waere freq*t falsch:
    Es ergaebe Spruenge in der Phase, und die hoert man als Knacken.
    """
    mod_ph = 2.0 * np.pi * np.cumsum(freq * verh) / RATE
    mod = index * np.sin(mod_ph)
    tr_ph = 2.0 * np.pi * np.cumsum(freq) / RATE
    return np.sin(tr_ph + mod)


def luft(t, freq, menge):
    """Schmales Rauschband um die Zielfrequenz herum.

    Gibt dem Vorgang etwas Koerper, ohne einen Ton hinzuzufuegen. Zu
    viel davon klingt nach Wind, zu wenig nach reiner Rechnung.
    """
    n = len(t)
    rng = np.random.default_rng(11)
    r = rng.standard_normal(n)
    sp = np.fft.rfft(r)
    f = np.fft.rfftfreq(n, 1.0 / RATE)
    mitte = float(np.median(freq)) * 4.0
    sp[(f < mitte * 0.6) | (f > mitte * 2.5)] = 0.0
    r = np.fft.irfft(sp, n)
    r /= np.max(np.abs(r)) or 1.0
    return menge * r


def nachhall(x, sekunden, seed):
    n = len(x)
    rng = np.random.default_rng(seed)
    m = int(sekunden * RATE)
    tt = np.arange(m) / RATE
    env = np.exp(-6.9 * tt / sekunden)
    env[: int(RATE * 0.012)] = 0.0
    ir = rng.standard_normal(m) * env
    ir /= np.sqrt(np.sum(ir ** 2)) or 1.0
    g = n + m
    return np.fft.irfft(np.fft.rfft(x, g) * np.fft.rfft(ir, g), g)[:n]


def vorgang(cfg, rueckwaerts, chan):
    n = int(SECONDS * RATE)
    t = np.arange(n) / RATE
    ziel_f = note(cfg["ziel"])

    freq = fahrt(t, ziel_f, cfg["anfahrt"], cfg["oktaven"])

    # Die Modulationsstaerke faellt vom Anfangswert auf den Endwert.
    # Das ist, was den Klang waehrend des Vorgangs heller und dann
    # wieder dunkler macht, ohne dass etwas angeschlagen wird.
    i0, i1 = cfg["index"]
    w = np.clip(t / (cfg["anfahrt"] * 1.6), 0.0, 1.0)
    index = i0 + (i1 - i0) * (1.0 - (1.0 - w) ** 2.0)

    # Lautstaerke: weicher Einsatz ueber die ganze Anfahrt, dann stehen
    # und langsam fallen. Kein Anschlag an keiner Stelle.
    amp = huelle(t, cfg["anfahrt"] * 0.9, 0.85)

    kern = fm(t, freq, cfg["verh"], index)
    # Die Quinte darueber, leiser und schwaecher moduliert. Keine
    # Terz - mit Terz waere es ein Akkord und damit Musik.
    quinte = fm(t, freq * 1.4983, cfg["verh"], index * 0.55)

    sig = amp * (0.72 * kern + 0.30 * quinte)
    sig += amp * luft(t, freq, cfg["rausch"])

    # Ein tiefer Sinus unter dem Ziel, der mit anfaehrt. Gibt Gewicht.
    sub_ph = 2.0 * np.pi * np.cumsum(freq * 0.5) / RATE
    sig += 0.30 * huelle(t, cfg["anfahrt"], 0.55) * np.sin(sub_ph)

    if rueckwaerts:
        # Schliessen ist derselbe Vorgang umgekehrt: Die Frequenz
        # faehrt herunter statt herauf. Dafuer wird das fertige Signal
        # gespiegelt und dann eine neue Huellkurve darueber gelegt -
        # ohne die laege der weiche Einsatz am Ende und der Abfall am
        # Anfang, und das klingt rueckwaerts abgespielt.
        sig = sig[::-1].copy()
        sig *= huelle(t, 0.05, 0.60)

    if chan:
        v = int(0.005 * RATE)
        sig = np.concatenate([np.zeros(v), sig])[:n]
    sig /= np.max(np.abs(sig)) or 1.0
    wet = nachhall(sig, 1.4, 41 + chan)
    wet /= np.max(np.abs(wet)) or 1.0
    return 0.80 * sig + 0.20 * wet


def bauen(cfg, rueckwaerts):
    st = np.stack([vorgang(cfg, rueckwaerts, 0),
                   vorgang(cfg, rueckwaerts, 1)], axis=1)
    st /= np.max(np.abs(st)) or 1.0
    return st * PEAK


def schreiben(st, path):
    pcm = (np.clip(st, -1.0, 1.0) * 32767.0).astype("<i2")
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    tmp = path.replace(".ogg", ".wav")
    with wave.open(tmp, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp,
                    "-c:a", "libvorbis", "-q:a", "5", path], check=True)
    os.remove(tmp)

    m = st.mean(axis=1)
    sp = np.abs(np.fft.rfft(m * np.hanning(len(m)))) ** 2
    f = np.fft.rfftfreq(len(m), 1.0 / RATE)
    tot = sp.sum()

    def band(a, b):
        return sp[(f >= a) & (f < b)].sum() / tot * 100.0

    env = np.convolve(np.abs(m), np.ones(441) / 441.0, mode="same")
    print(f"{os.path.basename(path):18s} {len(m)/RATE:4.1f} s  "
          f"Spitze {np.max(np.abs(m)):.3f}  "
          f"Gipfel bei {env.argmax()/RATE*1000:4.0f} ms  "
          f"80-300 {band(80,300):5.1f} | 300-1k {band(300,1000):5.1f} | "
          f"1-4k {band(1000,4000):5.1f} | >4k {band(4000,22050):5.1f} %")


def main():
    ziel = sys.argv[1] if len(sys.argv) > 1 else "/home/claude"
    for name, cfg in FASSUNGEN.items():
        schreiben(bauen(cfg, False), os.path.join(ziel, f"open_{name}.ogg"))
        schreiben(bauen(cfg, True), os.path.join(ziel, f"close_{name}.ogg"))


if __name__ == "__main__":
    main()
