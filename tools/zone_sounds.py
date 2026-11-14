"""Synthesizes goo's Zone sounds, each mono, as placeholders the operator may swap.

Decision full-block-growth-wubs-and-raises-void-motes: when the room expands by
a full block, a dull sort of "wub" sounds. The operator heard the first take,
a buzzy tone under a closing filter, as a deactivating ability, not a positive
sound, and asked for a musical swell, something spacey. Every take after it is
built of pure tones, no buzz: a chord or a bell swelling in, opening through a
filter and ringing off into a long tail of space.

Decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player:
while the player channels to go to the pocket, a door is conjured in front
of them with sounds. Each take swells across the charge's second; the first is
a breath of air rising through a climbing band under a shimmering tone gliding
up, which the operator heard as close. They found the chord takes climbing a
fourth and a fifth pitched up too high, and picked c3: an open fifth from a low
root, climbing a semitone over rising air.

Run from the repository root:
    python tools/zone_sounds.py               # writes every shipped take
    python tools/zone_sounds.py wub           # writes the named sounds' shipped takes alone
    python tools/zone_sounds.py --takes DIR   # writes every take to DIR to listen to, each reversed take beside
                                                  # the forward take it reverses
Needs numpy, scipy and soundfile (libsndfile with Ogg Vorbis).
"""

import argparse
import pathlib

import numpy as np
import soundfile
from scipy import signal as sg

SAMPLE_RATE = 44100
PEAK = 0.85
SEED = 20261111
SOUNDS = pathlib.Path("src/main/resources/assets/goo/sounds/effects")
# A one-shot fades in and out over these, so it starts and ends with no click.
EDGE_SECONDS = 0.004
# A semitone's frequency ratio.
SEMITONE = 2.0 ** (1.0 / 12.0)


def times(seconds):
    return np.arange(int(seconds * SAMPLE_RATE)) / SAMPLE_RATE


def faded_edges(signal):
    edge = int(EDGE_SECONDS * SAMPLE_RATE)
    ramp = np.linspace(0.0, 1.0, edge)
    signal[:edge] *= ramp
    signal[-edge:] *= ramp[::-1]
    return signal


def normalized(signal):
    return faded_edges(PEAK * signal / np.max(np.abs(signal)))


def swept_low_pass(signal, cutoffs):
    """Filters a signal block by block, each block under its own cutoff, carrying the filter's state across."""
    block = 256
    out = np.zeros_like(signal)
    state = None
    for start in range(0, signal.size, block):
        b, a = sg.butter(2, min(cutoffs[start], SAMPLE_RATE / 2 - 1), btype="low", fs=SAMPLE_RATE)
        if state is None:
            state = sg.lfilter_zi(b, a) * 0.0
        out[start:start + block], state = sg.lfilter(b, a, signal[start:start + block], zi=state)
    return out


def space(signal, seconds, mix, rng):
    """Rings a signal off into space: convolved with a dark, slowly dying burst of noise, and the tail kept on."""
    ir_t = times(seconds)
    ir = rng.standard_normal(ir_t.size) * np.exp(-ir_t / (seconds / 5.0))
    ir = sg.lfilter(*sg.butter(2, 3500.0, btype="low", fs=SAMPLE_RATE), ir)
    wet = sg.fftconvolve(np.concatenate([signal, np.zeros(ir.size)]), ir)[: signal.size + ir.size]
    dry = np.concatenate([signal, np.zeros(ir.size)])
    return (1.0 - mix) * dry / np.max(np.abs(dry)) + mix * wet / np.max(np.abs(wet))


def swell(t, attack, hold, release):
    """Rises smoothly over the attack, holds, then dies away over the release."""
    rise = np.clip(t / attack, 0.0, 1.0)
    rise = rise * rise * (3.0 - 2.0 * rise)
    fall = np.exp(-np.maximum(t - attack - hold, 0.0) / release)
    return rise * fall


