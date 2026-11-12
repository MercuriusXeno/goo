#version 330

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <goo:void_stars.glsl>

// Voidstone (decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform):
// near-black stone holding a field of pale stars. Every star sits on a sixteenth-of-a-block texel picked by
// hashing that texel's world position and face, so a floor of any size reads as one random field; each star
// twinkles on its own clock over a faint nebula drifting through the dark.
// Rooms stand a million blocks out, past where a float can place a sixteenth of a block, so the texel is
// counted in integers: the camera's block times sixteen plus the camera-relative texel, exact anywhere.
//
// The field tiles at the room's width (decision voidstone-starfield-tiles-in-either-direction). A copy of the
// room shows the room's own field moved by whole room widths, so the room's east edge meets its west edge at
// every seam. The stars are random and hold their texel as the room grows, so the two sides of a seam meet as
// one random field. The nebula is noise sampled around a loop whose length is the room's width along each
// axis, a circle in four-dimensional noise, so it repeats exactly at the room's width at any width, a fraction
// of a block included, with its cells the same size however wide the room. VoidstoneRoom packs the room's
// center and extent into TextureMat; an extent of 0 means no room is known, and the nebula draws by world
// position unlooped.
//
// The block the boundary cuts draws the same field (decisions boundary-cut-voidstone-draws-without-its-stars and
// room-edge-draws-a-sparser-starfield). It reaches past the boundary plane, where the neighbouring copy's cut
// block lies too, so every face draws only between the room's planes and the two meet at the plane.

in vec3 voidLocal;
flat in vec3 voidNormal;

out vec4 fragColor;

const float TEXELS = 16.0;
const int BLOCK_TEXELS = 16;
const int HALF_BLOCK_TEXELS = 8;
const float TICKS_PER_CLOCK = 24000.0;
const int NEBULA_TEXELS = 40;
const float NEBULA_DRIFT = 0.004;
const vec3 STONE = vec3(0.010, 0.009, 0.016);
const vec3 NEBULA = vec3(0.030, 0.020, 0.055);
const int CENTER_SPLIT = 4096;
const float TURN = 6.28318530718;

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

float hash4(ivec4 point, int seed) {
    uint mixed = mixBits(uint(point.x) ^ mixBits(uint(point.y) ^ mixBits(uint(point.z)
            ^ mixBits(uint(point.w) ^ mixBits(uint(seed))))));
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

// Value noise in four dimensions: each of the cell's sixteen corners weighted by how near the point stands.
float valueNoise4(vec4 point, int seed) {
    vec4 cell = floor(point);
    vec4 into = point - cell;
    into = into * into * (3.0 - 2.0 * into);
    float total = 0.0;
    for (int corner = 0; corner < 16; corner++) {
        ivec4 offset = ivec4(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1, (corner >> 3) & 1);
        vec4 weight = mix(1.0 - into, into, vec4(offset));
        total += hash4(ivec4(cell) + offset, seed) * weight.x * weight.y * weight.z * weight.w;
    }
    return total;
}

int roomCenter(float high, float low) {
    return int(high) * CENTER_SPLIT + int(low);
}

// How far a place stands east or south of the room's middle, in texels.
float fromMiddle(int center, int texel, float within) {
    return float(texel - center * BLOCK_TEXELS - HALF_BLOCK_TEXELS) + within;
}

// A place on an axis that tiles, as a point on a circle in noise cells: once round the circle is one room
// width, so the place one width on lands on the same point.
vec2 aroundLoop(float texelsFromMiddle, float width) {
    float angle = TURN * texelsFromMiddle / width;
    return vec2(cos(angle), sin(angle)) * (width / TURN / float(NEBULA_TEXELS));
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

    float extent = TextureMat[1][0];
    float nebula;
    if (extent > 0.0) {
        // The axes across the face that run east or south tile: a top face's both, a side's horizontal one
        // alone, its vertical axis running straight through the noise.
        int centerX = roomCenter(TextureMat[0][0], TextureMat[0][1]);
        int centerZ = roomCenter(TextureMat[0][2], TextureMat[0][3]);
        float width = 2.0 * extent * TEXELS;
        vec2 toMiddle = vec2(fromMiddle(facing.x > 0.5 ? centerZ : centerX, across.x, within.x),
                fromMiddle(centerZ, across.y, within.y));
        if (abs(toMiddle.x) > width * 0.5 || (facing.y > 0.5 && abs(toMiddle.y) > width * 0.5)) {
            discard;
        }
        vec2 alongY = facing.y > 0.5 ? aroundLoop(toMiddle.y, width)
                : vec2((float(across.y) + within.y) / float(NEBULA_TEXELS), 0.0);
        vec4 drift = vec4(ticks * NEBULA_DRIFT, 0.0, ticks * NEBULA_DRIFT * 0.6, 0.0) / float(NEBULA_TEXELS);
        nebula = smoothstep(0.45, 1.0, valueNoise4(vec4(aroundLoop(toMiddle.x, width), alongY) + drift, seed + 11));
    } else {
        ivec2 drift = ivec2(int(ticks * NEBULA_DRIFT), int(ticks * NEBULA_DRIFT * 0.6));
        nebula = smoothstep(0.45, 1.0, valueNoise(across + drift, within, seed + 11));
    }
    vec3 color = STONE + NEBULA * nebula;

    float pick = hash(ivec3(across, seed));
    float rate = 0.03 + 0.09 * hash(ivec3(across, seed + 7));
    float bright = step(1.0 - BRIGHT_STARS, pick)
            * mix(BRIGHT_STAR_FLOOR, BRIGHT_STAR_PEAK, hash(ivec3(across, seed + 3)));
    float dim = step(1.0 - BRIGHT_STARS - DIM_STARS, pick) * (1.0 - step(1.0 - BRIGHT_STARS, pick)) * DIM_STAR_GLOW;
    color += STAR * (bright + dim) * twinkle(ticks, rate, pick * 61.0);

    fragColor = vec4(color, 1.0);
}
