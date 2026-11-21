#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>

// Rock goo's burnout explosion (decision elemental-explosion-per-type).
// RockExplosionVisual packs the vertex color: red is the explosion's
// progress, green and blue the vertex's position on the disc, each of
// [-1, 1] mapped onto [0, 1], and alpha the share of the dust's opacity:
// whole for a landing, a fraction for Crush's held ghost.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;

layout(location = 0) out float progress;
layout(location = 1) out vec2 discPos;
layout(location = 2) out float opacity;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    progress = Color.r;
    discPos = Color.gb * 2.0 - 1.0;
    opacity = Color.a;
}
