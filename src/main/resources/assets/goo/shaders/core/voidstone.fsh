#version 330

#moj_import <minecraft:globals.glsl>

// Voidstone (decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform):
// near-black stone holding a field of pale stars. Every star sits on a sixteenth-of-a-block texel picked by
// hashing that texel's world position and face, so a floor of any size reads as one random field with no
// tile repeating; each star twinkles on its own clock over a faint nebula drifting through the dark.

in vec3 voidWorldPos;
flat in vec3 voidNormal;

out vec4 fragColor;

const float TEXELS = 16.0;
const float TICKS_PER_CLOCK = 24000.0;
const float BRIGHT_STARS = 0.012;
const float DIM_STARS = 0.035;
const float NEBULA_TEXELS = 40.0;
const float NEBULA_DRIFT = 0.004;
const vec3 STONE = vec3(0.010, 0.009, 0.016);
const vec3 NEBULA = vec3(0.030, 0.020, 0.055);
const vec3 STAR = vec3(0.87, 0.86, 0.96);

float hash(vec3 point) {
    point = fract(point * vec3(443.897, 441.423, 437.195));
    point += dot(point, point.yzx + 19.19);
    return fract((point.x + point.y) * point.z);
}

float valueNoise(vec2 point, float seed) {
    vec2 cell = floor(point);
    vec2 into = fract(point);
    into = into * into * (3.0 - 2.0 * into);
    float a = hash(vec3(cell, seed));
    float b = hash(vec3(cell + vec2(1.0, 0.0), seed));
    float c = hash(vec3(cell + vec2(0.0, 1.0), seed));
    float d = hash(vec3(cell + vec2(1.0, 1.0), seed));
    return mix(mix(a, b, into.x), mix(c, d, into.x), into.y);
}

void main() {
    vec3 facing = abs(voidNormal);
    vec2 across;
    float depth;
    float side;
    if (facing.y > 0.5) {
        across = voidWorldPos.xz;
        depth = voidWorldPos.y;
        side = 0.0;
    } else if (facing.x > 0.5) {
        across = voidWorldPos.zy;
        depth = voidWorldPos.x;
        side = 1.0;
    } else {
        across = voidWorldPos.xy;
        depth = voidWorldPos.z;
        side = 2.0;
    }
    // The face's own plane and side seed its stars, so the top and the sides of one block differ.
    float plane = floor(depth * TEXELS + 0.5) + side * 4099.0;
    vec2 texel = floor(across * TEXELS);
    float ticks = GameTime * TICKS_PER_CLOCK;

    vec2 drift = vec2(ticks * NEBULA_DRIFT, ticks * NEBULA_DRIFT * 0.6);
    float nebula = valueNoise(texel / NEBULA_TEXELS + drift / NEBULA_TEXELS, plane + 11.0);
    nebula = smoothstep(0.45, 1.0, nebula);
    vec3 color = STONE + NEBULA * nebula;

    float pick = hash(vec3(texel, plane));
    float rate = 0.03 + 0.09 * hash(vec3(texel, plane + 7.0));
    float twinkle = 0.55 + 0.45 * sin(ticks * rate + pick * 61.0);
    float bright = step(1.0 - BRIGHT_STARS, pick) * (0.7 + 0.3 * hash(vec3(texel, plane + 3.0)));
    float dim = step(1.0 - BRIGHT_STARS - DIM_STARS, pick) * (1.0 - step(1.0 - BRIGHT_STARS, pick)) * 0.28;
    color += STAR * (bright + dim) * twinkle;

    fragColor = vec4(color, 1.0);
}
