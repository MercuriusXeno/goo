#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// The proxy boxes of an Unmake drink's stream, each a body's bounds grown by
// the field's reach, drawn so the fragment shader can march the field inside
// them (decision unmake-waves-dissolve-by-crucible-cost). Position is
// camera-relative; UV1.x names the proxy the vertex belongs to.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec3 rayPoint;
flat out int proxy;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    rayPoint = Position;
    proxy = UV1.x;
}
