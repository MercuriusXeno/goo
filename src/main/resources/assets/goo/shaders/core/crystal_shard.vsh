#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;

layout(location = 0) out vec4 vertColor;

void main() {
    gl_Position = ProjMat * (ModelViewMat * vec4(Position, 1.0));
    vertColor = Color;
}
