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
