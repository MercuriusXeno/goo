#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>

// Voidstone's faces (decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform):
// the stars are placed by world position. Position is camera-relative and small, so it stays exact; the
// vertex hands the fragment its offset from the camera's block, and the fragment adds the camera's block
// in integers, since a float a million blocks out cannot place a sixteenth of a block.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;

layout(location = 0) out vec3 voidLocal;
layout(location = 1) flat out vec3 voidNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    voidLocal = Position - CameraOffset;
    voidNormal = Normal;
}
