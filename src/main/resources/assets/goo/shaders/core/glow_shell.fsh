#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
#include <goo:see_through_glow.glsl>

// The glow shell: vanilla's position_color fragment shader, its color retuned toward the shell's additive look in
// the OIT phase alone (decision additive-glows-draw-as-translucent-layers-in-the-oit-phase).

layout(location = 0) in vec4 vertexColor;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

void main() {
    vec4 color = vertexColor;
    if (color.a == 0.0) {
        discard;
    }
    color = seeThroughGlow(color * ColorModulator);

#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#elif defined(OIT_ACCUMULATE)
    fragColor = sampleColorForAccumulation(color);
#else
    fragColor = color;
#endif
}
