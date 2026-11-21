#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// The block transform of decision petrify-stone-encasement-and-calcify-map:
// the old block's quads, drawn over the new block, carry how far the
// transform has run in the overlay coordinates' x instead of the hurt overlay.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec2 texCoord0;
layout(location = 5) out vec3 mingleWorldPos;
layout(location = 6) flat out float mingleProgress;
layout(location = 7) flat out float mingleSeed;

// Must match BlockMingleRenderer.PROGRESS_UNITS: UV1.x carries the share of the transform run.
const float PROGRESS_UNITS = 4096.0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    // The color arrives lit as the world lights the block, face shade, occlusion and biome tint baked in,
    // so it is applied as terrain applies it, with no entity lighting over it.
    vertexColor = Color;
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;

    // Position is camera-relative; the camera globals recover the world position.
    mingleWorldPos = Position + vec3(CameraBlockPos) - CameraOffset;
    mingleProgress = float(UV1.x) / PROGRESS_UNITS;
    // UV1.y carries the transition's seed, so each use on a block dissolves through its own field
    // (decision diagnose-then-fix-dissolve-repeat).
    mingleSeed = float(UV1.y);
}
