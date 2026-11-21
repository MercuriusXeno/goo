#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>

// Petrify's fog (decision petrify-fog-is-billowing-roiling-dust).
// PetrifyFog packs the vertex color: red is the puff's share of its life,
// green and blue the vertex's corner on the puff's quad, each of [-1, 1]
// mapped onto [0, 1], and alpha the puff's seed.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;

layout(location = 0) out float age;
layout(location = 1) out vec2 corner;
layout(location = 2) flat out float seed;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    age = Color.r;
    corner = Color.gb * 2.0 - 1.0;
    seed = Color.a;
}
