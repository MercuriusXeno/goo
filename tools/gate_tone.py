"""Synthesizes Astral's gate rising tone, one mono take.

Decision gate-fixates-your-view-on-the-moon-until-it-takes-you: the screen floods white on a rising tone. The server
sends the tone to the caster as the fixation begins; it rises over the whole fixation, astral_gate.json's 100 ticks,
and peaks as the white stands full and the gate carries the caster. The level change then stops every sound the
client plays, so the tone is built to be cut at its peak, with a short release past it in case the cut comes late.

The tone is a soft organ chord, a root, its fifth and its octave, each voice two sines a hair apart so it shimmers,
gliding up three octaves on a curve that creeps and then rushes, as the moon swells, while its loudness swells from
nothing to the peak.

Run from the repository root:
    python tools/gate_tone.py                 # writes the shipped take
    python tools/gate_tone.py --take DIR      # writes the take to DIR to listen to
Needs numpy and soundfile (libsndfile with Ogg Vorbis).
"""

import argparse
import pathlib

import numpy as np
import soundfile

SAMPLE_RATE = 44100
PEAK = 0.8
SOUNDS = pathlib.Path("src/main/resources/assets/goo/sounds/effects")
SHIPPED = "gate_tone.ogg"

# The fixation's length: astral_gate.json's fixation of 100 ticks at 20 ticks a second.
RISE_SECONDS = 5.0
# A short fall past the peak, heard only where the level change lands late.
RELEASE_SECONDS = 0.25
START_HZ = 110.0
OCTAVES = 3.0
# How sharply the glide and the swell bend: one is even, higher creeps longer and then rushes.
GLIDE_BEND = 2.2
SWELL_BEND = 1.6
# Each voice of the chord, as a ratio over the root, and how loud it sits.
VOICES = ((1.0, 1.0), (1.5, 0.55), (2.0, 0.4))
# How far apart each voice's two sines sit, a share of its pitch, so the voice shimmers.
DETUNE = 0.004


def rising_tone():
    """The tone, normalized: the chord gliding up as it swells, then a short release."""
    rise = int(SAMPLE_RATE * RISE_SECONDS)
    release = int(SAMPLE_RATE * RELEASE_SECONDS)
    share = np.concatenate([np.linspace(0.0, 1.0, rise, endpoint=False), np.ones(release)])
    pitch = START_HZ * 2.0 ** (OCTAVES * share ** GLIDE_BEND)
    # The phase each frequency reaches, summed sample by sample, so the glide stays continuous.
    phase = 2.0 * np.pi * np.cumsum(pitch) / SAMPLE_RATE
    chord = np.zeros_like(share)
    for ratio, level in VOICES:
        for detune in (-DETUNE, DETUNE):
            chord += level * np.sin(phase * ratio * (1.0 + detune))
    swell = share ** SWELL_BEND
    swell[rise:] = np.linspace(1.0, 0.0, release)
    signal = chord * swell
    return (signal * PEAK / np.max(np.abs(signal))).astype(np.float32)


def write(path, signal):
    path.parent.mkdir(parents=True, exist_ok=True)
    soundfile.write(str(path), signal, SAMPLE_RATE, format="OGG", subtype="VORBIS")
    print(f"wrote {path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--take", type=pathlib.Path, help="write the take to this folder to listen to")
    args = parser.parse_args()
    write((args.take or SOUNDS) / SHIPPED, rising_tone())


if __name__ == "__main__":
    main()
