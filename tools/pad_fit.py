"""
Macht eine fremde Aufnahme fuer den Hub brauchbar: Naht schliessen,
Tieffrequentes raus, auf Pegel bringen.

WOFUER:
Eine Aufnahme von Freesound klingt besser als jede gerechnete Flaeche,
weil ein Raum und ein Instrument dahinterstehen. Aber sie ist nicht
fuer diesen Zweck gemischt. Drei Dinge muss man fast immer richten.

1. DIE NAHT.
   "Loop" im Dateinamen heisst nicht, dass die Datei im Kreis
   aufgeht. Bei 496506__vectorspace__ambient_loop_A_001b.wav betraegt
   der Sprung zwischen letztem und erstem Abtastwert 0,068, der
   groesste gewoehnliche Schritt innerhalb der Datei aber nur 0,010 -
   also fast das Siebenfache. Das knackt bei jeder Wiederholung
   hoerbar. Hier wird deshalb das Ende ueber den Anfang geblendet;
   die Datei wird dabei um die Blendlaenge kuerzer.

2. DAS UNTERSTE BAND.
   In derselben Datei liegen 72 Prozent der Leistung unter 80 Hz.
   Polyfrost hat dort 0,4 Prozent.

   ABER DAS IST KEIN RUMPELN, und diesen Fehler habe ich zuerst
   gemacht: Nachgemessen steht die Aufnahme in C, mit der Wurzel auf
   C2 bei 65,1 Hz und daruber C3, F3, C4 und A#4. Die 72 Prozent sind
   der GRUNDTON. Ein Hochpass bei 80 Hz schneidet ihn weg - deshalb
   klingt die Fassung hp80 duenn, und deshalb faellt dort auch die
   Stereobreite von 0,16 auf 0,10.

   Das Problem bleibt trotzdem eins: Auf Notebooklautsprechern ist
   C2 praktisch nicht wiederzugeben, und die Energie frisst die
   Aussteuerung weg, sodass der hoerbare Teil leiser gemischt werden
   muss. Die brauchbare Antwort ist deshalb kein Schnitt, sondern
   eine Absenkung - der Grundton bleibt, gibt aber Platz ab.

   Dieses Werkzeug schreibt mehrere Fassungen; entschieden wird nach
   Gehoer, nicht nach der Tabelle.

3. DER PEGEL.
   Die Vorlage liegt bei Spitze 0,17. Rohdateien von Freesound sind
   meist deutlich lauter ausgesteuert; diese hier bei 0,31.

Ausfuehren aus dem Projektordner:
    python3 tools/pad_fit.py <datei.wav> [zielordner]
Braucht: numpy und ffmpeg
"""
import os
import subprocess
import sys
import wave

import numpy as np

RATE = 44100
PEAK = 0.17

# Laenge der Ueberblendung an der Naht. Lang genug, dass der Uebergang
# nicht auffaellt, kurz genug, dass nicht die halbe Aufnahme doppelt
# liegt. Bei einer Flaeche ohne Rhythmus ist das unkritisch.
FADE = 3.0

# Welche Fassungen geschrieben werden.
#   ("hp", hz)     harter Hochpass - schneidet weg
#   ("shelf", hz, db)  Absenkung unterhalb hz um db - behaelt den Ton
#   ("roh", 0)     nur Naht und Pegel
FASSUNGEN = [
    ("roh", 0, 0.0),
    ("hp80", 80, 0.0),
    ("shelf6", 90, -6.0),
    ("shelf12", 90, -12.0),
]


def load(path):
    raw = subprocess.run(
        ["ffmpeg", "-v", "quiet", "-i", path, "-f", "f32le",
         "-ac", "2", "-ar", str(RATE), "-"],
        capture_output=True).stdout
    return np.frombuffer(raw, dtype="<f4").astype(np.float64).reshape(-1, 2)