def chorused(t, hz, cents, voices, rng):
    """A pure tone thickened by voices detuned a few cents either way, each wavering slowly."""
    out = np.zeros_like(t)
    for voice in range(voices):
        spread = (voice / max(voices - 1, 1) - 0.5) * 2.0 * cents
        drift = 1.0 + 0.0015 * np.sin(2.0 * np.pi * rng.uniform(0.2, 0.6) * t + rng.uniform(0, 2 * np.pi))
        phase = 2.0 * np.pi * np.cumsum(hz * 2.0 ** (spread / 1200.0) * drift) / SAMPLE_RATE
        out += np.sin(phase + rng.uniform(0, 2 * np.pi))
    return out / voices


def chord(t, root_hz, steps, levels, cents, voices, rng, glide=None):
    """A chord of pure tones the given semitones above a root, each at its level, gliding by a pitch curve."""
    curve = np.ones_like(t) if glide is None else glide
    return sum(level * chorused(t, root_hz * SEMITONE ** step * curve, cents, voices, rng)
               for step, level in zip(steps, levels))


def buzzy_wub(spec):
    """Take a, the first shipped: a gliding buzzy tone through a filter that opens and closes once, over a thump."""
    t = times(spec["seconds"])
    pitch = spec["pitch_from"] * (spec["pitch_to"] / spec["pitch_from"]) ** (t / t[-1])
    phase = 2.0 * np.pi * np.cumsum(pitch) / SAMPLE_RATE
    tone = sum(np.sin(k * phase) / k for k in range(1, spec["overtones"] + 1))
    rise = np.exp(-((t - spec["filter_peak_at"]) / (spec["filter_peak_at"] * 0.9)) ** 2)
    shaped = swept_low_pass(tone, spec["filter_floor"] + (spec["filter_peak"] - spec["filter_floor"]) * rise)
    sub = spec["sub"] * np.sin(2.0 * np.pi * spec["sub_hz"] * t) * np.exp(-t / spec["sub_decay"])
    envelope = np.minimum(1.0, t / 0.02) * np.exp(-t / spec["decay"])
    return normalized((shaped / np.max(np.abs(shaped)) + sub) * envelope)


def chord_swell(spec):
    """A chord of pure tones swelling in as a filter opens over it, rising a little in pitch, ringing into space."""
    rng = np.random.default_rng(SEED)
    t = times(spec["seconds"])
    glide = SEMITONE ** (spec["rise_semitones"] * np.clip(t / spec["attack"], 0.0, 1.0))
    tone = chord(t, spec["root_hz"], spec["steps"], spec["levels"], spec["cents"], 3, rng, glide)
    opening = np.clip(t / spec["attack"], 0.0, 1.0)
    shaped = swept_low_pass(tone, spec["filter_from"] + (spec["filter_to"] - spec["filter_from"]) * opening)
    body = shaped * swell(t, spec["attack"], spec["hold"], spec["release"])
    if spec.get("sparkle"):
        top = chorused(t, spec["root_hz"] * SEMITONE ** 24, 6.0, 2, rng) * np.sin(2.0 * np.pi * 7.0 * t) ** 2
        body += spec["sparkle"] * top * swell(t - spec["attack"] * 0.5, spec["attack"], 0.0, spec["release"] * 0.6)
    return normalized(cut_to(space(body, spec["space"], spec["wet"], rng), spec.get("length")))


def cut_to(signal, seconds):
    """Cuts a sound to a length, its tail faded out over its last third so the space closes rather than stops."""
    if seconds is None:
        return signal
    kept = signal[:int(seconds * SAMPLE_RATE)].copy()
    fade = kept.size // 3
    kept[-fade:] *= np.cos(np.linspace(0.0, np.pi / 2.0, fade)) ** 2
    return kept


def bell_swell(spec):
    """A soft-struck bell of a few partials, its attack rounded off into a swell, with a high air over it, in space."""
    rng = np.random.default_rng(SEED)
    t = times(spec["seconds"])
    bell = sum(level * chorused(t, spec["root_hz"] * ratio, 3.0, 2, rng) * np.exp(-t / (spec["ring"] / ratio))
               for ratio, level in zip(spec["ratios"], spec["levels"]))
    air = rng.standard_normal(t.size)
    air = sg.lfilter(*sg.butter(2, [spec["air_low"], spec["air_high"]], btype="band", fs=SAMPLE_RATE), air)
    body = (bell / np.max(np.abs(bell)) + spec["air"] * air / np.max(np.abs(air))) \
        * swell(t, spec["attack"], 0.0, spec["ring"])
    return normalized(space(body, spec["space"], spec["wet"], rng))


