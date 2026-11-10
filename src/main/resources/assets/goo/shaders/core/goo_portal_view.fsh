#version 330

#moj_import <minecraft:globals.glsl>
#moj_import "portal_texels.glsl"

// A gate's face showing the view through it (decision portals-link-by-looking-through-portal-vision):
// an oval filling the face, inside it the partner side rendered from the camera mirrored through
// the pair, sampled at this fragment's own screen point so what lies beyond shows flush and sharp,
// as though right there; a thin rim of the gate's color glows at the oval's edge. The oval's edge
// and the rim draw on sixteen texels a block, stepping texel by texel; the view through stays at
// full resolution (decision portals-and-wormhole-draw-sixteen-texels-a-block).

uniform sampler2D Sampler0;

in vec2 frameCoord;
flat in vec2 texels;
in vec4 frameColor;

out vec4 fragColor;

// Where across the oval the rim starts, zero the heart and one the edge.
const float RIM_START = 0.9;

void main() {
    vec2 across = portalTexelCenter(frameCoord, texels) * 2.0 - 1.0;
    float out_ = length(across);
    if (out_ > 1.0) {
        discard;
    }
    vec3 view = texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb;
    float rim = smoothstep(RIM_START, 1.0, out_);
    fragColor = vec4(mix(view, frameColor.rgb * 1.4, rim), 1.0);
}
