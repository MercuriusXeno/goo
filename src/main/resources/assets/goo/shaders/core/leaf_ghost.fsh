#version 330

#moj_import <minecraft:globals.glsl>

// Leaf's held ghost, Bloom's sphere (decision bloom-places-buds-by-biome-and-surface):
// a soft glowing green-gold haze filling the sphere Bloom covers, a green fog
// swirling slowly round the world's up through it, and pollen motes drifting
// up through the haze, brighter low down. A whole sphere, whichever face the
// throw strikes. LIGHTNING blend, so the haze glows.

in vec3 surfaceDir;
in float opacity;

out vec4 fragColor;

// GameTime is the fraction of a 24000-tick day.
const float TICKS_PER_DAY = 24000.0;
const vec3 HAZE_GREEN = vec3(0.35, 0.80, 0.30);
const vec3 POLLEN_GOLD = vec3(1.00, 0.92, 0.45);
const vec3 SWIRL_GREEN = vec3(0.45, 1.00, 0.40);
// How fast the fog swirls round the normal, in radians a tick, and how much it twists with height.
const float SWIRL_RATE = 0.012;
const float SWIRL_TWIST = 2.5;
const float FOG_SCALE = 2.6;
// Pollen cells over the sphere, how fast the motes rise, in cells a tick, and how big each mote is.
const float POLLEN_DENSITY = 9.0;
const float POLLEN_RISE = 0.02;
const float MOTE_RADIUS = 0.11;
const float POLLEN_SHARE = 0.35;
const vec3 WORLD_UP = vec3(0.0, 1.0, 0.0);

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float near = mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x),
                     mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y);
    float far = mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
                    mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y);
    return mix(near, far, f.z);
}

float fbm(vec3 p) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int octave = 0; octave < 4; octave++) {
        sum += amplitude * noise(p);
        p = p * 2.02 + vec3(3.1, -1.7, 2.3);
        amplitude *= 0.5;
    }
    return sum;
}

// Turns a direction round an axis.
vec3 turned(vec3 v, vec3 axis, float angle) {
    return v * cos(angle) + cross(axis, v) * sin(angle) + axis * dot(axis, v) * (1.0 - cos(angle));
}

void main() {
    vec3 dir = normalize(surfaceDir);
    // From the sphere's bottom, 0, to its top, 1.
    float height = dir.y * 0.5 + 0.5;
    float ticks = GameTime * TICKS_PER_DAY;

    // The fog swirls round the world's up, twisting with height.
    vec3 swirled = turned(dir, WORLD_UP, ticks * SWIRL_RATE + height * SWIRL_TWIST);
    float fog = fbm(swirled * FOG_SCALE + vec3(0.0, ticks * 0.004, 0.0));
    float wisps = smoothstep(0.42, 0.72, fog);

    // Pollen rides upward through jittered cells; each mote a soft round speck.
    vec3 rising = dir * POLLEN_DENSITY - WORLD_UP * ticks * POLLEN_RISE;
    vec3 cell = floor(rising);
    float pollen = 0.0;
    for (int x = 0; x <= 1; x++) {
        for (int y = 0; y <= 1; y++) {
            for (int z = 0; z <= 1; z++) {
                vec3 here = cell + vec3(x, y, z);
                if (hash(here) > POLLEN_SHARE) {
                    continue;
                }
                vec3 mote = here + vec3(hash(here + 3.1), hash(here + 7.7), hash(here + 1.3));
                float twinkle = 0.6 + 0.4 * sin(ticks * 0.15 + hash(here + 9.9) * 40.0);
                pollen = max(pollen, (1.0 - smoothstep(0.0, MOTE_RADIUS, length(rising - mote))) * twinkle);
            }
        }
    }

    float ground = 1.0 - smoothstep(0.0, 0.7, height);
    float haze = 0.12 + 0.18 * ground;
    vec3 color = HAZE_GREEN * haze + SWIRL_GREEN * wisps * 0.35 + POLLEN_GOLD * pollen * (0.6 + 0.6 * ground);
    float alpha = haze + wisps * 0.3 + pollen * 0.8;
    fragColor = vec4(color / max(alpha, 0.001), clamp(alpha, 0.0, 1.0) * opacity);
}
