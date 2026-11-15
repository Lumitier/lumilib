"""
Probiert Einstellungen von pad_loop.py durch und bewertet sie gegen die
Messwerte von Polyfrosts Flaeche.

WARUM DIESE DATEI EXISTIERT:
Vier Werte haengen zusammen - Lautstaerke der obersten Stimme, Hoehe der
Tonhoehenbewegung, Tiefe der Lautstaerkeschwingung und die Grenze, ab
der das Stereobild gespreizt wird. An einem zu drehen verschiebt die
anderen Messwerte mit, weil am Ende normiert wird und weil der Hall
alles verkoppelt. Ich habe vier Durchgaenge lang an einzelnen Zahlen
gedreht und mir dabei jedes Mal einen anderen Wert kaputtgemacht.
Besser: alle Kombinationen rechnen und die beste nehmen.

DIE ZIELWERTE stammen aus der Messung ihrer Datei (nur die Zahlen, nie
die Datei selbst). Die Gewichte sagen, was wie stark zaehlt: Die
Bandverteilung und die Tonhoehenbewegung entscheiden ueber den
Charakter, die Schwankung bestimmt, ob es pumpt, das Stereobild ist
Beiwerk.

Ausfuehren aus dem Projektordner:
    python3 tools/pad_sweep.py            grobe Suche, rund 2 Minuten
    python3 tools/pad_sweep.py --fein     um das bisherige Beste herum
"""
import itertools
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pad_loop as P  # noqa: E402

RATE = P.RATE

# IMMER DIE ECHTE LAENGE.
# Der erste Durchgang lief mit 34 s, um Zeit zu sparen - und das war
# ein Fehler. snap() rastet jede Frequenz auf ein Vielfaches von
# 1/Laenge; bei halber Laenge ist dieses Raster doppelt so grob, also
# landen Tonhoehenbewegung und Lautstaerkeschwingungen auf anderen
# Werten. Die gefundene Einstellung ergab bei 68 s dann 4,38 Cent
# statt der gemessenen 2,57 und eine ganz andere Bandverteilung.
LENGTH = P.SECONDS

ZIEL = {
    "b_tief": (75.1, 1.00),   # Prozent der Leistung 80-300 Hz
    "b_mitte": (24.0, 0.60),  # Prozent 300-1000 Hz
    "schwank": (7.0, 0.60),   # dB ueber die Schleife
    "stereo": (0.21, 0.10),   # Aehnlichkeit der Kanaele
    "cents": (2.75, 1.00),    # Streuung der Tonhoehe in Cent
    "teil2": (-23.7, 0.70),   # zweiter Teilton in dB
    # Die Schwankung JE BAND. Das ist, was man als Leben hoert, und
    # nicht der Gesamtwert: Bei der Vorlage bewegt sich der Bass um
    # 19 dB und die Hoehe um 8,5, also mit starkem Gefaelle. Deshalb
    # zaehlt das hier am schwersten.
    "sw1": (18.9, 1.20),      # 80-180 Hz
    "sw2": (18.2, 1.00),      # 180-300 Hz
    "sw3": (12.5, 0.90),      # 300-500 Hz
    "sw4": (8.5, 0.70),       # 500-1000 Hz
}

# Das Stereobild bekommt absichtlich wenig Gewicht. Ihre 0,21 sind mit
# addierten Sinuskurven nicht erreichbar: 85 Prozent der Energie liegen
# unter 300 Hz, und genau die muss in der Mitte bleiben, sonst loescht
# sich der Bass beim Summieren aus. Ein Versuch mit 90 Grad auf allen
# Teiltoenen ergab -0,37 und eine Bandverteilung von 48 zu 52 - der
# Bass war weg. Bei ihnen kommt der Wert daher, dass eine Aufnahme in
# jedem Kanal eigenen Raum und eigenes Rauschen hat.


def inst_cents(x, f0):
    """Streuung der Tonhoehe in Cent, ueber die Momentanfrequenz.

    Nicht ueber ein Spektrogramm: Dessen Bins waeren hier 5 Hz breit,
    und der Maximumsuchende springt dann zwischen Nachbarbins. Das
    sieht nach Bewegung aus, wo keine ist - siehe pad_loop.py.
    """
    n = len(x)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1.0 / RATE)
    keep = (f > f0 - 12.0) & (f < f0 + 12.0)
    Y = np.zeros(n, complex)
    Y[:len(X)][keep] = X[keep] * 2.0
    z = np.fft.ifft(Y)
    ph = np.unwrap(np.angle(z))
    w = int(RATE * 0.25)
    phs = np.convolve(ph, np.ones(w) / w, mode="valid")
    fi = np.diff(phs) * RATE / (2.0 * np.pi)
    cut = int(RATE * 2)
    fi = fi[cut:-cut]
    return float((1200.0 * np.log2(fi / np.median(fi))).std())


