#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// The box around Bore's drill, drawn so the fragment shader can march to the bit inside it
// (decision bore-shows-a-big-drill-from-the-glove). Position is camera-relative, and is the ray to
// march along.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec3 rayPoint;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    rayPoint = Position;
}
