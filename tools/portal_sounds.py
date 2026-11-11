"""Synthesizes goo's three portal sounds, Portal-game style, each mono.

Decision portal-placement-plays-a-zap-that-opens-into-a-hum: placing a portal
plays a short energetic zap that opens into a brief hum, like a portal gun
shot landing. The zap is a bright chirp falling onto the hum's pitch, buzzing
with frequency modulation, over a swirl of noise whose pitch falls with it;
the hum it lands on swells as the zap fades, then dies away.

Decision stepping-through-plays-a-soft-whoosh-at-both-ends: stepping through
plays a quick soft airy whoosh. The whoosh is noise through a soft band whose
pitch rises and falls as the loudness swells and fades.

Decision standing-portals-loop-a-soft-ambient-hum: a standing portal loops a
soft ambient hum. The hum is a low tone and its overtones, warbling slowly,
under a breath of noise; every part repeats a whole number of times over the
loop, so its end runs into its start with no seam.

The operator kept take a of each sound, so the mod ships one file of each.

Run from the repository root:
    python tools/portal_sounds.py                 # writes the shipped takes
    python tools/portal_sounds.py --takes DIR     # writes every take to DIR to listen to
Needs numpy and soundfile (libsndfile with Ogg Vorbis).
"""

import argparse
import pathlib

import numpy as np
import soundfile

SAMPLE_RATE = 44100
SEED = 20261111
PEAK = 0.85
SOUNDS = pathlib.Path("src/main/resources/assets/goo/sounds/effects")
# A one-shot fades in and out over these, so it starts and ends with no click.
EDGE_SECONDS = 0.004

# Each placement take: its length; the zap's chirp, from its start pitch down onto the hum's pitch over its
# fall, buzzing at the modulation rate and depth; the swirl of noise under the zap, its band falling from the
# start pitch to the end pitch, and its level; the hum's pitch, its overtones' levels and their slow beating,
# how long it takes to die away, and its level against the zap.
PLACE_TAKES = {
    "a": dict(seconds=1.0, chirp_from=1600.0, fall=0.14, buzz_hz=70.0, buzz=1.2, swirl_from=5000.0,
              swirl_to=400.0, swirl=0.35, hum_hz=140.0, overtones=(1.0, 0.45, 0.2, 0.08), beat_hz=3.0,
              hum_decay=0.35, hum=0.7),
    "b": dict(seconds=0.9, chirp_from=2400.0, fall=0.09, buzz_hz=110.0, buzz=2.0, swirl_from=7000.0,
              swirl_to=700.0, swirl=0.45, hum_hz=180.0, overtones=(1.0, 0.6, 0.3, 0.15), beat_hz=4.5,
              hum_decay=0.28, hum=0.6),
    "c": dict(seconds=1.2, chirp_from=1100.0, fall=0.2, buzz_hz=45.0, buzz=0.8, swirl_from=3500.0,
              swirl_to=250.0, swirl=0.3, hum_hz=110.0, overtones=(1.0, 0.35, 0.15, 0.05), beat_hz=2.0,
              hum_decay=0.5, hum=0.8),
}

# Each stepping-through take: its length; the band the noise passes through, rising from its low pitch to
# its peak pitch and falling back as the loudness swells; how narrow the band is (higher rings more); where
# in the take the swell peaks; and a lowpass that softens what the band lets through.
CROSS_TAKES = {
    "a": dict(seconds=0.45, low_hz=350.0, peak_hz=1800.0, resonance=1.2, swell_at=0.4, soften_hz=4000.0),
    "b": dict(seconds=0.35, low_hz=500.0, peak_hz=2600.0, resonance=1.6, swell_at=0.3, soften_hz=6000.0),
    "c": dict(seconds=0.6, low_hz=250.0, peak_hz=1200.0, resonance=0.9, swell_at=0.5, soften_hz=3000.0),
}

