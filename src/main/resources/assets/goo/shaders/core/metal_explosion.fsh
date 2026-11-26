#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:oit.glsl>

// Metal goo's burnout explosion (decision elemental-explosion-per-type): an
// urchin of chrome spikes. Each cone shades from C0C0C0 to E8E8E8 by how
// squarely it faces the eye, a specular band sweeps down the spikes as the
// show runs, and each tip carries a white glint. TRANSLUCENT blend, the
// spikes drawn solid at the vertex alpha's opacity, whole for a landing.

layout(location = 0) in vec3 viewPos;
layout(location = 1) in vec3 viewNormal;
layout(location = 2) in float progress;
layout(location = 3) in float along;
layout(location = 4) in float opacity;

// Under improved transparency the depth bounds and transmittance phases take the alpha alone, and the
// accumulate phase weights the color by the transmittance in front of it
// (decision goo-translucency-draws-through-order-independent-transparency).
#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

const vec3 SHADOW_COLOR = vec3(0.753, 0.753, 0.753);
const vec3 LIT_COLOR = vec3(0.91, 0.91, 0.91);
const vec3 GLINT_COLOR = vec3(1.0, 1.0, 1.0);
const float BAND_SWEEPS = 3.0;
const float BAND_WIDTH = 0.12;
const float TIP_GLINT_START = 0.85;

void main() {
    float facing = abs(dot(normalize(viewNormal), normalize(-viewPos)));
    vec3 color = mix(SHADOW_COLOR, LIT_COLOR, facing);

    // The band runs base to tip BAND_SWEEPS times over the show.
    float bandPos = fract(progress * BAND_SWEEPS);
    float offset = (along - bandPos) / BAND_WIDTH;
    float band = exp(-offset * offset);
    float tipGlint = smoothstep(TIP_GLINT_START, 1.0, along);
    color = mix(color, GLINT_COLOR, clamp(band * 0.8 + tipGlint, 0.0, 1.0));

    vec4 drawn = vec4(color, opacity);
#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, drawn.a);
#elif defined(OIT_ACCUMULATE)
    fragColor = sampleColorForAccumulation(drawn);
#else
    fragColor = drawn;
#endif
}
