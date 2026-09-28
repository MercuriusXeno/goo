#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Frost goo's burnout explosion (decision elemental-explosion-per-type).
// FrostExplosionVisual packs the vertex color: red is the explosion's
// progress, green how far the fog has rolled, blue how much mist is left.
// The normal is the vertex's direction from the zone's center.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 viewPos;
out vec3 viewNormal;
out vec3 surfaceDir;
out float progress;
out float rolled;
out float mist;

void main() {
    vec4 vp = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * vp;
    viewPos = vp.xyz;
    viewNormal = (ModelViewMat * vec4(Normal, 0.0)).xyz;
    surfaceDir = Normal;
    progress = Color.r;
    rolled = Color.g;
    mist = Color.b;
}
