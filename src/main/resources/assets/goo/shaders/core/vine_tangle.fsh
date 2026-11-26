#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
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

// Under improved transparency the depth bounds and transmittance phases take the alpha alone, and the
// accumulate phase weights the color by the transmittance in front of it
// (decision goo-translucency-draws-through-order-independent-transparency).
#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

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
    vec4 color = vec4(lit, 1.0);
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
#endif
}