def airy_conjure(spec):
    """Take a, the first shipped: air swelling up through a rising band under a tone gliding up a fifth."""
    t = times(spec["seconds"])
    share = t / t[-1]
    rng = np.random.default_rng(SEED)
    noise = rng.standard_normal(t.size)
    band = spec["air_from"] * (spec["air_to"] / spec["air_from"]) ** share
    air = swept_low_pass(noise, band) - swept_low_pass(noise, band * 0.5)
    pitch = spec["pitch_from"] * (spec["pitch_to"] / spec["pitch_from"]) ** share
    phase = 2.0 * np.pi * np.cumsum(pitch) / SAMPLE_RATE
    shimmer_hz = spec["shimmer_from"] + (spec["shimmer_to"] - spec["shimmer_from"]) * share
    shimmer = 1.0 + spec["shimmer"] * np.sin(2.0 * np.pi * np.cumsum(shimmer_hz) / SAMPLE_RATE)
    tone = (np.sin(phase) + 0.4 * np.sin(2.0 * phase)) * shimmer
    rise = share ** spec["swell"] * np.minimum(1.0, (t[-1] - t) / spec["release"])
    return normalized((spec["air"] * air / np.max(np.abs(air)) + tone / np.max(np.abs(tone))) * rise)


def chord_conjure(spec):
    """Air rising through a climbing band under a chord gliding up, both swelling across the charge, in space."""
    rng = np.random.default_rng(SEED)
    t = times(spec["seconds"])
    share = np.clip(t / spec["charge"], 0.0, 1.0)
    noise = rng.standard_normal(t.size)
    band = spec["air_from"] * (spec["air_to"] / spec["air_from"]) ** share
    air = swept_low_pass(noise, band) - swept_low_pass(noise, band * 0.5)
    glide = SEMITONE ** (spec["rise_semitones"] * share)
    tone = chord(t, spec["root_hz"], spec["steps"], spec["levels"], 8.0, 3, rng, glide)
    shimmer = 1.0 + spec["shimmer"] * np.sin(2.0 * np.pi * np.cumsum(4.0 + 14.0 * share) / SAMPLE_RATE)
    rise = share ** spec["swell"] * np.exp(-np.maximum(t - spec["charge"], 0.0) / spec["release"])
    body = (spec["air"] * air / np.max(np.abs(air)) + tone * shimmer / np.max(np.abs(tone))) * rise
    return normalized(cut_to(space(body, spec["space"], spec["wet"], rng), spec.get("length")))


