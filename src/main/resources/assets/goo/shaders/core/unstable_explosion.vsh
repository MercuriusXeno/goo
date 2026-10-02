#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Unstable goo's burnout explosion (decision elemental-explosion-per-type).
// UnstableExplosionVisual packs the vertex color: red is the explosion's
// progress, green marks a ring vertex, blue is the ring's radial position.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 viewPos;
out vec3 viewNormal;
out vec3 surfaceDir;
out float progress;
out float opacity;
out float ringFlag;
out float ringRadial;

void main() {
    vec4 vp = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * vp;
    viewPos = vp.xyz;
    viewNormal = (ModelViewMat * vec4(Normal, 0.0)).xyz;
    // The unit-sphere direction stays put as the camera moves, so the
    // crackle noise sits on the fireball's surface rather than swimming.
    surfaceDir = Normal;
    progress = Color.r;
    // Alpha is the dome's opacity, faded in over the fuse tail (decision dome-fades-in-before-its-start).
    opacity = Color.a;
    ringFlag = Color.g;
    ringRadial = Color.b;
}
