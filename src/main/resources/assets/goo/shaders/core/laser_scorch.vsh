#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Laser's scorch (decision sunbeam-leaves-a-glowing-scorch-that-trails-and-fades).
// LaserScorch packs the vertex color: red is the mark's heat, 1 just burnt
// and 0 faded, green its seed. The normal's x and y carry the corner's place
// across the quad, -1 to 1.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec2 acrossMark;
out float heat;
out float seed;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    acrossMark = Normal.xy;
    heat = Color.r;
    seed = Color.g;
}
