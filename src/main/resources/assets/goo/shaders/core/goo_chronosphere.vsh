#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Chronosphere's veil (decision chronosphere-hastes-players-slows-mobs).
// ChronosphereVisual packs the vertex color: red, green and blue are the
// vertex's direction from the sphere's center, each of [0, 1]; alpha is how
// strongly the veil draws.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 sphereDir;
out vec3 viewNormal;
out vec3 viewPos;
out float strength;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    viewPos = view.xyz;
    viewNormal = mat3(ModelViewMat) * Normal;
    sphereDir = Color.rgb * 2.0 - 1.0;
    strength = Color.a;
}
