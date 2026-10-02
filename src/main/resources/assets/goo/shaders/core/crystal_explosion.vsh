#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Crystal goo's burnout explosion (decision elemental-explosion-per-type).
// CrystalExplosionVisual packs the vertex color: red is the explosion's
// progress, blue how far the shell has shattered. The normal is the unit
// direction from the center.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 viewPos;
out vec3 viewNormal;
out vec3 surfaceDir;
out float progress;
out float opacity;
out float shattered;

void main() {
    vec4 vp = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * vp;
    viewPos = vp.xyz;
    viewNormal = (ModelViewMat * vec4(Normal, 0.0)).xyz;
    surfaceDir = Normal;
    progress = Color.r;
    // Alpha is the dome's opacity, faded in over the fuse tail (decision dome-fades-in-before-its-start).
    opacity = Color.a;
    shattered = Color.b;
}
