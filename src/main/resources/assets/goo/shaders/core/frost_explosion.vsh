#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Frost goo's burnout explosion (decision elemental-explosion-per-type).
// FrostExplosionVisual packs the vertex color: red is the explosion's
// progress, green how far the frost has crystallized, blue how much of
// the nova is left. The normal is the unit direction from the center.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 viewPos;
out vec3 viewNormal;
out vec3 surfaceDir;
out float crystallized;
out float remaining;

void main() {
    vec4 vp = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * vp;
    viewPos = vp.xyz;
    viewNormal = (ModelViewMat * vec4(Normal, 0.0)).xyz;
    surfaceDir = Normal;
    crystallized = Color.g;
    remaining = Color.b;
}
