#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Rock goo's burnout explosion (decision elemental-explosion-per-type).
// RockExplosionVisual packs the vertex color: red is the explosion's
// progress, green and blue the vertex's position on the disc, each of
// [-1, 1] mapped onto [0, 1], and alpha the share of the dust's opacity:
// whole for a landing, a fraction for Crush's held ghost.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out float progress;
out vec2 discPos;
out float opacity;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    progress = Color.r;
    discPos = Color.gb * 2.0 - 1.0;
    opacity = Color.a;
}
