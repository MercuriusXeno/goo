#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>

// Metal goo's burnout explosion (decision elemental-explosion-per-type).
// MetalExplosionVisual packs the vertex color: red is the explosion's
// progress, green the vertex's place along its spike (0 at the base, 1 at
// the tip), alpha its opacity, whole for a landing and a share of it for a
// held ghost. The normal is the cone side's outward normal.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;

layout(location = 0) out vec3 viewPos;
layout(location = 1) out vec3 viewNormal;
layout(location = 2) out float progress;
layout(location = 3) out float along;
layout(location = 4) out float opacity;

void main() {
    vec4 vp = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * vp;
    viewPos = vp.xyz;
    viewNormal = (ModelViewMat * vec4(Normal, 0.0)).xyz;
    progress = Color.r;
    along = Color.g;
    opacity = Color.a;
}