# Each wub take. a is the first, buzzy one. b to e are pure tones: a chord's root, its notes in semitones above it
# and their levels, its chorus spread in cents, how far it rises as it swells, the filter's opening, the swell's
# attack, hold and release, and how long and how loud its space rings; e is a bell of partials instead.
WUB_TAKES = {
    "a": (buzzy_wub, dict(seconds=0.75, pitch_from=72.0, pitch_to=48.0, overtones=9, filter_floor=90.0,
                          filter_peak=520.0, filter_peak_at=0.22, sub_hz=42.0, sub=0.6, sub_decay=0.18,
                          decay=0.32)),
    "b": (chord_swell, dict(seconds=1.4, root_hz=130.81, steps=(0, 4, 7, 12), levels=(1.0, 0.6, 0.7, 0.45),
                            cents=9.0, rise_semitones=0.0, filter_from=300.0, filter_to=2600.0, attack=0.35,
                            hold=0.15, release=0.45, space=1.8, wet=0.45)),
    "c": (chord_swell, dict(seconds=1.4, root_hz=110.0, steps=(0, 7, 12, 16, 19), levels=(1.0, 0.7, 0.55, 0.35, 0.3),
                            cents=12.0, rise_semitones=2.0, filter_from=250.0, filter_to=3200.0, attack=0.4,
                            hold=0.1, release=0.5, space=2.2, wet=0.55, sparkle=0.25)),
    "d": (chord_swell, dict(seconds=1.2, root_hz=55.0, steps=(0, 7, 12, 16, 24), levels=(1.0, 0.6, 0.7, 0.3, 0.2),
                            cents=6.0, rise_semitones=0.0, filter_from=150.0, filter_to=1400.0, attack=0.25,
                            hold=0.1, release=0.4, space=1.5, wet=0.35)),
    # The operator found b and c awesome but too long: each again, quicker to swell and to ring off, cut to a
    # length, about 1.3 s for b2 and c2 and about 0.9 s for b3 and c3.
    "b2": (chord_swell, dict(seconds=0.9, root_hz=130.81, steps=(0, 4, 7, 12), levels=(1.0, 0.6, 0.7, 0.45),
                             cents=9.0, rise_semitones=0.0, filter_from=300.0, filter_to=2600.0, attack=0.25,
                             hold=0.1, release=0.3, space=1.0, wet=0.45, length=1.3)),
    "b3": (chord_swell, dict(seconds=0.7, root_hz=130.81, steps=(0, 4, 7, 12), levels=(1.0, 0.6, 0.7, 0.45),
                             cents=9.0, rise_semitones=0.0, filter_from=300.0, filter_to=2600.0, attack=0.2,
                             hold=0.05, release=0.22, space=0.7, wet=0.4, length=0.9)),
    "c2": (chord_swell, dict(seconds=0.9, root_hz=110.0, steps=(0, 7, 12, 16, 19),
                             levels=(1.0, 0.7, 0.55, 0.35, 0.3), cents=12.0, rise_semitones=2.0, filter_from=250.0,
                             filter_to=3200.0, attack=0.28, hold=0.08, release=0.32, space=1.1, wet=0.5,
                             sparkle=0.25, length=1.3)),
    "c3": (chord_swell, dict(seconds=0.7, root_hz=110.0, steps=(0, 7, 12, 16, 19),
                             levels=(1.0, 0.7, 0.55, 0.35, 0.3), cents=12.0, rise_semitones=2.0, filter_from=250.0,
                             filter_to=3200.0, attack=0.22, hold=0.05, release=0.24, space=0.8, wet=0.45,
                             sparkle=0.25, length=0.9)),
    "e": (bell_swell, dict(seconds=1.6, root_hz=392.0, ratios=(1.0, 2.0, 3.0, 4.16), levels=(1.0, 0.5, 0.3, 0.15),
                           ring=0.9, attack=0.12, air_low=4000.0, air_high=9000.0, air=0.12, space=2.4, wet=0.55)),
}

