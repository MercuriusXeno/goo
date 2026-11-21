#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>

// Growth's breeze (decision growth-breeze-ticks-plants).
// GrowthBreeze packs the vertex color, as PetrifyFog does: red is how far along the cone the
// cross-section stands, green and blue the vertex's position on the
// cross-section, each of [-1, 1] mapped onto [0, 1].

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;

layout(location = 0) out float along;
layout(location = 1) out vec2 discPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    along = Color.r;
    discPos = Color.gb * 2.0 - 1.0;
}