# Each hum take: its loop length; the tone's pitch and its overtones' levels; a second voice a few cycles
# off the first, which beats against it; how fast and how deep the pitch warbles and the loudness breathes;
# and the breath of noise under it, its band and its level.
HUM_TAKES = {
    "a": dict(seconds=4.0, hum_hz=110.0, overtones=(1.0, 0.4, 0.18, 0.08), detune_hz=1.0, warble_hz=0.5,
              warble=0.6, breathe_hz=0.25, breathe=0.15, air_low=300.0, air_high=1400.0, air=0.12),
    "b": dict(seconds=4.0, hum_hz=82.5, overtones=(1.0, 0.6, 0.35, 0.2), detune_hz=0.75, warble_hz=0.75,
              warble=0.9, breathe_hz=0.5, breathe=0.2, air_low=200.0, air_high=900.0, air=0.08),
    "c": dict(seconds=3.0, hum_hz=146.67, overtones=(1.0, 0.3, 0.1, 0.05), detune_hz=1.33, warble_hz=0.33,
              warble=0.4, breathe_hz=0.33, breathe=0.1, air_low=500.0, air_high=2500.0, air=0.18),
}

# Each take the mod ships, by the file it ships as; sounds.json names each.
SHIPPED = {
    "place": {"a": "portal_place.ogg"},
    "cross": {"a": "portal_cross.ogg"},
    "hum": {"a": "portal_hum.ogg"},
}


def times(seconds):
    return np.arange(int(SAMPLE_RATE * seconds)) / SAMPLE_RATE


def phase_of(pitch_hz):
    """The running phase of a tone whose pitch moves sample by sample, so the tone never jumps."""
    return 2.0 * np.pi * np.cumsum(pitch_hz) / SAMPLE_RATE


def band_pass(signal, centre_hz, resonance):
    """A state-variable band pass whose centre may move sample by sample."""
    centre_hz = np.broadcast_to(centre_hz, signal.shape)
    damping = 1.0 / resonance
    low = band = 0.0
    out = np.empty_like(signal)
    for index, sample in enumerate(signal):
        tuning = 2.0 * np.sin(np.pi * min(centre_hz[index], SAMPLE_RATE / 6.0) / SAMPLE_RATE)
        low += tuning * band
        high = sample - low - damping * band
        band += tuning * high
        out[index] = band
    return out


def low_pass(signal, cutoff_hz):
    """A one-pole lowpass, gentle, for softening."""
    weight = 1.0 - np.exp(-2.0 * np.pi * cutoff_hz / SAMPLE_RATE)
    out = np.empty_like(signal)
    held = 0.0
    for index, sample in enumerate(signal):
        held += weight * (sample - held)
        out[index] = held
    return out


def faded_edges(signal):
    edge = int(SAMPLE_RATE * EDGE_SECONDS)
    ramp = np.linspace(0.0, 1.0, edge)
    signal[:edge] *= ramp
    signal[-edge:] *= ramp[::-1]
    return signal


def normalized(signal):
    return (signal * PEAK / np.max(np.abs(signal))).astype(np.float32)


def overtone_stack(phase, levels):
    return sum(level * np.sin((index + 1) * phase) for index, level in enumerate(levels))


def place_take(name):
    """The placement zap: a buzzing chirp falling onto a hum's pitch over a falling swirl, the hum then dying away."""
    spec = PLACE_TAKES[name]
    t = times(spec["seconds"])
    rng = np.random.default_rng(SEED)
    fall = np.clip(t / spec["fall"], 0.0, 1.0)
    # The chirp falls exponentially in pitch, quick at first, settling on the hum's pitch.
    chirp_hz = spec["hum_hz"] * (spec["chirp_from"] / spec["hum_hz"]) ** ((1.0 - fall) ** 2)
    buzz = spec["buzz"] * np.sin(2.0 * np.pi * spec["buzz_hz"] * t)
    zap = np.sin(phase_of(chirp_hz) + buzz) + 0.4 * np.sin(2.0 * phase_of(chirp_hz) + 1.5 * buzz)
    zap_loudness = np.exp(-t / (0.6 * spec["fall"])) * (1.0 - np.exp(-t / 0.002))
    swirl_hz = spec["swirl_to"] * (spec["swirl_from"] / spec["swirl_to"]) ** (1.0 - np.clip(t / (2.0 * spec["fall"]), 0, 1))
    swirl = band_pass(rng.standard_normal(t.size), swirl_hz, 2.0)
    swirl = swirl / np.max(np.abs(swirl)) * np.exp(-t / (1.2 * spec["fall"]))
    # The hum swells as the zap fades, its two voices beating slowly, then dies away.
    hum_phase = 2.0 * np.pi * spec["hum_hz"] * t
    beat_phase = 2.0 * np.pi * (spec["hum_hz"] + spec["beat_hz"]) * t
    hum = overtone_stack(hum_phase, spec["overtones"]) + 0.6 * overtone_stack(beat_phase, spec["overtones"])
    hum = hum / np.max(np.abs(hum))
    hum_loudness = (1.0 - np.exp(-t / (0.5 * spec["fall"]))) * np.exp(-np.maximum(t - spec["fall"], 0) / spec["hum_decay"])
    signal = zap * zap_loudness + spec["swirl"] * swirl + spec["hum"] * hum * hum_loudness
    return normalized(faded_edges(signal))


