"""Synthesizes goo's dull earthquake rumble, a short seamless mono loop.

Decision spire-rumbles-like-a-dull-earthquake: vanilla ships no earthquake
rumble, so the mod ships its own. The rumble is a low roar: noise held in a
low band with a fainter body above it, shaped in the frequency domain over the
whole loop so its end runs into its start with no click, its loudness churning
at random rather than pulsing, which the operator heard as a bloodstream, and
with no crackle, which the operator heard as electricity; driven into a soft
clip so it roughens like distant thunder.

The operator kept all three takes, so the mod ships each as a variation the
game picks at random as the rumble starts.

Run from the repository root:
    python tools/earth_rumble.py                 # writes the shipped takes
    python tools/earth_rumble.py --takes DIR     # writes every take to DIR to listen to
Needs numpy and soundfile (libsndfile with Ogg Vorbis).
"""

import argparse
import pathlib

import numpy as np
import soundfile

SAMPLE_RATE = 44100
SEED = 20261107
PEAK = 0.85
SOUNDS = pathlib.Path("src/main/resources/assets/goo/sounds/effects")

# Each take: its loop length; the roar's band and how steeply it falls with pitch (1 brown); a fainter body band
# above it that carries on small speakers and its level; how fast and how deep the roar's loudness churns,
# at random, never in a rhythm; and how hard it is driven into a soft clip, which roughens it like distant thunder.
TAKES = {
    "d": dict(seconds=2.0, low_hz=20.0, high_hz=160.0, tilt=1.0, body_low=160.0, body_high=350.0, body=0.3,
              churn_low=3.0, churn_high=9.0, churn=0.45, drive=1.5),
    "e": dict(seconds=2.0, low_hz=15.0, high_hz=100.0, tilt=1.0, body_low=100.0, body_high=260.0, body=0.35,
              churn_low=2.0, churn_high=7.0, churn=0.35, drive=3.0),
    "f": dict(seconds=1.5, low_hz=30.0, high_hz=250.0, tilt=0.9, body_low=250.0, body_high=450.0, body=0.2,
              churn_low=2.0, churn_high=6.0, churn=0.6, drive=1.2),
}
# Each take the mod ships, by the file it ships as; sounds.json names each.
SHIPPED = {"d": "earth_rumble.ogg", "e": "earth_rumble2.ogg", "f": "earth_rumble3.ogg"}


def band_noise(samples, rng, low_hz, high_hz, tilt):
    """Noise held between two pitches, periodic over the loop, falling with pitch by the tilt."""
    spectrum = np.fft.rfft(rng.standard_normal(samples))
    freqs = np.fft.rfftfreq(samples, 1.0 / SAMPLE_RATE)
    shape = np.zeros_like(freqs)
    band = (freqs >= low_hz) & (freqs <= high_hz)
    shape[band] = freqs[band] ** -tilt
    # Soft shoulders, so the band's edges do not ring.
    above = freqs > high_hz
    shape[above] = high_hz ** -tilt * np.exp(-((freqs[above] - high_hz) / (0.25 * high_hz)) ** 2)
    signal = np.fft.irfft(spectrum * shape, samples)
    return signal / np.max(np.abs(signal))


def take(name):
    """One take of the rumble, normalized: the roar and its body, their loudness churning at random."""
    spec = TAKES[name]
    samples = int(SAMPLE_RATE * spec["seconds"])
    rng = np.random.default_rng(SEED)
    roar = band_noise(samples, rng, spec["low_hz"], spec["high_hz"], spec["tilt"])
    roar = roar + spec["body"] * band_noise(samples, rng, spec["body_low"], spec["body_high"], 0.5)
    # Loudness churning at random within a band of rates, periodic over the loop so the loop stays seamless.
    churn = band_noise(samples, rng, spec["churn_low"], spec["churn_high"], 0.0)
    roar = roar * (1.0 + spec["churn"] * churn)
    roar = np.tanh(spec["drive"] * roar / np.max(np.abs(roar)))
    return (roar * PEAK / np.max(np.abs(roar))).astype(np.float32)


def write(path, signal):
    path.parent.mkdir(parents=True, exist_ok=True)
    soundfile.write(str(path), signal, SAMPLE_RATE, format="OGG", subtype="VORBIS")
    print(f"wrote {path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--takes", type=pathlib.Path, help="write every take to this folder to listen to")
    args = parser.parse_args()
    if args.takes:
        for name in TAKES:
            write(args.takes / f"earth_rumble_{name}.ogg", take(name))
    else:
        for name, file in SHIPPED.items():
            write(SOUNDS / file, take(name))


if __name__ == "__main__":
    main()
