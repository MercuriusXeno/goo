#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import "mingle_noise.glsl"

// Petrify's stone over a mob (decision petrify-stone-encasement-and-calcify-map):
// vanilla's stone texture laid over the model's skin coordinates, in noise patches
// that cover the share of the model the vertex alpha carries, so the patches
// spread and grow together as the petrify gauge fills, whole at a statue. A thin
// dark seam rings each patch, the crack where stone meets flesh. Frost draws through
// this same shader with packed ice in place of stone, its share the frozen gauge
// (decision frozen-gauge-per-mob-encases-when-full).

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 stoneColor;
in vec4 lightMapColor;
in vec2 skinCoord;
in vec3 skinNormal;

out vec4 fragColor;

// A 64-texel skin across [0, 1] holds four 16-texel stone tiles, so the stone keeps the skin's texel size.
const float STONE_TILES = 4.0;
// Noise cells across the skin's coordinates: a handful of patches over a body.
const float PATCH_CELLS = 6.0;
// Width of the crack ringing each patch, in field units, and how dark it runs.
const float SEAM_BAND = 0.04;
const float SEAM_SHADE = 0.45;
// A share at or over this is a whole statue, with no patch edges left.
const float WHOLE = 0.999;

void main() {
    float share = stoneColor.a;
    float seam = 0.0;
    if (share < WHOLE) {
        float threshold = mingleThreshold(share);
        float field = mingleField(vec3(skinCoord * PATCH_CELLS, 0.0));
        if (field < threshold) {
            discard;
        }
        seam = 1.0 - smoothstep(threshold, threshold + SEAM_BAND, field);
    }
    vec4 stone = texture(Sampler0, fract(skinCoord * STONE_TILES));
    float shade = 0.7 + 0.3 * abs(normalize(skinNormal).y);
    vec3 lit = stone.rgb * shade * mix(1.0, SEAM_SHADE, seam);
    lit *= lightMapColor.rgb;
    fragColor = apply_fog(vec4(lit, 1.0), sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
