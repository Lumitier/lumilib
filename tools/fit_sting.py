"""
Macht aus einer fremden Aufnahme den Oeffnungs- und Schliessklang.

WOFUER:
Acht gerechnete Versuche sind daran gescheitert, dass gerechnete
Klaenge nach Rechnung klingen. Die Aufnahme von TommasoMotteran ist
der Ausgangspunkt; hier wird sie nur angepasst.

WAS GEMACHT WIRD:

1. PEGEL. Die Rohdatei liegt bei Spitze 0,291 und RMS -30,7 dBFS.
   Ein Einzelklang darf lauter sein als die Flaeche (0,17), weil er
   nur kurz auftritt - hier 0,40.

2. KEIN UMSTIMMEN BEIM OEFFNEN. Nachgemessen steht die Aufnahme in C:
   staerkster Ton 262,4 Hz (C4), darueber C5 bei 531 Hz, dazu F3, F4,
   D3, D4, E4. Die Flaeche im Hub enthaelt C2, C3, F3, C4 und A#4.
   Die beiden passen also von sich aus zusammen - Glueck, aber
   nachgeprueft.

3. DER SCHLIESSKLANG ENTSTEHT AUS DEMSELBEN MATERIAL, tiefer
   gestimmt. Polyfrost macht es genauso: deren open liegt auf G#4,
   deren close auf G#3, also dieselbe Note eine Oktave tiefer. Das
   garantiert, dass die beiden zusammengehoeren, und spart eine
   zweite Lizenz.

   Zwei Wege dafuer, beide hier drin:

     ABSPIELRATE AENDERN (wie ein Tonband langsamer laufen lassen).
     Einfach und klingt natuerlich, aber die Datei wird dabei
     laenger: eine Oktave tiefer heisst doppelt so lang. Wird danach
     gekuerzt.

     PHASENVOKODER. Rechnet ueber die Kurzzeit-Fourier-Transformation
     und haelt die Laenge. Klingt bei starken Verschiebungen etwas
     glasig, aber hier ist das kein Nachteil.

Die Lizenz erlaubt das Bearbeiten ausdruecklich: CC BY 4.0, "to remix
(to adapt and modify) as long as you credit the author". Die
Namensnennung steht in LICENSE-sounds.txt.

Ausfuehren aus dem Projektordner:
    python3 tools/fit_sting.py <stinger.wav> [zielordner]
Braucht: numpy und ffmpeg
"""
import os
import subprocess
import sys
import wave

import numpy as np

RATE = 44100
PEAK = 0.40

# Halbtoene, um die der Schliessklang tiefer liegt. 12 ist eine ganze
# Oktave, wie bei der Vorlage; 7 eine Quinte, also weniger drastisch.
TIEFER = 12

# Der Schliessklang wird auf diese Laenge gekuerzt, falls er durch das
# Umstimmen laenger geworden ist.
MAX_CLOSE = 2.2


def load(path):
    raw = subprocess.run(
        ["ffmpeg", "-v", "quiet", "-i", path, "-f", "f32le",
         "-ac", "2", "-ar", str(RATE), "-"],
        capture_output=True).stdout
    return np.frombuffer(raw, dtype="<f4").astype(np.float64).reshape(-1, 2)


def rate_aendern(x, halbtoene):
    """Tiefer stimmen, indem langsamer abgespielt wird.

    Die Datei wird dabei laenger - um denselben Faktor, um den die
    Frequenz sinkt. Eine Oktave tiefer heisst doppelt so lang.
    """
    faktor = 2.0 ** (-halbtoene / 12.0)
    n_neu = int(len(x) / faktor)
    alt = np.arange(len(x))
    neu = np.linspace(0.0, len(x) - 1.0, n_neu)
    return np.stack([np.interp(neu, alt, x[:, c]) for c in range(2)], axis=1)


