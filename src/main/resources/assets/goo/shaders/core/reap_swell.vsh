#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Reap's swell (decision reap-breeze-harvests-and-replants). ReapSwells packs the
// swell's strength in the vertex color's alpha. The normal is the unit direction
// from the sphere's center.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 surfaceDir;
out float strength;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    surfaceDir = Normal;
    strength = Color.a;
}
