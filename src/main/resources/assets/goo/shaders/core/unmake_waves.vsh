#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Unmake's waves (decision unmake-waves-dissolve-by-crucible-cost).
// UnmakeWaves packs the vertex color: red is how far along the cone the
// cross-section stands, green and blue the vertex's position on the
// cross-section, each of [-1, 1] mapped onto [0, 1].

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out float along;
out vec2 discPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    along = Color.r;
    discPos = Color.gb * 2.0 - 1.0;
}
