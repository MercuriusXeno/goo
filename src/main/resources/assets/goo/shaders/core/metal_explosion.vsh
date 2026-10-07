#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Metal goo's burnout explosion (decision elemental-explosion-per-type).
// MetalExplosionVisual packs the vertex color: red is the explosion's
// progress, green the vertex's place along its spike (0 at the base, 1 at
// the tip), alpha its opacity, whole for a landing and a share of it for a
// held ghost. The normal is the cone side's outward normal.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 viewPos;
out vec3 viewNormal;
out float progress;
out float along;
out float opacity;

void main() {
    vec4 vp = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * vp;
    viewPos = vp.xyz;
    viewNormal = (ModelViewMat * vec4(Normal, 0.0)).xyz;
    progress = Color.r;
    along = Color.g;
    opacity = Color.a;
}
