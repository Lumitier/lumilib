"""
Erzeugt den Hintergrundton des Hubs: eine ruhige Flaeche, nahtlos im Kreis.

WARUM SELBST ERZEUGT:
OneConfig hat so etwas, aber das ist Auftragsarbeit ihres Sounddesigners
und gehoert ihnen. Hier entsteht eine eigene Flaeche, gerechnet.

DIE KENNWERTE STAMMEN AUS EINER MESSUNG IHRER DATEI.
Nicht die Datei, nur die Zahlen dahinter - und die haben zwei Annahmen
widerlegt, die mich vorher in die Irre gefuehrt haben:

1. "Ein stehender Akkord klingt wie ein Motor."
   Falsch. Ihre Flaeche steht 68 Sekunden lang auf EINEM Akkord
   (C#3 + G#3 + F4, also C#-Dur weit gestellt) und klingt trotzdem
   ruhig. Akkordwechsel braucht es nicht.

2. "Die Flaeche braucht Obertoene, damit sie nicht im Brummband endet."
   Genau verkehrt herum. Gemessen faellt ihr zweiter Teilton um
   23,7 dB ab, der dritte um 28,4, der vierte um 36,1 - das sind
   nahezu reine Sinustoene mit einem Hauch Farbe. Meine Fassungen
   hatten 1/n hoch 1,25, also nur 7,5 dB beim zweiten. Diese Obertoene
   WAREN das Kratzen.

DER DRITTE FEHLER STAND IN MEINEN AKKORDEN:
a-Moll in enger Lage heisst 220 und 262 Hz nebeneinander - eine kleine
Terz. In dieser Tonlage liegen zwei solche Toene innerhalb einer
Frequenzgruppe des Gehoers, und das hoert man als Rauigkeit, nicht als
Zusammenklang. Ihre Lage besteht nur aus Quinte und grosser Sexte, also
weiten Abstaenden. Hier jetzt auch: kein Intervall unter einer Quinte
unterhalb von 400 Hz.

GEMESSENE ZIELWERTE:
  Spitze        0,17          (meine Fassungen lagen bei 0,30)
  RMS           -27,7 dBFS
  Baender       80-300 Hz 75 % | 300 Hz-1 kHz 24 % | darueber 0,5 %
  Bewegung      alle 2 bis 5,7 Sekunden
  Schwankung    7 dB ueber die Schleife

WARUM ES TROTZDEM NAHTLOS BLEIBT:
Jede Frequenz - auch die der langsamen Schwingungen - ist ein
ganzzahliges Vielfaches von 1/Laenge. Und der Hall wird ueber die FFT
gefaltet, also ZYKLISCH: Der Nachhall des Endes landet wieder am
Anfang.

Ausfuehren aus dem Projektordner:
    python3 tools/pad_loop.py            erzeugt den eingestellten Satz
    python3 tools/pad_loop.py --alle     schreibt alle drei zum Vergleich
Braucht: numpy und ffmpeg
"""
import os
import subprocess
import sys
import wave

import numpy as np

OUT_DIR = "src/main/resources/assets/lumilib/sounds/ui"
RATE = 44100

# Gemessen an der Vorlage. Deutlich leiser als meine ersten Fassungen -
# eine Flaeche hinter einem Menue darf nicht praesent sein.
PEAK = 0.17

# Laenge wie bei der Vorlage. Bei einem stehenden Akkord faellt die
# Wiederholung ohnehin kaum auf; die Laenge sorgt nur dafuer, dass sich
# die langsamen Schwingungen nicht zu schnell wiederholen.
SECONDS = 68.0

# Abfall der Teiltoene: 1/n hoch FALLOFF. Gemessen ergibt die Vorlage
# rund 3,0 - also fast Sinus. Darunter wird es blechern.
FALLOFF = 3.0

CHOICE = "dur"

# Lautstaerke der drei Stimmen, von unten nach oben. Siehe pad().
VOICE_GAIN = [1.0, 0.80, 0.38]

A4 = 440.0


def note(semitones):
    """Frequenz, Halbtoene ueber dem Kammerton a."""
    return A4 * (2.0 ** (semitones / 12.0))


# Die Lagen. Angegeben in Halbtoenen ueber a, von unten nach oben.
# Kein Intervall unter sieben Halbtoenen im unteren Bereich - siehe oben.
#
# Die Vorlage faehrt C#-Dur als -20, -13, -4. Hier steht D-Dur, einen
# Halbton darueber: dieselbe Bauart, anderer Grundton.
SETS = {
    # D3, A3, F#4 - Grundton, Quinte, grosse Terz eine Oktave hoeher.
    "dur": [-19, -12, -3],
    # D3, A3, E4 - statt der Terz die None. Schwebender, weil ohne
    # Terz kein Dur und kein Moll festgelegt ist.
    "offen": [-19, -12, -5],
    # D3, A3, F4 - kleine Terz, aber eine Oktave hoeher gelegt, also
    # weit genug auseinander, um nicht rau zu werden.
    "moll": [-19, -12, -4],
}


def snap(freq, length):
    """Naechste Frequenz, die in length Sekunden ganz aufgeht."""
    return max(1.0, round(freq * length)) / length


