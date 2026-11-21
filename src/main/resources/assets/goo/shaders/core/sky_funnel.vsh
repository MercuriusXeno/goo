#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>

// The sky funnel and the doorway it rises from (decision
// sky-funnel-points-down-to-the-doorway-from-any-distance): a window into space. The position
// arrives relative to the camera in the world's axes, so it is the direction the point is seen
// through, which the fragment shader reads the stars along; the color carries the brightness.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec3 seenAlong;
layout(location = 1) out vec4 brightness;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    seenAlong = Position;
    brightness = Color;
}
