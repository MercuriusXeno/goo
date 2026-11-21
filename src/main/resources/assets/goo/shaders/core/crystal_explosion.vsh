#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>

// Crystal goo's burnout explosion (decision elemental-explosion-per-type).
// CrystalExplosionVisual packs the vertex color: red is the explosion's
// progress, blue how far the shell has shattered. The normal is the unit
// direction from the center.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;

layout(location = 0) out vec3 viewPos;
layout(location = 1) out vec3 viewNormal;
layout(location = 2) out vec3 surfaceDir;
layout(location = 3) out float progress;
layout(location = 4) out float opacity;
layout(location = 5) out float shattered;

void main() {
    vec4 vp = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * vp;
    viewPos = vp.xyz;
    viewNormal = (ModelViewMat * vec4(Normal, 0.0)).xyz;
    surfaceDir = Normal;
    progress = Color.r;
    // Alpha is the dome's opacity.
    opacity = Color.a;
    shattered = Color.b;
}
