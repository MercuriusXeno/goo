#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>

// Leaf's held ghost, Bloom's pollen haze sphere (decision bloom-places-buds-by-biome-and-surface).
// LeafHeldGhost packs the sphere's opacity in the vertex color's alpha. The
// normal is the unit direction from the sphere's center.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;

layout(location = 0) out vec3 surfaceDir;
layout(location = 1) out float opacity;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    surfaceDir = Normal;
    opacity = Color.a;
}
