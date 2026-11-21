#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>
#include <goo:mingle_noise.glsl>

// Vines tangled over a rooted mob (decision vines-unpack-root-and-thorn):
// vanilla's vine texture laid over the model's skin coordinates, its gaps left
// open so the mob shows between the strands, in noise patches covering the
// share of the model the vertex alpha carries, so the tangle spreads over the
// mob as the blob unpacks and thins away as the vines fall.

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vineColor;
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec2 skinCoord;
layout(location = 5) in vec3 skinNormal;
// The skin's texels across and down, which the vine tile and the patch field lay their grid on
// (decision diagnose-and-correct-vines-and-ailment-overlay-assuming-a-square-skin).
layout(location = 6) flat in vec2 skinTexels;

layout(location = 0) out vec4 fragColor;

// Noise cells across the skin's coordinates: a handful of patches over a body.
const float PATCH_CELLS = 6.0;
// A vine texel this transparent is a gap between strands.
const float STRAND_ALPHA = 0.5;
// A share at or over this is a whole tangle, with no patch edges left.
const float WHOLE = 0.999;

void main() {
    float share = vineColor.a;
    // The field is read at the skin texel grid's corners and blended per pixel, so the tangle's edge runs
    // through the skin's texels, its shape pinned to them (decision cut-edge-blends-texel-corners).
    if (share < WHOLE
            && mingleCornerField(skinCoord, skinTexels, PATCH_CELLS, vec3(0.0)) < mingleThreshold(share)) {
        discard;
    }
    // One vine texel per skin texel along either axis, so the vine's texels are square and the mob's own size
    // on a skin of any shape (decision diagnose-and-correct-vines-and-ailment-overlay-assuming-a-square-skin).
    vec4 vine = texture(Sampler0, fract(skinCoord * skinTexels / vec2(textureSize(Sampler0, 0))));
    if (vine.a < STRAND_ALPHA) {
        discard;
    }
    float shade = 0.7 + 0.3 * abs(normalize(skinNormal).y);
    vec3 lit = vine.rgb * vineColor.rgb * shade * lightMapColor.rgb;
    fragColor = apply_fog(vec4(lit, 1.0), sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
