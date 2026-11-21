#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>

// Laser's scorch (decision sunbeam-leaves-a-glowing-scorch-that-trails-and-fades).
// LaserScorch packs the vertex color: red is the mark's heat, 1 just burnt
// and 0 faded, green its seed. The normal's x and y carry the corner's place
// across the quad, -1 to 1.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;

layout(location = 0) out vec2 acrossMark;
layout(location = 1) out float heat;
layout(location = 2) out float seed;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    acrossMark = Normal.xy;
    heat = Color.r;
    seed = Color.g;
}
