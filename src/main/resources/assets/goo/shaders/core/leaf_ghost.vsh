#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Leaf's held ghost, Bloom's pollen haze sphere (decision bloom-places-buds-by-biome-and-surface).
// LeafHeldGhost packs the sphere's opacity in the vertex color's alpha. The
// normal is the unit direction from the sphere's center.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 surfaceDir;
out float opacity;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    surfaceDir = Normal;
    opacity = Color.a;
}
