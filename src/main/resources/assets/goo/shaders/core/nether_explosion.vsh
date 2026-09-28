#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Nether goo's burnout explosion (decision elemental-explosion-per-type).
// NetherExplosionVisual packs the vertex color: red is the explosion's
// progress, green how near the rush has come to the center, blue how much
// of it is left. The normal is the unit direction from the center.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 viewPos;
out vec3 viewNormal;
out vec3 surfaceDir;
out float progress;
out float nearness;
out float remaining;

void main() {
    vec4 vp = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * vp;
    viewPos = vp.xyz;
    viewNormal = (ModelViewMat * vec4(Normal, 0.0)).xyz;
    surfaceDir = Normal;
    progress = Color.r;
    nearness = Color.g;
    remaining = Color.b;
}
