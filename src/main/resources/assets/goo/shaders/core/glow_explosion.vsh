#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Glow goo's burnout explosion (decisions elemental-explosion-per-type,
// burnouts-are-whole-spheres). GlowExplosionVisual packs the vertex color:
// red is the explosion's progress, blue the bloom's brightness. The normal
// is the unit direction from the sphere's center.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 surfaceDir;
out float progress;
out float opacity;
out float brightness;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    surfaceDir = Normal;
    progress = Color.r;
    // Alpha is the sphere's opacity.
    opacity = Color.a;
    brightness = Color.b;
}
