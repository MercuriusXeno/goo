#version 330

#moj_import <minecraft:globals.glsl>

// A gate's face showing the view through it (decision portals-link-by-looking-through-portal-vision):
// an oval filling the face, inside it the partner side rendered from the camera mirrored through
// the pair, sampled at this fragment's own screen point so what lies beyond shows flush, as though
// right there; a thin rim of the gate's color glows at the oval's edge.

uniform sampler2D Sampler0;

in vec2 frameCoord;
in vec4 frameColor;

out vec4 fragColor;

// Where across the oval the rim starts, zero the heart and one the edge.
const float RIM_START = 0.9;

void main() {
    vec2 across = frameCoord * 2.0 - 1.0;
    float out_ = length(across);
    if (out_ > 1.0) {
        discard;
    }
    vec3 view = texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb;
    float rim = smoothstep(RIM_START, 1.0, out_);
    fragColor = vec4(mix(view, frameColor.rgb * 1.4, rim), 1.0);
}
