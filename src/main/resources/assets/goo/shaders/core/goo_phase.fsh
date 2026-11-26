#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

// A phased entity's body (decision phased-things-draw-as-a-ghostly-image), drawn in place
// of its own: the skin washed most of the way toward its grey, lit as vanilla lights an
// entity, translucent under the vertex alpha, and brightening to a faint pale outline where
// the body turns away from the eye, so a phased thing reads as between dimensions.

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 ghostColor;
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec2 skinCoord;
layout(location = 5) in vec3 skinNormal;
layout(location = 6) in vec3 towardEye;

// Under improved transparency the depth bounds and transmittance phases take the alpha alone, and the
// accumulate phase weights the color by the transmittance in front of it
// (decision goo-translucency-draws-through-order-independent-transparency).
#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

// Skin texels below this alpha are holes in the body, as vanilla's cutout reads them.
const float SKIN_CUTOUT = 0.1;
// How far the skin's color washes toward its grey: 0 keeps it, 1 is fully grey.
const float DESATURATION = 0.8;
// A faint cold cast over the grey, so the phase reads apart from plain stone-grey mobs.
const vec3 PHASE_CAST = vec3(0.92, 0.95, 1.08);
// The outline's pale cold color.
const vec3 OUTLINE_COLOR = vec3(0.80, 0.88, 1.0);
// How sharply the outline gathers at the silhouette: higher keeps it thinner.
const float OUTLINE_SHARPNESS = 3.0;
// How far the outline lifts the body's alpha toward opaque at the silhouette.
const float OUTLINE_ALPHA = 0.45;

void main() {
    vec4 skin = texture(Sampler0, skinCoord);
    if (skin.a < SKIN_CUTOUT) {
        discard;
    }
    float luminance = dot(skin.rgb, vec3(0.299, 0.587, 0.114));
    vec3 washed = mix(skin.rgb, vec3(luminance), DESATURATION) * PHASE_CAST * ghostColor.rgb;
    vec4 lit = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(skinNormal), vec4(washed, ghostColor.a));
    float facing = abs(dot(normalize(skinNormal), normalize(towardEye)));
    float outline = pow(1.0 - facing, OUTLINE_SHARPNESS);
    vec3 body = mix(lit.rgb * lightMapColor.rgb, OUTLINE_COLOR, outline);
    float alpha = mix(ghostColor.a, 1.0, outline * OUTLINE_ALPHA);
    vec4 color = vec4(body, alpha) * ColorModulator;
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