def messen(stereo, base):
    mono = stereo.mean(axis=1)
    n = len(mono)
    sp = np.abs(np.fft.rfft(mono * np.hanning(n))) ** 2
    f = np.fft.rfftfreq(n, 1.0 / RATE)
    tot = sp.sum()

    def band(a, b):
        return sp[(f >= a) & (f < b)].sum() / tot * 100.0

    amp = np.abs(np.fft.rfft(mono * np.hanning(n)))

    def peak(fr):
        m = (f > fr - 2.0) & (f < fr + 2.0)
        return amp[m].max() if m.any() else 1e-12

    sec = int(n / RATE)
    per = np.array([np.sqrt((mono[i * RATE:(i + 1) * RATE] ** 2).mean())
                    for i in range(sec)])

    # Schwankung je Band, ueber ein Spektrogramm. Hier ist ein
    # Spektrogramm richtig - es geht um Pegel ueber die Zeit, nicht um
    # Frequenzen auf den Bruchteil eines Hertz.
    hop, win = 2048, 8192
    cnt = (n - win) // hop
    hw = np.hanning(win)
    S = np.empty((cnt, win // 2 + 1))
    for i in range(cnt):
        S[i] = np.abs(np.fft.rfft(mono[i * hop:i * hop + win] * hw))
    sf = np.fft.rfftfreq(win, 1.0 / RATE)

    def bandschwank(a, b):
        e = S[:, (sf >= a) & (sf < b)].sum(axis=1)
        return 20.0 * np.log10(e.max() / max(e.min(), 1e-12))

    return {
        "sw1": bandschwank(80, 180),
        "sw2": bandschwank(180, 300),
        "sw3": bandschwank(300, 500),
        "sw4": bandschwank(500, 1000),
        "b_tief": band(80, 300),
        "b_mitte": band(300, 1000),
        "schwank": 20.0 * np.log10(per.max() / max(per.min(), 1e-9)),
        "stereo": float(np.corrcoef(stereo[:, 0], stereo[:, 1])[0, 1]),
        "cents": inst_cents(mono, base),
        "teil2": 20.0 * np.log10(peak(base * 2) / peak(base)),
    }


def fehler(got):
    """Summe der gewichteten relativen Abweichungen."""
    s = 0.0
    for key, (soll, w) in ZIEL.items():
        skala = max(abs(soll), 1.0)
        s += w * abs(got[key] - soll) / skala
    return s


def lauf(top, drift, dlow, dhigh):
    P.VOICE_GAIN = [1.0, 0.78, top]
    P.DRIFT_CENTS = drift
    P.DEPTH_LOW = dlow
    P.DEPTH_HIGH = dhigh
    base = P.note(P.SETS["dur"][0])
    return messen(P.pad("dur", LENGTH, RATE), base)


def main():
    if "--fein" in sys.argv:
        gitter = dict(top=[0.41, 0.45, 0.49], drift=[1.8, 2.0, 2.2],
                      dlow=[0.40, 0.44, 0.48], dhigh=[0.08, 0.12, 0.16])
    else:
        gitter = dict(top=[0.38, 0.45, 0.52], drift=[1.6, 2.0, 2.4],
                      dlow=[0.34, 0.44, 0.54], dhigh=[0.06, 0.12, 0.20])

    keys = list(gitter)
    treffer = []
    gesamt = int(np.prod([len(gitter[k]) for k in keys]))
    for i, combo in enumerate(itertools.product(*(gitter[k] for k in keys)), 1):
        cfg = dict(zip(keys, combo))
        got = lauf(**cfg)
        treffer.append((fehler(got), cfg, got))
        print(f"\r  {i}/{gesamt}", end="", flush=True)
    print()

    treffer.sort(key=lambda r: r[0])
    print(f"\n{'Fehler':>7}  {'oben':>5} {'Cent':>5} {'tief':>5} {'hoch':>5}"
          f"   {'t%':>5} {'m%':>5} {'Schw':>5} {'Cent':>5} {'T2':>6}"
          f"   {'sw80':>5} {'sw180':>5} {'sw300':>5} {'sw500':>5}")
    print(f"{'Ziel':>7}  {'':>5} {'':>5} {'':>5} {'':>5}"
          f"   {75.1:5.1f} {24.0:5.1f} {7.0:5.1f} {2.75:5.2f} {-23.7:6.1f}"
          f"   {18.9:5.1f} {18.2:5.1f} {12.5:5.1f} {8.5:5.1f}")
    for err, cfg, got in treffer[:8]:
        print(f"{err:7.3f}  {cfg['top']:5.2f} {cfg['drift']:5.2f} "
              f"{cfg['dlow']:5.2f} {cfg['dhigh']:5.2f}   "
              f"{got['b_tief']:5.1f} {got['b_mitte']:5.1f} "
              f"{got['schwank']:5.1f} {got['cents']:5.2f} {got['teil2']:6.1f}"
              f"   {got['sw1']:5.1f} {got['sw2']:5.1f} {got['sw3']:5.1f} "
              f"{got['sw4']:5.1f}")


if __name__ == "__main__":
    main()
