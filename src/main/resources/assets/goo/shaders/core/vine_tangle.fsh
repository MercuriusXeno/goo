#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import "mingle_noise.glsl"

// Vines tangled over a rooted mob (decision vines-unpack-root-and-thorn):
// vanilla's vine texture laid over the model's skin coordinates, its gaps left
// open so the mob shows between the strands, in noise patches covering the
// share of the model the vertex alpha carries, so the tangle spreads over the
// mob as the blob unpacks and thins away as the vines fall.

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vineColor;
in vec4 lightMapColor;
in vec2 skinCoord;
in vec3 skinNormal;

out vec4 fragColor;

// A 64-texel skin across [0, 1] holds four 16-texel vine tiles, so the vines keep the skin's texel size.
const float VINE_TILES = 4.0;
// Noise cells across the skin's coordinates: a handful of patches over a body.
const float PATCH_CELLS = 6.0;
// A vine texel this transparent is a gap between strands.
const float STRAND_ALPHA = 0.5;
// A share at or over this is a whole tangle, with no patch edges left.
const float WHOLE = 0.999;

void main() {
    float share = vineColor.a;
    if (share < WHOLE && mingleField(vec3(skinCoord * PATCH_CELLS, 0.0)) < mingleThreshold(share)) {
        discard;
    }
    vec4 vine = texture(Sampler0, fract(skinCoord * VINE_TILES));
    if (vine.a < STRAND_ALPHA) {
        discard;
    }
    float shade = 0.7 + 0.3 * abs(normalize(skinNormal).y);
    vec3 lit = vine.rgb * vineColor.rgb * shade * lightMapColor.rgb;
    fragColor = apply_fog(vec4(lit, 1.0), sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
