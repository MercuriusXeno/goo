// The texel grid of decision portals-and-wormhole-draw-sixteen-texels-a-block: a gate's face draws
// on sixteen texels a block fixed to the face, as a block's texture does, so the opening is no
// sharper than the blocks around it. Every fragment within a texel takes the one value its texel's
// center answers. A shader under shaders/core imports it by its quoted relative name.

// The center of the texel a face coordinate, zero to one across each way, falls in, on a grid of
// texels across each way.
vec2 portalTexelCenter(vec2 frameCoord, vec2 texels) {
    return (floor(frameCoord * texels) + 0.5) / texels;
}

// A fragment's texel: its center on the face, and the screen point that center stands at.
struct PortalTexel {
    vec2 center;
    vec2 screenPoint;
};

// A fragment's texel, the screen point of its center reached from the fragment's own screen point
// through how the face coordinate changes across the screen, so a view sampled there shows one
// color of the far side across the whole texel.
PortalTexel portalTexel(vec2 frameCoord, vec2 texels, vec2 fragPoint) {
    vec2 center = portalTexelCenter(frameCoord, texels);
    mat2 faceAcrossScreen = mat2(dFdx(frameCoord), dFdy(frameCoord));
    return PortalTexel(center, fragPoint + inverse(faceAcrossScreen) * (center - frameCoord));
}
