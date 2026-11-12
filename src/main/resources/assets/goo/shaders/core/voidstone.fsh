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
// The field wraps at the room's width (decision voidstone-starfield-tiles-in-either-direction). A copy of the
// room beyond its east plane shows the field from the room's west plane on, so within a band inside the east
// plane the field fades into the field one room width back, and at the plane it shows the field at the west
// plane exactly; the south plane fades to the north the same way. The copies then meet the room with no seam
// in the nebula or the star lattice, and away from the east and south bands a star holds its texel as the
// room grows. VoidstoneRoom packs the room's center and extent into TextureMat; an extent of 0 means no room
// is known, and the field draws unwrapped.
//
// The block the boundary cuts draws a sparser starfield (decisions boundary-cut-voidstone-draws-without-its-stars
// and room-edge-draws-a-sparser-starfield): its faces come black, and fewer of its texels hold a star. The cut
// block reaches past the boundary plane, where the neighbouring copy's cut block lies too, so every face draws
// only between the room's planes and the two meet at the plane as one field.

in vec3 voidLocal;
flat in vec3 voidNormal;
flat in float voidSparse;

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
// The band the field fades across, one nebula cell, held to half the room's width so the far plane stays clear.
const float WRAP_BAND = 40.0;

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

// The field at one texel: the nebula's glow there and its star's brightness on the clock. The star share
// keeps that share of the voidstone's stars, bright and dim alike, 1 for the full field.
vec2 fieldAt(ivec2 texel, vec2 within, int seed, float ticks, float starShare) {
    ivec2 drift = ivec2(int(ticks * NEBULA_DRIFT), int(ticks * NEBULA_DRIFT * 0.6));
    float glow = smoothstep(0.45, 1.0, valueNoise(texel + drift, within, seed + 11));
    float pick = hash(ivec3(texel, seed));
    float rate = 0.03 + 0.09 * hash(ivec3(texel, seed + 7));
    float brightShare = BRIGHT_STARS * starShare;
    float starredShare = (BRIGHT_STARS + DIM_STARS) * starShare;
    float bright = step(1.0 - brightShare, pick)
            * mix(BRIGHT_STAR_FLOOR, BRIGHT_STAR_PEAK, hash(ivec3(texel, seed + 3)));
    float dim = step(1.0 - starredShare, pick) * (1.0 - step(1.0 - brightShare, pick)) * DIM_STAR_GLOW;
    return vec2(glow, (bright + dim) * twinkle(ticks, rate, pick * 61.0));
}

int roomCenter(float high, float low) {
    return int(high) * CENTER_SPLIT + int(low);
}

// How far a place stands inside the room's far plane along one axis, east or south, in texels.
float toFarPlane(int center, float extent, int texel, float within) {
    return float(center * BLOCK_TEXELS + HALF_BLOCK_TEXELS - texel) + extent * TEXELS - within;
}

// A place one room width back along one axis: the width's whole texels and its part counted apart, so the
// place stays exact however far out the room stands.
void widthBack(int texel, float within, float width, out int texelBack, out float withinBack) {
    float moved = within - fract(width);
    int carry = int(floor(moved));
    texelBack = texel - int(floor(width)) + carry;
    withinBack = moved - float(carry);
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

    // The axes across the face that run east or south wrap and clip: a top face's both, a side's horizontal one
    // alone, its vertical axis standing mid-room. The weight is how far the field has faded into the field one
    // room width back: none a band or more inside the far plane, wholly at the plane.
    float extent = TextureMat[1][0];
    float width = 2.0 * extent * TEXELS;
    vec2 weight = vec2(0.0);
    if (extent > 0.0) {
        int centerX = roomCenter(TextureMat[0][0], TextureMat[0][1]);
        int centerZ = roomCenter(TextureMat[0][2], TextureMat[0][3]);
        vec2 toFar = vec2(extent * TEXELS);
        if (facing.y > 0.5) {
            toFar = vec2(toFarPlane(centerX, extent, across.x, within.x),
                    toFarPlane(centerZ, extent, across.y, within.y));
        } else {
            toFar.x = toFarPlane(facing.x > 0.5 ? centerZ : centerX, extent, across.x, within.x);
        }
        if (any(lessThan(toFar, vec2(0.0))) || any(greaterThan(toFar, vec2(width)))) {
            discard;
        }
        float band = min(WRAP_BAND, extent * TEXELS);
        weight = vec2(1.0) - smoothstep(vec2(0.0), vec2(band), toFar);
    }
    float starShare = voidSparse > 0.5 ? EDGE_STARS / (BRIGHT_STARS + DIM_STARS) : 1.0;
    ivec2 back;
    vec2 withinBack;
    widthBack(across.x, within.x, width, back.x, withinBack.x);
    widthBack(across.y, within.y, width, back.y, withinBack.y);
    vec2 here = fieldAt(across, within, seed, ticks, starShare);
    vec2 behindX = fieldAt(ivec2(back.x, across.y), vec2(withinBack.x, within.y), seed, ticks, starShare);
    vec2 behindY = fieldAt(ivec2(across.x, back.y), vec2(within.x, withinBack.y), seed, ticks, starShare);
    vec2 behindBoth = fieldAt(back, withinBack, seed, ticks, starShare);
    vec2 field = mix(mix(here, behindX, weight.x), mix(behindY, behindBoth, weight.x), weight.y);

    vec3 color = STONE + NEBULA * field.x + STAR * field.y;
    fragColor = vec4(color, 1.0);
}
