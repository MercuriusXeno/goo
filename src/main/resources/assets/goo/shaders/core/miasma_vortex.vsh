#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// The box around Drain's cone, drawn so the fragment shader can march the miasma vortex inside it
// (decision drain-casts-a-filled-tornadic-miasma-vortex-along-the-look). Position is camera-relative,
// and is the ray to march along.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec3 rayPoint;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    rayPoint = Position;
}
