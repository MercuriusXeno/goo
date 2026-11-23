#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

// The glow shell under improved transparency: vanilla's position_color fragment shader, with each fragment retuned
// toward the light the shell adds with improved transparency off, since in the OIT phase it draws as a see-through
// layer with coverage, which dims what it covers rather than brightening it. Its coverage rises, so a faint layer
// still shows and a dense one nearly covers, and its color whitens as it grows dense, as stacked additive light
// whitens toward its middle (decision additive-glows-draw-as-translucent-layers-in-the-oit-phase).

layout(location = 0) in vec4 vertexColor;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

// How far a fully dense layer whitens.
const float WHITEN = 0.5;

vec4 seeThroughGlow(vec4 glow) {
    float coverage = glow.a * (2.0 - glow.a);
    return vec4(mix(glow.rgb, vec3(1.0), WHITEN * glow.a), coverage);
}

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
