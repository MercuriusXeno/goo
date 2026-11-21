#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>

// Unstable goo's burnout explosion (decision elemental-explosion-per-type).
// UnstableExplosionVisual packs the vertex color: red is the explosion's
// progress, green marks a ring vertex, blue is the ring's radial position.
// A sphere vertex with blue set is a held ghost's (decision
// held-visual-ghosts-the-landing-in-two-passes): it never fades, and its
// crackle runs on the game clock rather than on progress.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;

layout(location = 0) out vec3 viewPos;
layout(location = 1) out vec3 viewNormal;
layout(location = 2) out vec3 surfaceDir;
layout(location = 3) out float progress;
layout(location = 4) out float opacity;
layout(location = 5) out float ringFlag;
layout(location = 6) out float ringRadial;
layout(location = 7) out float held;
layout(location = 8) out float clock;

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
