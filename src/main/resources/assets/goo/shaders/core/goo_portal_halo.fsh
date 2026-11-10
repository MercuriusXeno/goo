#version 330

#moj_import <minecraft:globals.glsl>
#moj_import "portal_texels.glsl"

// A gate's halo (decision portal-halo-glows-additively-onto-the-wall): its color glowing onto the
// wall around the oval, fading outward to nothing, drawn on sixteen texels a block like the rim.
// The quad reaches HALO_MARGIN_TEXELS past the oval's blocks each way, so its grid lines up texel for
// texel with the rim's and every texel inside the oval's edge is left to the rim.
// The halo breaks into feathered tongues that circle the oval like a vortex while its outer reach
// ripples in and out, never resting; every term reads the snapped texel center, so the motion steps
// texel to texel (decision portal-halo-turns-and-ripples).

in vec2 frameCoord;
flat in vec2 texels;
in vec4 frameColor;

out vec4 fragColor;

// How far the quad reaches past the oval's blocks each way, in texels: PortalFaces.HALO_MARGIN, half
// a block, counted at sixteen texels a block.
const float HALO_MARGIN_TEXELS = 8.0;
// How many texels out from the oval's edge the halo fades to nothing at its longest tongue's still
// reach, short of the quad's border.
const float HALO_REACH_TEXELS = 6.0;
// How strongly the halo glows on the texels touching the oval's edge.
const float HALO_STRENGTH = 0.7;
// GameTime runs from zero to one over a day of 24000 ticks; this many seconds make one day. Each
// period below divides it whole, so the halo carries on unbroken as the day wraps.
const float SECONDS_PER_DAY = 1200.0;
const float TAU = 6.28318530718;
// How many seconds the tongues take to circle the oval once, tuned by eye.
const float HALO_TURN_SECONDS = 8.0;
// How many seconds the outer reach takes to ripple out and back once, tuned by eye.
const float HALO_RIPPLE_SECONDS = 1.0;
// How many texels the outer reach ripples in and out by.
const float HALO_RIPPLE_TEXELS = 1.5;
// How many tongues the halo breaks into around the oval, the coarsest of its noise's three layers.
const float HALO_TONGUES = 5.0;
// The share of the reach a tongue's gap still keeps, so the halo thins between tongues and never parts.
const float HALO_GAP_REACH = 0.45;
// The texel distance a ripple never carries the reach past: the quad's border, half a texel in.
const float HALO_REACH_LIMIT = HALO_MARGIN_TEXELS - 0.5;

// A noise taken over the angle around the oval, nothing to one, its layers whole multiples of the
// tongue count, so it closes on itself all round.
float tongues(float angle) {
    float noise = 0.55 * sin(angle * HALO_TONGUES)
        + 0.3 * sin(angle * HALO_TONGUES * 2.0 + 1.3)
        + 0.15 * sin(angle * HALO_TONGUES * 5.0 + 2.1);
    return 0.5 + 0.5 * noise;
}

void main() {
    vec2 oval = texels - 2.0 * HALO_MARGIN_TEXELS;
    vec2 across = (portalTexelCenter(frameCoord, texels) * texels - HALO_MARGIN_TEXELS) / oval * 2.0 - 1.0;
    float beyond = -portalTexelsInside(across, oval, 1.0);
    if (beyond <= 0.0) {
        discard;
    }
    float seconds = GameTime * SECONDS_PER_DAY;
    float angle = atan(across.y, across.x) + seconds * TAU / HALO_TURN_SECONDS;
    float ripple = sin(seconds * TAU / HALO_RIPPLE_SECONDS) * HALO_RIPPLE_TEXELS;
    float tongue = mix(HALO_GAP_REACH, 1.0, tongues(angle));
    float reach = min(HALO_REACH_TEXELS * tongue + ripple, HALO_REACH_LIMIT);
    float fade = max(1.0 - beyond / max(reach, 1.0), 0.0);
    fragColor = vec4(frameColor.rgb, frameColor.a * HALO_STRENGTH * fade * fade);
}
