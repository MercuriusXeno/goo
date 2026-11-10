// The texel grid of decision portals-and-wormhole-draw-sixteen-texels-a-block: a gate's oval edge,
// its rim and its swirl draw on sixteen texels a block fixed to the face, as a block's texture does,
// so they are no sharper than the blocks around them; the view through stays at full resolution.
// Every fragment within a texel takes the one value its texel's center answers. A shader under
// shaders/core imports it by its quoted relative name.

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

// How faintly the texel just inside the rim takes its color.
const float PORTAL_RIM_FAINT = 0.3;

// A rim's strength at a texel: whole on the one ring of texels on the edge, faint on the ring just
// inside it, nothing further in.
float portalTexelRim(float texelsInside) {
    return texelsInside < 1.0 ? 1.0 : (texelsInside < 2.0 ? PORTAL_RIM_FAINT : 0.0);
}
