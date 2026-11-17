"""Synthesizes Lunar's tone, one mono take: a swelling drone with no pitch glide.

Decision gate-fixates-your-view-on-the-moon-until-it-takes-you: the screen floods white on a rising tone. The server
sends the tone to the caster as the fixation begins; it rises over the whole fixation, astral_lunar.json's 100 ticks,
and peaks as the white stands full and Lunar carries the caster. The level change then stops every sound the
client plays, so the tone is built to be cut at its peak, with a short release past it in case the cut comes late.

The operator heard a first take, a chord gliding up three octaves, as a slide whistle, and chose a swelling drone
with no glide in its place: what rises is loudness and brightness, never pitch. A low drone, a root, its fifth and
its octave, each voice a stack of harmonics a hair detuned so it beats slowly, opens its upper harmonics as the
trip runs, while an airy roar of noise swells beneath it and brightens from a low rumble to a wide rush.

Run from the repository root:
    python tools/lunar_tone.py                 # writes the shipped take
    python tools/lunar_tone.py --take DIR      # writes the take to DIR to listen to
Needs numpy and soundfile (libsndfile with Ogg Vorbis).
"""

import argparse
import pathlib

import numpy as np
import soundfile

SAMPLE_RATE = 44100
SEED = 20261116
PEAK = 0.85
SOUNDS = pathlib.Path("src/main/resources/assets/goo/sounds/effects")
SHIPPED = "lunar_tone.ogg"

# The fixation's length: astral_lunar.json's fixation of 100 ticks at 20 ticks a second.
RISE_SECONDS = 5.0
# A short fall past the peak, heard only where the level change lands late.
RELEASE_SECONDS = 0.25
ROOT_HZ = 55.0
# Each voice of the drone, as a ratio over the root, and how loud it sits.
VOICES = ((1.0, 1.0), (1.5, 0.6), (2.0, 0.45))
# The harmonics each voice stacks, and how far apart its two detuned stacks sit, a share of its pitch.
HARMONICS = 24
DETUNE = 0.003
# How many harmonics stand open at the start and at the peak: the drone brightens without moving its pitch.
OPEN_AT_START = 1.5
OPEN_AT_PEAK = 18.0
# The roar: noise whose band reaches from a low rumble at the start to a wide rush at the peak, and its level.
ROAR_LOW_HZ = 40.0
ROAR_START_HIGH_HZ = 250.0
ROAR_PEAK_HIGH_HZ = 6000.0
ROAR = 0.55
# How sharply the swell bends: higher creeps longer and then rushes, as the moon does.
SWELL_BEND = 1.8
BRIGHTEN_BEND = 1.4
# The roar is shaped in blocks this long, each its own band, crossfaded so no edge clicks.
BLOCK_SECONDS = 0.1


def drone(share):
    """The drone at each sample: harmonic stacks at a fixed pitch, their upper harmonics opening with the share."""
    seconds = np.arange(share.size) / SAMPLE_RATE
    open_harmonics = OPEN_AT_START + (OPEN_AT_PEAK - OPEN_AT_START) * share ** BRIGHTEN_BEND
    signal = np.zeros_like(share)
    for ratio, level in VOICES:
        for harmonic in range(1, HARMONICS + 1):
            # A harmonic fades in once the opening passes it, softly over one harmonic's width.
            opened = np.clip(open_harmonics - harmonic + 1.0, 0.0, 1.0)
            for detune in (-DETUNE, DETUNE):
                pitch = ROOT_HZ * ratio * harmonic * (1.0 + detune)
                signal += level * opened / harmonic * np.sin(2.0 * np.pi * pitch * seconds)
    return signal / np.max(np.abs(signal))


def roar(share, rng):
    """Noise in a band whose top climbs with the share, shaped block by block and crossfaded."""
    block = int(SAMPLE_RATE * BLOCK_SECONDS)
    hop = block // 2
    window = np.hanning(block)
    signal = np.zeros(share.size + block)
    freqs = np.fft.rfftfreq(block, 1.0 / SAMPLE_RATE)
    for start in range(0, share.size, hop):
        at = share[min(start + hop, share.size - 1)] ** BRIGHTEN_BEND
        high = ROAR_START_HIGH_HZ * (ROAR_PEAK_HIGH_HZ / ROAR_START_HIGH_HZ) ** at
        shape = ((freqs >= ROAR_LOW_HZ) & (freqs <= high)) / np.sqrt(np.maximum(freqs, ROAR_LOW_HZ))
        piece = np.fft.irfft(np.fft.rfft(rng.standard_normal(block)) * shape, block)
        signal[start:start + block] += piece * window
    signal = signal[:share.size]
    return signal / np.max(np.abs(signal))


def swelling_drone():
    """The tone, normalized: the drone and the roar swelling and brightening to the peak, then a short release."""
    rise = int(SAMPLE_RATE * RISE_SECONDS)
    release = int(SAMPLE_RATE * RELEASE_SECONDS)
    share = np.concatenate([np.linspace(0.0, 1.0, rise, endpoint=False), np.ones(release)])
    rng = np.random.default_rng(SEED)
    signal = drone(share) + ROAR * roar(share, rng)
    swell = share ** SWELL_BEND
    swell[rise:] = np.linspace(1.0, 0.0, release)
    signal = np.tanh(1.5 * signal * swell)
    return (signal * PEAK / np.max(np.abs(signal))).astype(np.float32)


def write(path, signal):
    path.parent.mkdir(parents=True, exist_ok=True)
    soundfile.write(str(path), signal, SAMPLE_RATE, format="OGG", subtype="VORBIS")
    print(f"wrote {path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--take", type=pathlib.Path, help="write the take to this folder to listen to")
    args = parser.parse_args()
    write((args.take or SOUNDS) / SHIPPED, swelling_drone())


if __name__ == "__main__":
    main()
