#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Unmake's ripple rings (decision unmake-waves-dissolve-by-crucible-cost).
// UnmakeWaves packs the vertex color: red is how far down the cone the ring
// stands, green which edge of the ring's band the vertex sits on, 0 inner to
// 1 outer.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out float along;
out float across;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    along = Color.r;
    across = Color.g;
}