def voice(t, length, base, seed):
    """Ein Ton: Grundton plus sehr schwache Teiltoene, langsam atmend.

    Die Schwingung der Lautstaerke liegt zwischen 0,18 und 0,5 Hz - so
    gemessen an der Vorlage. Das ist schnell genug, dass sich etwas
    bewegt, und langsam genug, dass man es nicht als Zittern hoert.
    """
    rng = np.random.default_rng(seed)
    out = np.zeros(len(t))
    for k in range(1, 9):
        f = snap(base * k, length)
        if f > 6000.0:
            break
        gain = 1.0 / (k ** FALLOFF)
        # Zwei Stimmen je Teilton, ein halbes Promille auseinander.
        # Das ist Chor, keine Schwebung: Bei 147 Hz sind das 0,07 Hz
        # Unterschied, also ein Durchgang in vierzehn Sekunden - zu
        # langsam, um als Brummen wahrgenommen zu werden.
        #
        # ABER DIE ZWEITE STIMME BLEIBT LEISE, und das ist der Punkt,
        # der mich zwei Durchgaenge gekostet hat: Zwei gleich laute
        # Toene loeschen sich im Tal der Schwebung fast vollstaendig
        # aus. Bei 1 zu 0,7 sind das 15 dB je Teilton, und ueber alle
        # Teiltoene summiert kam ich auf 10 dB Schwankung statt der
        # gemessenen 7. Bei 1 zu 0,4 bleiben 5 dB - hoerbar als Breite,
        # nicht als Pumpen.
        #
        # Die Richtung der Verstimmung wechselt je Teilton, damit die
        # Schwebungen nicht alle gleichzeitig ins Tal laufen.
        drift = 1.0005 if k % 2 else 1.0 / 1.0005
        det = snap(base * k * drift, length)
        ph = rng.uniform(0.0, 2.0 * np.pi)
        lfo = snap(rng.uniform(0.18, 0.50), length)
        # Tiefe je Teilton klein halten. Die Schwingungen laufen
        # unabhaengig; treffen sich mehrere im Tal, addiert sich das.
        # Bei 0,25 bis 0,40 ergab das 12 dB Schwankung statt der
        # gemessenen 7 - hoerbar als Pumpen.
        depth = rng.uniform(0.10, 0.18)
        breathe = (1.0 - depth) + depth * np.sin(2.0 * np.pi * lfo * t + ph)
        out += gain * breathe * np.sin(2.0 * np.pi * f * t + ph)
        out += gain * 0.4 * breathe * np.sin(2.0 * np.pi * det * t + ph + 1.3)
    return out


def reverb(x, rate, seconds):
    """Hall durch ZYKLISCHE Faltung mit abklingendem Rauschen."""
    n = len(x)
    rng = np.random.default_rng(23)
    t = np.arange(n) / rate
    env = np.exp(-6.9 * t / seconds)
    env[: int(rate * 0.02)] = 0.0
    ir = rng.standard_normal(n) * env
    ir /= np.sqrt(np.sum(ir ** 2)) or 1.0
    wet = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(ir), n)
    return wet / (np.max(np.abs(wet)) or 1.0)


def pad(name, length, rate):
    n = int(length * rate)
    t = np.arange(n) / rate

    dry = np.zeros(n)
    for i, semi in enumerate(SETS[name]):
        # Obere Toene deutlich leiser. Zwei Gruende: Sonst zieht der
        # oberste Ton die Aufmerksamkeit auf sich und es klingt nach
        # Melodie - und die Messung der Vorlage verlangt es. Dort
        # liegen 75 Prozent der Leistung zwischen 80 und 300 Hz und
        # nur 24 Prozent darueber; mit gleich laut gesetzten Stimmen
        # kam ich auf 55 zu 45.
        dry += VOICE_GAIN[i] * voice(t, length, note(semi), 100 + i)

    dry /= np.max(np.abs(dry)) or 1.0
    wet = reverb(dry, rate, 2.8)
    mix = 0.55 * dry + 0.45 * wet
    mix /= np.max(np.abs(mix)) or 1.0
    return mix * PEAK


def write(name, mono, rate, path):
    delay = int(rate * 0.013)
    stereo = np.stack([mono, np.roll(mono, delay)], axis=1)
    pcm = (np.clip(stereo, -1.0, 1.0) * 32767.0).astype("<i2")

    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path.replace(".ogg", ".wav")
    with wave.open(tmp, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp,
                    "-c:a", "libvorbis", "-q:a", "4", path], check=True)
    os.remove(tmp)

    spec = np.abs(np.fft.rfft(mono * np.hanning(len(mono)))) ** 2
    f = np.fft.rfftfreq(len(mono), 1.0 / rate)
    tot = spec.sum()

    def band(a, b):
        return spec[(f >= a) & (f < b)].sum() / tot * 100.0

    rms = float(np.sqrt((mono ** 2).mean()))
    sec = int(len(mono) / rate)
    per = np.array([np.sqrt((mono[i * rate:(i + 1) * rate] ** 2).mean())
                    for i in range(sec)])
    swing = 20.0 * np.log10(per.max() / max(per.min(), 1e-9))
    print(f"{name:6s} {os.path.getsize(path) / 1024.0:5.0f} KB  "
          f"Spitze {np.max(np.abs(mono)):.3f}  RMS {20 * np.log10(rms):5.1f} dBFS  "
          f"Schwankung {swing:4.1f} dB  "
          f"80-300 {band(80, 300):5.1f}%  300-1k {band(300, 1000):5.1f}%  "
          f">1k {band(1000, 22050):4.1f}%  "
          f"Naht {abs(mono[0] - mono[-1]):.5f}/{np.max(np.abs(np.diff(mono))):.5f}")


def main():
    alle = "--alle" in sys.argv
    for name in (list(SETS) if alle else [CHOICE]):
        mono = pad(name, SECONDS, RATE)
        path = (f"/home/claude/pad_{name}.ogg" if alle
                else os.path.join(OUT_DIR, "pad_loop.ogg"))
        write(name, mono, RATE, path)


if __name__ == "__main__":
    main()
