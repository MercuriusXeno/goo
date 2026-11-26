#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

// One ghost of a blink's trail (decision ghost-trail-spans-the-blink): the echoed body
// as a translucent figure in the goo type's color. The skin decides only where the body
// is, through its alpha, and how bright, through its luminance; the color is the goo's,
// so every ghost reads as the type that left it.

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 ghostColor;
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec2 skinCoord;
layout(location = 5) in vec3 skinNormal;

// Under improved transparency the depth bounds and transmittance phases take the alpha alone, and the
// accumulate phase weights the color by the transmittance in front of it
// (decision goo-translucency-draws-through-order-independent-transparency).
#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

// Skin texels below this alpha are holes in the body, as vanilla's cutout reads them.
const float SKIN_CUTOUT = 0.1;
// How much of the ghost's brightness follows the skin's own shading.
const float SKIN_SHADE = 0.45;

void main() {
    vec4 skin = texture(Sampler0, skinCoord);
    if (skin.a < SKIN_CUTOUT) {
        discard;
    }
    float luminance = dot(skin.rgb, vec3(0.299, 0.587, 0.114));
    vec3 tint = ghostColor.rgb * mix(1.0, 0.5 + luminance, SKIN_SHADE);
    vec4 lit = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(skinNormal), vec4(tint, ghostColor.a));
    vec4 color = vec4(lit.rgb * lightMapColor.rgb, ghostColor.a) * ColorModulator;
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
