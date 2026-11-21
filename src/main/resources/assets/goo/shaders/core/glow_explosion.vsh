#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>

// Glow goo's burnout explosion (decisions elemental-explosion-per-type,
// burnouts-are-whole-spheres). GlowExplosionVisual packs the vertex color:
// red is the explosion's progress, blue the bloom's brightness. The normal
// is the unit direction from the sphere's center.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;

layout(location = 0) out vec3 surfaceDir;
layout(location = 1) out float progress;
layout(location = 2) out float opacity;
layout(location = 3) out float brightness;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    surfaceDir = Normal;
    progress = Color.r;
    // Alpha is the sphere's opacity.
    opacity = Color.a;
    brightness = Color.b;
}
