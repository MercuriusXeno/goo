#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:oit.glsl>

layout(location = 0) in vec4 vertColor;

// Under improved transparency the depth bounds and transmittance phases take the alpha alone, and the accumulate
// phase weights the color by the transmittance in front of it
// (decision goo-translucency-draws-through-order-independent-transparency).
#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

void main() {
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, vertColor.a);
#elif defined(OIT_ACCUMULATE)
    fragColor = sampleColorForAccumulation(vertColor);
#else
    fragColor = vertColor;
#endif
}
