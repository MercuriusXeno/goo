#version 330

#moj_import <minecraft:globals.glsl>
#moj_import "portal_texels.glsl"

// A gate's tornadic halo (decision tornadic-halo-spirals-out-from-the-rim): a funnel standing off
// the wall in front of the portal, beside its surface glow. Translucent bands wind around the
// funnel's axis and travel outward along it with time, so they read as rifling out of the portal,
// fading to nothing at the funnel's far end. frameCoord runs zero to one around the rim, then zero
// to one up the funnel from the face; every term reads the snapped texel center, sixteen texels a
// block like the rim. The funnel's height and flare are PortalFaces' TORNADO_HEIGHT and
// TORNADO_FLARE.
// A hole's mouth wears the same tornado (decision hole-mouths-wear-the-tornadic-halo): its vertex
// alpha marks the torn edge, its funnel starts at the deepest the tear bites, and every part of it
// lying inside the tear holeTornEdge draws is cut, so the tornado starts at the tear.

in vec2 frameCoord;
flat in vec2 texels;
in vec4 frameColor;

out vec4 fragColor;

// GameTime runs from zero to one over a day of 24000 ticks; this many seconds make one day. Each
// period below divides it whole, so the bands carry on unbroken as the day wraps.
const float SECONDS_PER_DAY = 1200.0;
const float TAU = 6.28318530718;
// How many bands wind around the funnel.
const float TORNADO_BANDS = 3.0;
// How many turns a band winds around the funnel from the face to its far end: the rifling's twist,
// tuned by eye.
const float TORNADO_TWIST = 1.25;
// How many seconds a band takes to travel from the rim to the funnel's far end, tuned by eye.
const float TORNADO_RIFLE_SECONDS = 2.0;
// How sharply a band stands out from the gaps between, higher thinner.
const float TORNADO_BAND_SHARPNESS = 3.0;
// How strongly the bands glow at the rim.
const float TORNADO_STRENGTH = 0.55;
// How much a finer turbulence frays the bands, as a share of their strength.
const float TORNADO_FRAY = 0.35;
// How many finer wisps the turbulence breaks each band into around the funnel.
const float TORNADO_WISPS = 11.0;
// How much wider than its first ring the funnel flares at its far end: PortalFaces.TORNADO_FLARE.
const float TORNADO_FLARE = 0.6;

void main() {
    vec2 at = portalTexelCenter(frameCoord, texels);
    float around = at.x * TAU;
    float along = at.y;
    float spread = mix(1.0, 1.0 - HOLE_TEAR_DEPTH, frameColor.a) * (1.0 + TORNADO_FLARE * along * along);
    if (frameColor.a > 0.5 && spread < holeTornEdge(around, GameTime)) {
        discard;
    }
    float seconds = GameTime * SECONDS_PER_DAY;
    float travel = along - seconds / TORNADO_RIFLE_SECONDS;
    float rifling = around * TORNADO_BANDS + travel * TORNADO_TWIST * TAU;
    float band = pow(0.5 + 0.5 * sin(rifling), TORNADO_BAND_SHARPNESS);
    float fray = 0.5 + 0.5 * sin(around * TORNADO_WISPS - travel * TORNADO_TWIST * TAU * 2.0);
    float fade = (1.0 - along) * (1.0 - along);
    fragColor = vec4(frameColor.rgb, TORNADO_STRENGTH * band * mix(1.0 - TORNADO_FRAY, 1.0, fray) * fade);
}
