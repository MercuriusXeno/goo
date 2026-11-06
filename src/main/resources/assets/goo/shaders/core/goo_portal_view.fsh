#version 330

#moj_import <minecraft:globals.glsl>

// A linked portal's face (decision portals-link-by-looking-through-portal-vision): the partner
// side, rendered from the camera mirrored through the pair at the frame's own size, sampled at
// this fragment's screen point, so what lies beyond the partner shows flush, as though right there.

uniform sampler2D Sampler0;

out vec4 fragColor;

void main() {
    fragColor = vec4(texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb, 1.0);
}
