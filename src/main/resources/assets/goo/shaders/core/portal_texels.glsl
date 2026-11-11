// The texel grid of decision portals-and-wormhole-draw-sixteen-texels-a-block: a gate's oval edge,
// its rim and its swirl draw on sixteen texels a block fixed to the face, as a block's texture does,
// so they are no sharper than the blocks around them; the vortex's mouth, the swirl band's line,
// cuts the rim's texels along its crisp edge, and the view through stays at full resolution. Every
// fragment within a texel takes the one value its texel's center answers, but for that cut. A shader
// under shaders/core imports it by its quoted relative name.

// The center of the texel a face coordinate, zero to one across each way, falls in, on a grid of
// texels across each way.
vec2 portalTexelCenter(vec2 frameCoord, vec2 texels) {
    return (floor(frameCoord * texels) + 0.5) / texels;
}

// How many texels a point stands inside an oval's edge, measured square to the edge on the face, so
// a rim measured in texels runs the same width all round however long the oval is, and every texel
// on the edge takes it. across is the point from the oval's middle, minus one to one each way; edge
// is how far out the edge stands that way, one for a clean oval.
float portalTexelsInside(vec2 across, vec2 texels, float edge) {
    float out_ = max(length(across), 1.0e-4);
    return (edge - out_) / length(across / out_ * 2.0 / texels);
}

// How deep a hole mouth's tear bites into the oval, as a share of its radius.
const float HOLE_TEAR_DEPTH = 0.16;
// How fast the tear shifts; GameTime runs once a day, so this sets turns per day.
const float HOLE_TEAR_SPEED = 2400.0;

// How far out a hole mouth's torn, shifting edge stands at an angle around the oval, one for a clean
// oval's edge (decision hole-closes-the-space-to-an-opening-ahead). The mouth's own shader and its
// halo both read it, so the halo starts where the tear ends (decision hole-mouths-wear-the-portal-halo).
float holeTornEdge(float angle, float gameTime) {
    float time = gameTime * HOLE_TEAR_SPEED;
    float tear = 0.5 * sin(angle * 5.0 + time * 1.7)
        + 0.3 * sin(angle * 11.0 - time * 2.9)
        + 0.2 * sin(angle * 23.0 + time * 4.3);
    return 1.0 - HOLE_TEAR_DEPTH * (0.5 + 0.5 * tear);
}

// How many texels inside the oval's edge the swirl band's line runs: PortalFaces.BAND_INSET, half a
// texel. The rim paints only outside it, so the band's line visibly cuts the rim
// (decision vortex-mouth-cuts-the-pixel-rim).
const float PORTAL_RIM_LINE = 0.5;
// How many texels out from the line the rim reaches at most.
const float PORTAL_RIM_WIDTH = 1.5;
// How strongly the rim paints by the line, softened from whole.
const float PORTAL_RIM_STRENGTH = 0.8;

// How opaque a texel the rim paints draws, a quarter of whatever it sits on showing through, so the
// rim blends onto the wall.
const float PORTAL_RIM_EDGE_ALPHA = 0.75;

// A rim's strength at a fragment. texelInside is how far its texel's center stands inside the edge,
// exactInside how far the fragment itself does. Inside the swirl band's line, and more than
// PORTAL_RIM_WIDTH outside it, the fragment's own point takes no rim, so the line cuts the blocky
// texels in a smooth curve. Between, the texel's center sets its strength, strongest by the line and
// fainter texel by texel outward (decision vortex-mouth-cuts-the-pixel-rim).
float portalTexelRim(float texelInside, float exactInside) {
    if (exactInside > PORTAL_RIM_LINE || PORTAL_RIM_LINE - exactInside > PORTAL_RIM_WIDTH) {
        return 0.0;
    }
    float outward = max(PORTAL_RIM_LINE - texelInside, 0.0);
    return PORTAL_RIM_STRENGTH * max(1.0 - outward / PORTAL_RIM_WIDTH, 0.0);
}

// How opaque a fragment draws for the rim's sake: where the rim paints, a quarter of the wall shows
// through; everywhere else draws as it would.
float portalTexelEdgeAlpha(float rim) {
    return rim > 0.0 ? PORTAL_RIM_EDGE_ALPHA : 1.0;
}
