#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>
#include <goo:mingle_noise.glsl>

// The block transform of decision petrify-stone-encasement-and-calcify-map:
// the old block mingles into the new. The mingle noise field over world
// position is thresholded so the share of the old block left standing is the
// share of the transform not yet run; below the threshold the old block is
// discarded and the new block beneath shows, and a thin seam at the threshold
// darkens, so the two read as one material shifting into the other.

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec2 texCoord0;
layout(location = 5) in vec3 mingleWorldPos;
layout(location = 6) flat in float mingleProgress;
layout(location = 7) flat in float mingleSeed;

layout(location = 0) out vec4 fragColor;

// Noise cells per block: a block face breaks into several patches, each
// several texels across (decision mingle-noise-snaps-to-texels).
const float MINGLE_BLOCK_CELLS = 3.0;
// Width of the darkened seam above the threshold, in field units.
const float SEAM_BAND = 0.05;
// How dark the seam runs at the threshold.
const float SEAM_SHADE = 0.55;

void main() {
    float threshold = mingleThreshold(1.0 - mingleProgress);
    // The field is read at the world texel grid's corners and blended per pixel, so the old block's edge runs
    // through its texels, its shape pinned to them (decision cut-edge-blends-texel-corners). Each seed step moves
    // the sample tens of cells per axis, so each use on a block dissolves through its own field
    // (decision diagnose-then-fix-dissolve-repeat).
    float field = mingleWorldCornerField(mingleWorldPos, MINGLE_BLOCK_CELLS, mingleSeed * MINGLE_SEED_OFFSET);
    if (field < threshold) {
        discard;
    }
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }
    color *= vertexColor * ColorModulator;
    color *= lightMapColor;
    float seam = 1.0 - step(threshold + SEAM_BAND, field);
    color.rgb *= mix(1.0, SEAM_SHADE, seam);
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
