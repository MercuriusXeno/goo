#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// A portable hole's mouth carved out of the world's depth
// (decision hole-items-are-a-three-dimensional-soup-sunk-in-the-hole): the mouth's
// oval laid just off its face, camera relative.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
}
