#version 330

#moj_import <minecraft:globals.glsl>
#moj_import "portal_texels.glsl"

// A gate's tornadic halo (decision tornadic-halo-spirals-out-from-the-rim): the rim drawn as a short
// tube standing at most 2/16 of a block off the wall, like a barrel's mouth, its inner wall glowing
// with tight spiral grooves that turn slowly the screw way, and its front lip bristling with fine
// slanted hairs. It draws smooth and blended, reading the face coordinate unsnapped, off the texel
// grid the rim and surface glow keep. frameCoord runs zero to one around the rim, then zero to
// TUBE_SHARE up the tube and on to one out across the lip.
// A hole's mouth wears the same tornado (decision hole-mouths-wear-the-tornadic-halo): its vertex
// alpha marks the torn edge, its tube leans out from the deepest the tear bites, and every part of it
// inside the tear holeTornEdge draws is cut, so the tornado starts at the tear.

in vec2 frameCoord;
flat in vec2 texels;
in vec4 frameColor;

out vec4 fragColor;

// GameTime runs from zero to one over a day of 24000 ticks; this many seconds make one day. Each
// period below divides it whole, so the tornado carries on unbroken as the day wraps.
const float SECONDS_PER_DAY = 1200.0;
const float TAU = 6.28318530718;
// The share of the way along the tube and its lip where the tube ends: PortalFaces.TUBE_SHARE.
const float TUBE_SHARE = 0.5;
// How many grooves rifle the tube's inner wall.
const float TORNADO_GROOVES = 24.0;
// How many turns a groove winds over the tube's depth: the rifling's twist, tuned by eye.
const float TORNADO_TWIST = 0.25;
// How many seconds the rifling and the hairs take to turn once around, tuned by eye.
const float TORNADO_TURN_SECONDS = 8.0;
// How sharply a groove stands out from the wall between, higher thinner.
const float TORNADO_GROOVE_SHARPNESS = 4.0;
// How strongly the tube's wall glows between grooves, and how much more along a groove.
const float TUBE_GLOW = 0.3;
const float TUBE_GROOVE_GLOW = 0.55;
// How many fine hairs bristle off the lip around the rim.
const float TORNADO_HAIRS = 96.0;
// How far around a hair slants as it runs out to its tip, in hairs, the screw way.
const float TORNADO_HAIR_SLANT = 0.8;
// A hair's half width, as a share of the spacing between hairs.
const float TORNADO_HAIR_WIDTH = 0.12;
// How strongly a hair glows at its root.
const float TORNADO_HAIR_STRENGTH = 0.6;
// The shortest a hair runs, as a share of the lip's reach; the rest vary up to the whole reach.
const float TORNADO_HAIR_SHORTEST = 0.3;
// How strongly a soft mist glows under the hairs, fading out across the lip.
const float TORNADO_MIST = 0.15;

// A value nothing to one fixed for each whole number, the same at every frame.
float hash(float n) {
    return fract(sin(n * 12.9898) * 43758.5453);
}

float tube(float around, float inner, float seconds) {
    float spread = mix(mix(1.0, 1.0 - HOLE_TEAR_DEPTH, frameColor.a), 1.0, inner);
    if (frameColor.a > 0.5 && spread < holeTornEdge(around, GameTime)) {
        discard;
    }
    float turned = around - seconds * TAU / TORNADO_TURN_SECONDS;
    float rifling = turned * TORNADO_GROOVES + inner * TORNADO_TWIST * TAU;
    float groove = pow(0.5 + 0.5 * sin(rifling), TORNADO_GROOVE_SHARPNESS);
    return (TUBE_GLOW + TUBE_GROOVE_GLOW * groove) * mix(0.7, 1.0, inner);
}

float hairs(float around, float out_, float seconds) {
    float turned = around - seconds * TAU / TORNADO_TURN_SECONDS;
    float strand = turned / TAU * TORNADO_HAIRS + out_ * TORNADO_HAIR_SLANT;
    float off = abs(fract(strand) - 0.5);
    float line = 1.0 - smoothstep(TORNADO_HAIR_WIDTH, TORNADO_HAIR_WIDTH + fwidth(strand), off);
    float reach = mix(TORNADO_HAIR_SHORTEST, 1.0, hash(mod(floor(strand), TORNADO_HAIRS)));
    float hair = TORNADO_HAIR_STRENGTH * line * (1.0 - smoothstep(reach * 0.5, reach, out_));
    return hair + TORNADO_MIST * (1.0 - out_) * (1.0 - out_);
}

void main() {
    float around = frameCoord.x * TAU;
    float seconds = GameTime * SECONDS_PER_DAY;
    float glow = frameCoord.y < TUBE_SHARE
            ? tube(around, frameCoord.y / TUBE_SHARE, seconds)
            : hairs(around, (frameCoord.y - TUBE_SHARE) / (1.0 - TUBE_SHARE), seconds);
    fragColor = vec4(frameColor.rgb, glow);
}
