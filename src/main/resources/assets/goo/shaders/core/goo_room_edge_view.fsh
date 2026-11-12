#version 330

#moj_import <minecraft:globals.glsl>

// An Outsider room's edge showing the view through it (decision outsider-room-wraps-and-repeats-at-the-platform-edge):
// the room's opposite side rendered from the camera carried across the room, sampled at this fragment's own
// screen point, over the edge's whole face, with no oval, no rim and no swirl, so the room runs on unbroken.

uniform sampler2D Sampler0;

in vec2 frameCoord;
flat in vec2 texels;
in vec4 frameColor;

out vec4 fragColor;

void main() {
    fragColor = vec4(texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb, 1.0);
}
