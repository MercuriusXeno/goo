#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import "mingle_noise.glsl"

// The block transform of decision petrify-stone-encasement-and-calcify-map:
// the old block mingles into the new. The mingle noise field over world
// position is thresholded so the share of the old block left standing is the
// share of the transform not yet run; below the threshold the old block is
// discarded and the new block beneath shows, and a thin seam at the threshold
// darkens, so the two read as one material shifting into the other.

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;
in vec3 mingleWorldPos;
flat in float mingleProgress;

out vec4 fragColor;

// Noise cells per block: fine enough that a block face breaks into several patches.
const float MINGLE_BLOCK_CELLS = 4.0;
// Width of the darkened seam above the threshold, in field units.
const float SEAM_BAND = 0.05;
// How dark the seam runs at the threshold.
const float SEAM_SHADE = 0.55;

void main() {
    float threshold = mingleThreshold(1.0 - mingleProgress);
    float field = mingleField(mingleWorldPos * MINGLE_BLOCK_CELLS);
    if (field < threshold) {
        discard;
    }
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }
    color *= vertexColor * ColorModulator;
    color *= lightMapColor;
    float seam = 1.0 - smoothstep(threshold, threshold + SEAM_BAND, field);
    color.rgb *= mix(1.0, SEAM_SHADE, seam);
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
