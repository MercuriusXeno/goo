#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Goo's swirling ring particle (decision goo-swirl-ring-particle).
// GooRingMesh packs each vertex: the vertex color's red, green and blue are
// the theme tint, its alpha the particle's progress, and UV0 the vertex's
// position on the disc in [-1, 1].

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out float progress;
out vec2 discPos;
out vec3 tint;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    progress = Color.a;
    discPos = UV0;
    tint = Color.rgb;
}
