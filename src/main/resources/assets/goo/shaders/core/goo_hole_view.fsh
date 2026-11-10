#version 330

#moj_import <minecraft:globals.glsl>
#moj_import "portal_texels.glsl"

// A hole's mouth showing the view through it (decision hole-closes-the-space-to-an-opening-ahead):
// the far side rendered from the camera mirrored through the pair, sampled at this fragment's own
// screen point, laid over the wall see-through, inside an edge torn and shifting rather than a clean
// oval, a band of the mouth's color along the tear. The torn edge and the band draw on sixteen
// texels a block, each texel inside or outside the mouth whole, while the tear keeps shifting in
// time; the view through stays at full resolution
// (decision portals-and-wormhole-draw-sixteen-texels-a-block).

uniform sampler2D Sampler0;

in vec2 frameCoord;
flat in vec2 texels;
in vec4 frameColor;

out vec4 fragColor;

// How strongly the far side shows through the mouth, the wall showing through the rest.
const float VIEW_OPACITY = 0.6;

void main() {
    vec2 across = portalTexelCenter(frameCoord, texels) * 2.0 - 1.0;
    float out_ = length(across);
    float edge = holeTornEdge(atan(across.y, across.x), GameTime);
    if (out_ > edge) {
        discard;
    }
    vec3 view = texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb;
    float inside = portalTexelsInside(across, texels, edge);
    float band = portalTexelRim(inside);
    vec3 color = mix(view, frameColor.rgb * 1.4, band);
    fragColor = vec4(color, min(mix(VIEW_OPACITY, 1.0, band), portalTexelEdgeAlpha(inside)));
}
