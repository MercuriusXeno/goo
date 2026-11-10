#version 330

#moj_import "portal_texels.glsl"

// A portal hidden behind blocks while its owner chooses a partner (decision
// portals-link-by-looking-through-portal-vision): only a thin glowing ring at the oval's edge,
// drawn through the blocks, so the portal can be found without covering what stands before it.
// The ring draws on sixteen texels a block, measured in texels so it runs unbroken all round,
// whole on the edge's own texel and fading inward (decision portals-and-wormhole-draw-sixteen-texels-a-block).

in vec2 frameCoord;
flat in vec2 texels;
in vec4 frameColor;

out vec4 fragColor;

// The ring runs whole over the edge's own texel, then fades inward over this many texels.
const float RING_SOLID = 1.0;
const float RING_FEATHER = 2.0;

void main() {
    vec2 across = portalTexelCenter(frameCoord, texels) * 2.0 - 1.0;
    if (length(across) > 1.0) {
        discard;
    }
    float glow = portalTexelRim(portalTexelsInside(across, texels, 1.0), RING_SOLID, RING_FEATHER);
    if (glow <= 0.0) {
        discard;
    }
    fragColor = vec4(frameColor.rgb * 1.3, frameColor.a * glow);
}
