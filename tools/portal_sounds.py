"""Synthesizes goo's three portal sounds, Portal-game style, each mono.

Decision portal-placement-plays-a-zap-that-opens-into-a-hum: placing a portal
plays a short energetic zap that opens into a brief hum, like a portal gun
shot landing. The zap is a bright chirp falling onto the hum's pitch, buzzing
with frequency modulation, over a swirl of noise whose pitch falls with it;
the hum it lands on swells as the zap fades, then dies away.

Decision stepping-through-plays-a-soft-whoosh-at-both-ends: stepping through
plays a soft vorp like the Portal games' passage sound. The operator heard a
noise whoosh as wind swelling in too late, and handed two of the game's own
passage sounds as the reference; none of their audio is in the mod. The vorp
is built on the reference's measured parts: a wobbly low throb swelling some
thirty times a second, a click, a bright swoosh rising into a snap, a short
gap, then bursts of buzzy noise sputtering away, each quieter than the last.
The operator kept the vorp softened twice over, take o: the bursts, the click
and the snap darkened and gentled, the whole rounded off above 4 kHz.

Decision standing-portals-loop-a-soft-ambient-hum: a standing portal loops a
soft ambient hum. The hum is a low tone and its overtones, warbling slowly,
under a breath of noise; every part repeats a whole number of times over the
loop, so its end runs into its start with no seam.

The operator kept take a of the placement zap and of the hum, and take o of
the vorp, so the mod ships one file of each.

Run from the repository root:
    python tools/portal_sounds.py                 # writes every shipped take
    python tools/portal_sounds.py cross           # writes the named sounds' shipped takes alone
    python tools/portal_sounds.py --takes DIR     # writes every take to DIR to listen to
Needs numpy, scipy and soundfile (libsndfile with Ogg Vorbis).
"""

import argparse
import pathlib

import numpy as np
import soundfile
from scipy import signal as sg

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

# Each stepping-through take, a vorp: its length; the throb's pitch, how far its period wanders, how fast it
# swells, its level, its grit and what of it lingers under the tail; the click's time, level and top pitch;
# the swoosh's length, the pitches it rises between and its level; the snap's time, level and top pitch; the
# gap's end; the bursts' length, buzz pitch and strength, rate, decay, level, top pitch and how long each
# takes to fade in; and the pitch the whole is rounded off above. Take k matches the reference; n and o
# soften it, the operator finding k scratchy.
CROSS_K = dict(seconds=0.75, throb_hz=65.0, wander=0.25, swell_hz=30.0, throb=0.7, grit=0.08, low_tail=0.3,
               click_at=0.045, click=0.25, click_top=10000.0, swoosh=0.035, swoosh_from=700.0, swoosh_to=1600.0,
               swoosh_level=0.8, snap_at=0.145, snap=0.35, snap_top=0.0, gap_to=0.18, burst=0.045,
               buzz_hz=180.0, comb=0.6, sputter_hz=14.0, sputter_decay=0.68, tail_level=1.1, burst_top=6000.0,
               burst_attack=0.002, soft_top=0.0)
