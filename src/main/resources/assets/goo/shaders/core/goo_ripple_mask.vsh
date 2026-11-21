#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>

// One silhouette's mask in an afterimage's ripple (decision afterimage-is-one-shared-effect):
// AfterimageRenderer fills the echoed body's grown cubes in camera space, the vertex
// alpha the silhouette's fade.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out float fade;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    fade = Color.a;
}
