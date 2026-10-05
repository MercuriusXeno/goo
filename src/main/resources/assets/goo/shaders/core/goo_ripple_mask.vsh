#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// One silhouette's mask in an afterimage's ripple (decision afterimage-is-one-shared-effect):
// AfterimageRenderer fills the echoed body's grown cubes in camera space, the vertex
// alpha the silhouette's fade.

in vec3 Position;
in vec4 Color;

out float fade;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    fade = Color.a;
}