CROSS_TAKES = {
    "k": CROSS_K,
    "n": dict(CROSS_K, burst_top=2500.0, click_top=3000.0, comb=0.2, burst_attack=0.012, grit=0.0,
              snap_top=2500.0, soft_top=5000.0),
    "o": dict(CROSS_K, burst_top=2000.0, click_top=2500.0, comb=0.12, burst_attack=0.016, grit=0.0,
              snap_top=2000.0, soft_top=4000.0),
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
    "cross": {"o": "portal_cross.ogg"},
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


def filtered(signal, kind, hz, order=4):
    """A Butterworth filter of a kind, a lowpass, highpass or bandpass, at a pitch or a pair of pitches."""
    return sg.sosfilt(sg.butter(order, hz, kind, fs=SAMPLE_RATE, output="sos"), signal)


def wandering(rng, samples, hz):
    """Noise wandering at about a rate, scaled to plus or minus one."""
    noise = filtered(rng.standard_normal(samples), "lowpass", hz, 2)
    return noise / np.max(np.abs(noise))


def held_between(t, start, end, attack, release):
    """One between two times, ramped in over the attack and out over the release."""
    return np.minimum(np.clip((t - start) / attack, 0, 1), np.clip((end - t) / release, 0, 1))


def combed(signal, hz, feedback):
    """A feedback comb: rings at a pitch and its multiples, which reads as a buzz."""
    delay = int(round(SAMPLE_RATE / hz))
    feedback_taps = np.zeros(delay + 1)
    feedback_taps[0], feedback_taps[delay] = 1.0, -feedback
    return sg.lfilter([1.0], feedback_taps, signal)


def throb(t, rng, spec):
    """A low tone, its period wandering cycle to cycle and grit over it, swelling at the throb rate, and
    lingering low under the tail once the snap passes."""
    pitch_hz = spec["throb_hz"] * (1 + spec["wander"] * wandering(rng, t.size, 40.0))
    phase = phase_of(pitch_hz)
    tone = sum(np.sin(k * phase) / k ** 2.2 for k in range(1, 10))
    tone = tone / np.max(np.abs(tone))
    grit = filtered(rng.standard_normal(t.size), "bandpass", [300.0, 4000.0], 2)
    tone = tone + spec["grit"] * grit / np.max(np.abs(grit))
    swell = 0.55 + 0.45 * np.abs(np.sin(np.pi * spec["swell_hz"] * t + 0.6))
    opening = np.clip(0.3 + t / 0.03, 0, 1)
    after = np.maximum(t - spec["snap_at"], 0)
    lingering = np.where(t < spec["snap_at"], 1.0, spec["low_tail"] * np.exp(-after / 0.2))
    return tone * swell * opening * lingering


def buzz_burst(t, rng, start, length, low_hz, high_hz, buzz_hz, spec):
    """A short burst of noise between two pitches buzzing at a pitch, fading in over the take's attack and
    out over half its length."""
    noise = filtered(rng.standard_normal(t.size), "bandpass", [low_hz, high_hz], 2)
    noise = combed(noise, buzz_hz, spec["comb"])
    shape = held_between(t, start, start + length, spec["burst_attack"], length * 0.5)
    return noise / np.max(np.abs(noise)) * shape


def swoosh(t, rng, spec):
    """Noise through a band rising between two pitches, swelling into the snap and cut just after it."""
    start, end = spec["snap_at"] - spec["swoosh"], spec["snap_at"] + 0.012
    place = np.clip((t - start) / (end - start), 0, 1)
    centre_hz = spec["swoosh_from"] * (spec["swoosh_to"] / spec["swoosh_from"]) ** place
    noise = rng.standard_normal(t.size)
    out = np.zeros(t.size)
    # The band moves block by block; the rise is slow beside a block, so the blocks join smoothly.
    block = 256
    state = None
    for i in range(0, t.size, block):
        sos = sg.butter(2, [centre_hz[i] / 1.5, min(centre_hz[i] * 1.5, SAMPLE_RATE / 2 - 100)], "bandpass",
                        fs=SAMPLE_RATE, output="sos")
        if state is None:
            state = np.zeros((sos.shape[0], 2))
        out[i:i + block], state = sg.sosfilt(sos, noise[i:i + block], zi=state)
    bright = filtered(noise, "bandpass", [2500.0, 8000.0], 2) * 0.35
    shape = place ** 2 * held_between(t, start, end, 0.01, 0.003)
    return (out / np.max(np.abs(out)) + bright) * shape


def snap(t, rng, spec):
    """A hard click as the swoosh peaks, rounded off above the take's top pitch."""
    at = spec["snap_at"] - 0.008
    click = rng.standard_normal(t.size) * np.exp(-np.maximum(t - at, 0) / 0.004) * (t >= at)
    click = filtered(click, "highpass", 300.0, 2)
    return filtered(click, "lowpass", spec["snap_top"], 2) if spec["snap_top"] else click


def sputter(t, rng, spec):
    """Bursts at a steady rate, a little uneven, each quieter than the last."""
    out = np.zeros(t.size)
    at, level = spec["gap_to"], 1.0
    while at < t[-1] - 0.03:
        out += level * buzz_burst(t, rng, at, spec["burst"], 300.0, spec["burst_top"], spec["buzz_hz"], spec)
        at += 1.0 / spec["sputter_hz"] * (1 + 0.15 * rng.standard_normal())
        level *= spec["sputter_decay"]
    return out


def cross_take(name):
    """The stepping-through vorp: the throb, the click, the swoosh into the snap, then the sputter."""
    spec = CROSS_TAKES[name]
    t = times(spec["seconds"])
    rng = np.random.default_rng(SEED)
    out = spec["throb"] * throb(t, rng, spec)
    out += spec["click"] * buzz_burst(t, rng, spec["click_at"], 0.012, 400.0, spec["click_top"], 300.0, spec)
    out += spec["swoosh_level"] * swoosh(t, rng, spec)
    out += spec["snap"] * snap(t, rng, spec)
    out += spec["tail_level"] * sputter(t, rng, spec)
    if spec["soft_top"]:
        out = filtered(out, "lowpass", spec["soft_top"], 2)
    edge = int(SAMPLE_RATE * EDGE_SECONDS)
    out[-edge:] *= np.linspace(1, 0, edge)
    return normalized(out)


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
    parser.add_argument("sounds", nargs="*", help="the sounds to write, of " + ", ".join(SOUND_TAKES)
                        + "; every one if none")
    parser.add_argument("--takes", type=pathlib.Path, help="write every take to this folder to listen to")
    args = parser.parse_args()
    for sound, (takes, synthesize) in SOUND_TAKES.items():
        if args.sounds and sound not in args.sounds:
            continue
        if args.takes:
            for name in takes:
                write(args.takes / f"portal_{sound}_{name}.ogg", synthesize(name))
        else:
            for name, file in SHIPPED[sound].items():
                write(SOUNDS / file, synthesize(name))


if __name__ == "__main__":
    main()
