#version 330

#moj_import <minecraft:globals.glsl>

// Petrify's fog (decisions petrify-fog-is-billowing-roiling-dust,
// petrify-stone-encasement-and-calcify-map): one puff of a medusa's gaze
// miasma. Billowing grey-green dust churns inside it, turning the way its seed
// sets and folding over itself, and its outline is the dust's own ragged edge,
// changing as the dust churns. It thickens as it leaves the glove and thins
// away at the reach. TRANSLUCENT blend, the puffs pouring over each other.

in float age;
in vec2 corner;
flat in float seed;

out vec4 fragColor;

const float TAU = 6.2831853;
// GameTime runs from zero to one over a day of 24000 ticks; this many seconds make one day.
const float SECONDS_PER_DAY = 1200.0;
const vec3 MIASMA_COLOR = vec3(0.47, 0.52, 0.43);
const vec3 DUST_COLOR = vec3(0.76, 0.72, 0.62);
const float DUST_SCALE = 2.4;
// How fast a puff's dust turns, in turns a second, and how fast it folds over itself. Tuned by eye.
const float CHURN_TURNS_PER_SECOND = 0.25;
const float FOLD_PER_SECOND = 0.9;
// How far the folding pushes the dust about inside the puff, as a share of its radius.
const float FOLD_REACH = 0.45;
// How ragged the outline runs: how far the dust's own density pushes the edge in or out.
const float RAGGED = 0.7;
// Each puff's own opacity; the puffs pouring over each other build the fog.
const float PUFF_OPACITY = 0.24;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float billow(vec2 p) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int octave = 0; octave < 4; octave++) {
        sum += amplitude * abs(noise(p) * 2.0 - 1.0);
        p *= 2.0;
        amplitude *= 0.5;
    }
    return sum;
}

vec2 turned(vec2 p, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

void main() {
    float seconds = mod(GameTime * SECONDS_PER_DAY, 600.0);
    float spin = seed < 0.5 ? -1.0 : 1.0;
    vec2 p = turned(corner, seed * TAU + spin * seconds * CHURN_TURNS_PER_SECOND * TAU);
    vec2 offset = vec2(seed * 37.0, seed * 91.0);
    float t = seconds * FOLD_PER_SECOND;
    vec2 fold = vec2(noise(p * 1.7 + offset + vec2(t, 0.0)), noise(p * 1.7 + offset + vec2(0.0, -t) + 5.2)) - 0.5;
    float dust = billow((p + fold * FOLD_REACH * 2.0) * DUST_SCALE + offset);

    float radial = length(corner) + (0.5 - dust) * RAGGED;
    float body = 1.0 - smoothstep(0.35, 0.95, radial);
    float life = smoothstep(0.0, 0.12, age) * (1.0 - smoothstep(0.6, 1.0, age));
    float alpha = body * (0.35 + 0.65 * dust) * life * PUFF_OPACITY;
    vec3 color = mix(MIASMA_COLOR, DUST_COLOR, smoothstep(0.25, 0.75, dust)) * mix(0.8, 1.05, dust);

    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
