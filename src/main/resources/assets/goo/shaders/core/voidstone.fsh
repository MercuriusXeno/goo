#version 330

#moj_import <minecraft:globals.glsl>
#moj_import <goo:void_stars.glsl>

// Voidstone (decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform):
// near-black stone holding a field of pale stars. Every star sits on a sixteenth-of-a-block texel picked by
// hashing that texel's world position and face, so a floor of any size reads as one random field with no
// tile repeating; each star twinkles on its own clock over a faint nebula drifting through the dark.
// Rooms stand a million blocks out, past where a float can place a sixteenth of a block, so the texel is
// counted in integers: the camera's block times sixteen plus the camera-relative texel, exact anywhere.

in vec3 voidLocal;
flat in vec3 voidNormal;

out vec4 fragColor;

const float TEXELS = 16.0;
const float TICKS_PER_CLOCK = 24000.0;
const int NEBULA_TEXELS = 40;
const float NEBULA_DRIFT = 0.004;
const vec3 STONE = vec3(0.010, 0.009, 0.016);
const vec3 NEBULA = vec3(0.030, 0.020, 0.055);

uint mixBits(uint value) {
    value ^= value >> 16;
    value *= 0x7feb352du;
    value ^= value >> 15;
    value *= 0x846ca68bu;
    value ^= value >> 16;
    return value;
}

float hash(ivec3 point) {
    uint mixed = mixBits(uint(point.x) ^ mixBits(uint(point.y) ^ mixBits(uint(point.z))));
    return float(mixed) / 4294967295.0;
}

int floorDiv(int value, int by) {
    return value >= 0 ? value / by : -((-value + by - 1) / by);
}

float valueNoise(ivec2 texel, vec2 within, int seed) {
    ivec2 cell = ivec2(floorDiv(texel.x, NEBULA_TEXELS), floorDiv(texel.y, NEBULA_TEXELS));
    vec2 into = (vec2(texel - cell * NEBULA_TEXELS) + within) / float(NEBULA_TEXELS);
    into = into * into * (3.0 - 2.0 * into);
    float a = hash(ivec3(cell, seed));
    float b = hash(ivec3(cell + ivec2(1, 0), seed));
    float c = hash(ivec3(cell + ivec2(0, 1), seed));
    float d = hash(ivec3(cell + ivec2(1, 1), seed));
    return mix(mix(a, b, into.x), mix(c, d, into.x), into.y);
}

void main() {
    vec3 facing = abs(voidNormal);
    ivec3 blockTexels = CameraBlockPos * 16;
    vec3 local = voidLocal * TEXELS;
    ivec2 across;
    int plane;
    int side;
    vec2 within;
    if (facing.y > 0.5) {
        across = blockTexels.xz + ivec2(floor(local.xz));
        within = fract(local.xz);
        plane = blockTexels.y + int(floor(local.y + 0.5));
        side = 0;
    } else if (facing.x > 0.5) {
        across = blockTexels.zy + ivec2(floor(local.zy));
        within = fract(local.zy);
        plane = blockTexels.x + int(floor(local.x + 0.5));
        side = 1;
    } else {
        across = blockTexels.xy + ivec2(floor(local.xy));
        within = fract(local.xy);
        plane = blockTexels.z + int(floor(local.z + 0.5));
        side = 2;
    }
    // The face's own plane and side seed its stars, so the top and the sides of one block differ.
    int seed = plane * 3 + side;
    float ticks = GameTime * TICKS_PER_CLOCK;

    ivec2 drift = ivec2(int(ticks * NEBULA_DRIFT), int(ticks * NEBULA_DRIFT * 0.6));
    float nebula = smoothstep(0.45, 1.0, valueNoise(across + drift, within, seed + 11));
    vec3 color = STONE + NEBULA * nebula;

    float pick = hash(ivec3(across, seed));
    float rate = 0.03 + 0.09 * hash(ivec3(across, seed + 7));
    float bright = step(1.0 - BRIGHT_STARS, pick)
            * mix(BRIGHT_STAR_FLOOR, BRIGHT_STAR_PEAK, hash(ivec3(across, seed + 3)));
    float dim = step(1.0 - BRIGHT_STARS - DIM_STARS, pick) * (1.0 - step(1.0 - BRIGHT_STARS, pick)) * DIM_STAR_GLOW;
    color += STAR * (bright + dim) * twinkle(ticks, rate, pick * 61.0);

    fragColor = vec4(color, 1.0);
}