def naht_schliessen(x, fade_s):
    """Das Ende ueber den Anfang blenden.

    Die letzten fade_s Sekunden werden ausgeblendet und auf die
    eingeblendeten ersten fade_s Sekunden addiert. Danach ist der
    letzte Abtastwert die natuerliche Fortsetzung des ersten, und die
    Wiederholung ist nicht mehr zu hoeren. Die Datei verliert dabei
    fade_s Sekunden.

    Gleichlaufende Blenden (sin/cos) statt linearer: Bei zwei nicht
    zusammenhaengenden Signalen haelt das die Leistung konstant,
    waehrend eine lineare Blende in der Mitte einbricht.
    """
    n = int(fade_s * RATE)
    if n * 2 >= len(x):
        return x
    kopf = x[:n].copy()
    rest = x[n:len(x) - n]
    schwanz = x[len(x) - n:]
    w = np.linspace(0.0, np.pi / 2.0, n)[:, None]
    gemischt = kopf * np.sin(w) + schwanz * np.cos(w)
    return np.concatenate([gemischt, rest])


def tiefen(x, hz, db):
    """Unterhalb hz absenken - um db, oder ganz weg wenn db 0 ist.

    Ueber die FFT, weil die Datei ohnehin ganz im Speicher liegt und
    das so keine Phasendrehung einfuehrt. Der Uebergang laeuft ueber
    eine halbe Oktave, damit es nicht nach Telefon klingt.
    """
    if hz <= 0:
        return x
    n = len(x)
    f = np.fft.rfftfreq(n, 1.0 / RATE)
    rampe = np.clip(np.log2(np.maximum(f, 1.0) / (hz / 1.5)) / np.log2(1.5),
                    0.0, 1.0)
    boden = 0.0 if db == 0.0 else 10.0 ** (db / 20.0)
    g = boden + (1.0 - boden) * rampe
    out = np.empty_like(x)
    for c in range(x.shape[1]):
        out[:, c] = np.fft.irfft(np.fft.rfft(x[:, c]) * g, n)
    return out


def messen(x):
    m = x.mean(axis=1)
    n = len(m)
    sp = np.abs(np.fft.rfft(m * np.hanning(n))) ** 2
    f = np.fft.rfftfreq(n, 1.0 / RATE)
    tot = sp.sum()

    def band(a, b):
        return sp[(f >= a) & (f < b)].sum() / tot * 100.0

    sec = int(n / RATE)
    per = np.array([np.sqrt((m[i * RATE:(i + 1) * RATE] ** 2).mean())
                    for i in range(sec)])
    return (f"{n / RATE:5.1f} s  Spitze {np.max(np.abs(m)):.3f}  "
            f"RMS {20 * np.log10(np.sqrt((m ** 2).mean())):6.1f} dBFS  "
            f"Schwankung {20 * np.log10(per.max() / max(per.min(), 1e-9)):4.1f} dB  "
            f"Stereo {np.corrcoef(x[:, 0], x[:, 1])[0, 1]:+.2f}  "
            f"<80 {band(0, 80):4.1f} | 80-300 {band(80, 300):4.1f} | "
            f"300-1k {band(300, 1000):4.1f} | >1k {band(1000, 22050):4.1f} %  "
            f"Naht {abs(m[0] - m[-1]):.5f}/{np.max(np.abs(np.diff(m))):.5f}")


def schreiben(x, path):
    pcm = (np.clip(x, -1.0, 1.0) * 32767.0).astype("<i2")
    tmp = path.replace(".ogg", ".wav")
    with wave.open(tmp, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp,
                    "-c:a", "libvorbis", "-q:a", "4", path], check=True)
    os.remove(tmp)


def main():
    quelle = sys.argv[1]
    ziel = sys.argv[2] if len(sys.argv) > 2 else "/home/claude"
    os.makedirs(ziel, exist_ok=True)

    roh = load(quelle)
    print(f"{'roh':10s} {messen(roh)}")

    zu = naht_schliessen(roh, FADE)
    for name, hz, db in FASSUNGEN:
        y = tiefen(zu, hz, db)
        y = y / (np.max(np.abs(y.mean(axis=1))) or 1.0) * PEAK
        pfad = os.path.join(ziel, "fit_" + name + ".ogg")
        schreiben(y, pfad)
        print(f"{'fit_' + name:10s} {messen(y)}")


if __name__ == "__main__":
    main()