def cross_take(name):
    """The stepping-through whoosh: soft noise through a band rising and falling as it swells and fades."""
    spec = CROSS_TAKES[name]
    t = times(spec["seconds"])
    rng = np.random.default_rng(SEED)
    place = t / spec["seconds"]
    # One smooth swell, peaking where the take says, as a body passes the portal's face.
    rise = np.clip(place / spec["swell_at"], 0.0, 1.0)
    fade = np.clip((1.0 - place) / (1.0 - spec["swell_at"]), 0.0, 1.0)
    swell = np.where(place < spec["swell_at"], np.sin(0.5 * np.pi * rise) ** 2, np.sin(0.5 * np.pi * fade) ** 3)
    centre_hz = spec["low_hz"] * (spec["peak_hz"] / spec["low_hz"]) ** swell
    air = band_pass(rng.standard_normal(t.size), centre_hz, spec["resonance"])
    air = low_pass(air, spec["soften_hz"])
    return normalized(faded_edges(air * swell))


def periodic_pitch(hz, seconds):
    """The nearest pitch repeating a whole number of times over the loop, so the loop has no seam."""
    return max(1, round(hz * seconds)) / seconds


def periodic_band_noise(samples, rng, low_hz, high_hz):
    """Noise held between two pitches, built over the whole loop so it repeats with no seam."""
    spectrum = np.fft.rfft(rng.standard_normal(samples))
    freqs = np.fft.rfftfreq(samples, 1.0 / SAMPLE_RATE)
    centre = np.sqrt(low_hz * high_hz)
    width = np.log(high_hz / low_hz) / 2.0
    shape = np.exp(-(np.log(np.maximum(freqs, 1.0) / centre) / width) ** 2)
    signal = np.fft.irfft(spectrum * shape, samples)
    return signal / np.max(np.abs(signal))


def hum_take(name):
    """The ambient hum: a warbling tone, a second voice beating against it, and a breath of air, as one seamless loop."""
    spec = HUM_TAKES[name]
    seconds = spec["seconds"]
    t = times(seconds)
    rng = np.random.default_rng(SEED)
    hum_hz = periodic_pitch(spec["hum_hz"], seconds)
    second_hz = periodic_pitch(spec["hum_hz"] + spec["detune_hz"], seconds)
    warble_hz = periodic_pitch(spec["warble_hz"], seconds)
    breathe_hz = periodic_pitch(spec["breathe_hz"], seconds)
    # The warble bends the phase by whole cycles of a whole-number rate, so it too repeats over the loop.
    warble = spec["warble"] * np.sin(2.0 * np.pi * warble_hz * t)
    first = overtone_stack(2.0 * np.pi * hum_hz * t + warble, spec["overtones"])
    second = overtone_stack(2.0 * np.pi * second_hz * t - warble, spec["overtones"])
    tone = (first + 0.7 * second) * (1.0 + spec["breathe"] * np.sin(2.0 * np.pi * breathe_hz * t))
    tone = tone / np.max(np.abs(tone))
    air = periodic_band_noise(t.size, rng, spec["air_low"], spec["air_high"])
    return normalized(tone + spec["air"] * air)


SOUND_TAKES = {"place": (PLACE_TAKES, place_take), "cross": (CROSS_TAKES, cross_take), "hum": (HUM_TAKES, hum_take)}


def write(path, signal):
    path.parent.mkdir(parents=True, exist_ok=True)
    soundfile.write(str(path), signal, SAMPLE_RATE, format="OGG", subtype="VORBIS")
    print(f"wrote {path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--takes", type=pathlib.Path, help="write every take to this folder to listen to")
    args = parser.parse_args()
    for sound, (takes, synthesize) in SOUND_TAKES.items():
        if args.takes:
            for name in takes:
                write(args.takes / f"portal_{sound}_{name}.ogg", synthesize(name))
        else:
            for name, file in SHIPPED[sound].items():
                write(SOUNDS / file, synthesize(name))


if __name__ == "__main__":
    main()
