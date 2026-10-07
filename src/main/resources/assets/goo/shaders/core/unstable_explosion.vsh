#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

// Unstable goo's burnout explosion (decision elemental-explosion-per-type).
// UnstableExplosionVisual packs the vertex color: red is the explosion's
// progress, green marks a ring vertex, blue is the ring's radial position.
// A sphere vertex with blue set is a held ghost's (decision
// held-visual-ghosts-the-landing-in-two-passes): it never fades, and its
// crackle runs on the game clock rather than on progress.

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
out float held;
out float clock;

// Landing progress per day fraction: a 24000-tick day over a 16-tick show.
const float HELD_CLOCK_RATE = 1500.0;

void main() {
    vec4 vp = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * vp;
    viewPos = vp.xyz;
    viewNormal = (ModelViewMat * vec4(Normal, 0.0)).xyz;
    // The unit-sphere direction stays put as the camera moves, so the
    // crackle noise sits on the fireball's surface rather than swimming.
    surfaceDir = Normal;
    progress = Color.r;
    // Alpha is the dome's opacity.
    opacity = Color.a;
    ringFlag = Color.g;
    ringRadial = Color.b;
    held = (1.0 - step(0.5, Color.g)) * step(0.5, Color.b);
    clock = held > 0.5 ? GameTime * HELD_CLOCK_RATE : Color.r;
}
