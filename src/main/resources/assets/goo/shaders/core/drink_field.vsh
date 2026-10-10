#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// The proxy box of one region of an Unmake drink, drawn so the fragment
// shader can march the field inside it (decision
// unmake-waves-dissolve-by-crucible-cost). Position is camera-relative, and
// is the ray to march along.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec3 rayPoint;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    rayPoint = Position;
}
