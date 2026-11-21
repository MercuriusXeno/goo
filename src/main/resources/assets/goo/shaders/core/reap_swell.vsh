#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>

// Reap's swell (decision reap-breeze-harvests-and-replants). ReapSwells packs the
// swell's strength in the vertex color's alpha. The normal is the unit direction
// from the sphere's center.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;

layout(location = 0) out vec3 surfaceDir;
layout(location = 1) out float strength;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    surfaceDir = Normal;
    strength = Color.a;
}
