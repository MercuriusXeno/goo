#version 330

#moj_import <minecraft:globals.glsl>
#moj_import "portal_texels.glsl"

// A gate's tornadic halo (decision tornadic-halo-spirals-out-from-the-rim): the rim drawn as a short
// tube standing at most 2/16 of a block off the wall, like a barrel's mouth, its inner wall rifled by
// bright grooves that whorl around the portal several times before they reach the wall, turning
// slowly the screw way. It draws smooth and blended, reading the face coordinate unsnapped, off the
// texel grid the rim and surface glow keep. frameCoord runs zero to one around the rim, then zero to
// one up the tube from the face.
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
// How many grooves rifle the tube, each starting at its own place round the lip.
const float TORNADO_GROOVES = 2.0;
// How many times a groove whorls around the portal from the lip to the wall: the rifling's twist,
// tuned by eye.
const float TORNADO_TWIST = 3.0;
// How many seconds the rifling takes to turn once around, tuned by eye.
const float TORNADO_TURN_SECONDS = 8.0;
// How sharply a groove stands out from the wall between, higher thinner.
const float TORNADO_GROOVE_SHARPNESS = 2.5;
// How strongly the tube's wall glows between grooves, and how much more along a groove.
const float TUBE_GLOW = 0.08;
const float TUBE_GROOVE_GLOW = 0.9;

void main() {
    float around = frameCoord.x * TAU;
    float up = frameCoord.y;
    float spread = mix(mix(1.0, 1.0 - HOLE_TEAR_DEPTH, frameColor.a), 1.0, up);
    if (frameColor.a > 0.5 && spread < holeTornEdge(around, GameTime)) {
        discard;
    }
    float seconds = GameTime * SECONDS_PER_DAY;
    float turned = around - seconds * TAU / TORNADO_TURN_SECONDS;
    float rifling = TORNADO_GROOVES * (turned + up * TORNADO_TWIST * TAU);
    float groove = pow(0.5 + 0.5 * sin(rifling), TORNADO_GROOVE_SHARPNESS);
    fragColor = vec4(frameColor.rgb, TUBE_GLOW + TUBE_GROOVE_GLOW * groove);
}
