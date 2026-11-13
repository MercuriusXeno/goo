#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// The sky funnel and the doorway it rises from (decision
// sky-funnel-points-down-to-the-doorway-from-any-distance): a window into space. The position
// arrives relative to the camera in the world's axes, so it is the direction the point is seen
// through, which the fragment shader reads the stars along; the color carries the brightness.

in vec3 Position;
in vec4 Color;

out vec3 seenAlong;
out vec4 brightness;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    seenAlong = Position;
    brightness = Color;
}