def vokoder(x, halbtoene):
    """Tiefer stimmen bei gleicher Laenge, ueber einen Phasenvokoder.

    Arbeitsweise: Das Signal wird in ueberlappende Fenster zerlegt und
    je Fenster in Frequenzen zerlegt. Beim Zusammensetzen werden die
    Fenster enger gesetzt als beim Zerlegen - dadurch wird das Signal
    kuerzer, ohne dass sich die Tonhoehe aendert. Anschliessend wird
    es per Abspielrate wieder auf die alte Laenge gebracht, und DABEI
    sinkt die Tonhoehe.

    Entscheidend ist, dass die Phase beim Zusammensetzen fortgefuehrt
    und nicht einfach uebernommen wird: Sonst springt sie an jeder
    Fenstergrenze, und das hoert man als Rauschen und Flattern.
    """
    n_fft, hop = 2048, 512
    faktor = 2.0 ** (halbtoene / 12.0)      # > 1: erst kuerzer machen
    fenster = np.hanning(n_fft)
    out = np.zeros_like(x)

    for c in range(2):
        s = x[:, c]
        rahmen = 1 + max(0, (len(s) - n_fft) // hop)
        spek = np.empty((rahmen, n_fft // 2 + 1), complex)
        for i in range(rahmen):
            spek[i] = np.fft.rfft(s[i * hop:i * hop + n_fft] * fenster)

        # Momentane Frequenz je Fenster aus der Phasendifferenz
        erwartet = 2.0 * np.pi * hop * np.arange(n_fft // 2 + 1) / n_fft
        phase = np.angle(spek)
        delta = np.diff(phase, axis=0, prepend=phase[:1]) - erwartet
        delta = np.mod(delta + np.pi, 2.0 * np.pi) - np.pi
        echte = erwartet + delta

        hop_neu = int(hop / faktor)
        laenge = (rahmen - 1) * hop_neu + n_fft
        summe = np.zeros(laenge)
        gewicht = np.zeros(laenge)
        lauf = phase[0].copy()
        for i in range(rahmen):
            rek = np.abs(spek[i]) * np.exp(1j * lauf)
            stueck = np.fft.irfft(rek, n_fft) * fenster
            a = i * hop_neu
            summe[a:a + n_fft] += stueck
            gewicht[a:a + n_fft] += fenster ** 2
            lauf = lauf + echte[i] / faktor
        summe /= np.maximum(gewicht, 1e-8)

        # Jetzt per Abspielrate zurueck auf die alte Laenge - dabei
        # sinkt die Tonhoehe um genau die gewuenschten Halbtoene.
        ziel = np.linspace(0.0, len(summe) - 1.0, len(s))
        out[:, c] = np.interp(ziel, np.arange(len(summe)), summe)
    return out


def kuerzen(x, sekunden, aus=0.25):
    """Auf Laenge bringen, mit Ausblendung statt hartem Schnitt."""
    n = int(sekunden * RATE)
    if len(x) <= n:
        return x
    y = x[:n].copy()
    m = int(aus * RATE)
    w = np.cos(np.linspace(0.0, np.pi / 2.0, m)) ** 2
    y[-m:] *= w[:, None]
    return y


def pegeln(x):
    return x / (np.max(np.abs(x.mean(axis=1))) or 1.0) * PEAK


def schreiben(x, path):
    pcm = (np.clip(x, -1.0, 1.0) * 32767.0).astype("<i2")
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

    m = x.mean(axis=1)
    n = len(m)
    sp = np.abs(np.fft.rfft(m * np.hanning(n))) ** 2
    f = np.fft.rfftfreq(n, 1.0 / RATE)
    tot = sp.sum()

    def band(a, b):
        return sp[(f >= a) & (f < b)].sum() / tot * 100.0

    amp = np.abs(np.fft.rfft(m * np.hanning(n)))
    msk = (f > 50) & (f < 4000)
    grund = f[msk][amp[msk].argmax()]
    print(f"{os.path.basename(path):20s} {n/RATE:4.2f} s  "
          f"Spitze {np.max(np.abs(m)):.3f}  "
          f"Grundton {grund:6.1f} Hz  "
          f"80-300 {band(80,300):5.1f} | 300-1k {band(300,1000):5.1f} | "
          f"1-4k {band(1000,4000):5.1f} | >4k {band(4000,22050):5.1f} %  "
          f"{os.path.getsize(path)/1024.0:4.0f} KB")


def main():
    quelle = sys.argv[1]
    ziel = sys.argv[2] if len(sys.argv) > 2 else "/home/claude"
    roh = load(quelle)

    # Oeffnen: unveraendert bis auf den Pegel. Steht schon in C.
    schreiben(pegeln(roh), os.path.join(ziel, "open_fit.ogg"))

    # Schliessen, Weg 1: Abspielrate, danach gekuerzt.
    schreiben(pegeln(kuerzen(rate_aendern(roh, TIEFER), MAX_CLOSE)),
              os.path.join(ziel, "close_rate.ogg"))

    # Schliessen, Weg 2: Phasenvokoder, Laenge bleibt.
    schreiben(pegeln(vokoder(roh, TIEFER)),
              os.path.join(ziel, "close_fest.ogg"))

    # Schliessen, Weg 3: nur eine Quinte tiefer, per Abspielrate.
    schreiben(pegeln(kuerzen(rate_aendern(roh, 7), MAX_CLOSE)),
              os.path.join(ziel, "close_quinte.ogg"))


if __name__ == "__main__":
    main()
