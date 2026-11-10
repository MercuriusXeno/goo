#version 330

#moj_import <minecraft:globals.glsl>
#moj_import "portal_texels.glsl"

// A gate's halo (decision portal-halo-glows-additively-onto-the-wall): its color glowing onto the
// wall around the oval, fading outward to nothing, drawn on sixteen texels a block like the rim.
// The quad reaches HALO_MARGIN_TEXELS past the oval's blocks each way, so its grid lines up texel for
// texel with the rim's and every texel inside the oval's edge is left to the rim.

in vec2 frameCoord;
flat in vec2 texels;
in vec4 frameColor;

out vec4 fragColor;

// How far the quad reaches past the oval's blocks each way, in texels: PortalFaces.HALO_MARGIN, half
// a block, counted at sixteen texels a block.
const float HALO_MARGIN_TEXELS = 8.0;
// How many texels out from the oval's edge the halo fades to nothing, short of the quad's border.
const float HALO_REACH_TEXELS = 6.0;
// How strongly the halo glows on the texels touching the oval's edge.
const float HALO_STRENGTH = 0.7;

void main() {
    vec2 oval = texels - 2.0 * HALO_MARGIN_TEXELS;
    vec2 across = (portalTexelCenter(frameCoord, texels) * texels - HALO_MARGIN_TEXELS) / oval * 2.0 - 1.0;
    float beyond = -portalTexelsInside(across, oval, 1.0);
    if (beyond <= 0.0) {
        discard;
    }
    float fade = max(1.0 - beyond / HALO_REACH_TEXELS, 0.0);
    fragColor = vec4(frameColor.rgb, frameColor.a * HALO_STRENGTH * fade * fade);
}