# Each conjuring take. a is the first; b and c lay a chord gliding up over the rising air, b a major chord climbing
# a fourth, c an open fifth climbing a fifth with less air, each over the charge's second with its space after.
CONJURE_TAKES = {
    "a": (airy_conjure, dict(seconds=1.0, air_from=600.0, air_to=4200.0, air=0.6, pitch_from=220.0, pitch_to=330.0,
                             shimmer_from=6.0, shimmer_to=18.0, shimmer=0.35, swell=0.7, release=0.06)),
    "b": (chord_conjure, dict(seconds=1.0, charge=0.9, root_hz=220.0, steps=(0, 4, 7, 12), levels=(1.0, 0.6, 0.7, 0.4),
                              rise_semitones=5.0, air_from=600.0, air_to=4200.0, air=0.45, shimmer=0.25, swell=0.8,
                              release=0.08, space=1.4, wet=0.4)),
    "c": (chord_conjure, dict(seconds=1.0, charge=0.9, root_hz=196.0, steps=(0, 7, 12, 19), levels=(1.0, 0.7, 0.5, 0.3),
                              rise_semitones=7.0, air_from=800.0, air_to=5000.0, air=0.25, shimmer=0.3, swell=0.9,
                              release=0.08, space=1.6, wet=0.45)),
    # The operator found b and c okay but pitching up too high: each again from a lower root, b2 and c2 climbing a
    # whole tone, b3 and c3 a semitone, the air's band rising less with them, each cut to about 1.4 s.
    "b2": (chord_conjure, dict(seconds=1.0, charge=0.9, root_hz=174.61, steps=(0, 4, 7, 12),
                               levels=(1.0, 0.6, 0.7, 0.4), rise_semitones=2.0, air_from=500.0, air_to=2600.0,
                               air=0.4, shimmer=0.25, swell=0.8, release=0.08, space=1.0, wet=0.4, length=1.4)),
    "b3": (chord_conjure, dict(seconds=1.0, charge=0.9, root_hz=174.61, steps=(0, 4, 7, 12),
                               levels=(1.0, 0.6, 0.7, 0.4), rise_semitones=1.0, air_from=500.0, air_to=2000.0,
                               air=0.4, shimmer=0.25, swell=0.8, release=0.08, space=1.0, wet=0.4, length=1.4)),
    "c2": (chord_conjure, dict(seconds=1.0, charge=0.9, root_hz=146.83, steps=(0, 7, 12, 19),
                               levels=(1.0, 0.7, 0.5, 0.3), rise_semitones=2.0, air_from=600.0, air_to=3000.0,
                               air=0.25, shimmer=0.3, swell=0.9, release=0.08, space=1.1, wet=0.45, length=1.4)),
    "c3": (chord_conjure, dict(seconds=1.0, charge=0.9, root_hz=146.83, steps=(0, 7, 12, 19),
                               levels=(1.0, 0.7, 0.5, 0.3), rise_semitones=1.0, air_from=600.0, air_to=2400.0,
                               air=0.25, shimmer=0.3, swell=0.9, release=0.08, space=1.1, wet=0.45, length=1.4)),
}

SOUND_TAKES = {"wub": WUB_TAKES, "conjure": CONJURE_TAKES}
# The takes each sound ships, each with the file it ships as. The operator picked wub b3 and c3 "quite easily" and
# asked for both in a pool, so sounds.json lists both under the one wub event and the game picks one each time; for
# the conjuring they picked c3, the open fifth from a low root climbing a semitone.
SHIPPED = {"wub": {"b3": "zone_wub.ogg", "c3": "zone_wub2.ogg"}, "conjure": {"c3": "zone_conjure.ogg"}}
# Decision hole-closes-with-the-outsider-wub-reversed-and-pitched-down: Void's hole closes on the wub played
# backwards. Minecraft plays a file forward only, so each shipped wub take also ships reversed, its samples in reverse
# order, as the file named here.
REVERSED = {"wub": {"b3": "zone_wub_reversed.ogg", "c3": "zone_wub2_reversed.ogg"}}


def reversed_take(signal):
    return signal[::-1].copy()


def write(path, signal):
    path.parent.mkdir(parents=True, exist_ok=True)
    soundfile.write(str(path), signal, SAMPLE_RATE, format="OGG", subtype="VORBIS")
    print(f"wrote {path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("sounds", nargs="*", help="the sounds to write, of " + ", ".join(SOUND_TAKES)
                        + "; every one if none")
    parser.add_argument("--takes", type=pathlib.Path, help="write every take to this folder to listen to")
    args = parser.parse_args()
    for sound, takes in SOUND_TAKES.items():
        if args.sounds and sound not in args.sounds:
            continue
        reversed_files = REVERSED.get(sound, {})
        if args.takes:
            for name, (synthesize, spec) in takes.items():
                signal = synthesize(spec)
                write(args.takes / f"zone_{sound}_{name}.ogg", signal)
                if name in reversed_files:
                    write(args.takes / f"zone_{sound}_{name}_reversed.ogg", reversed_take(signal))
        else:
            for name, file in SHIPPED[sound].items():
                synthesize, spec = takes[name]
                signal = synthesize(spec)
                write(SOUNDS / file, signal)
                if name in reversed_files:
                    write(SOUNDS / reversed_files[name], reversed_take(signal))


if __name__ == "__main__":
    main()
