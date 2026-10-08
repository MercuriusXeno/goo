#version 330

// Unmake's ripple rings (decision unmake-waves-dissolve-by-crucible-cost):
// thin unstable-green rings, like ripples on water, leaving the glove and
// widening down the cone, each brightest along its middle and soft at both
// edges, fading in as it leaves the glove and out as it reaches the cone's
// end. Each segment flickers like static, a few dropping out altogether.
// TRANSLUCENT blend.

in float along;
in float across;
in float flicker;

out vec4 fragColor;

const float PI = 3.14159265;
const vec3 CORE_COLOR = vec3(0.78, 1.0, 0.62);
const vec3 EDGE_COLOR = vec3(0.224, 1.0, 0.078);
const float RING_OPACITY = 0.85;
// Segments flickering below this drop out, the gaps static leaves.
const float DROPOUT = 0.18;

void main() {
    float band = sin(across * PI);
    float nearFade = smoothstep(0.0, 0.08, along);
    float farFade = 1.0 - smoothstep(0.7, 1.0, along);
    float staticShare = flicker < DROPOUT ? 0.0 : 0.35 + 0.65 * flicker;
    float alpha = band * band * nearFade * farFade * staticShare * RING_OPACITY;
    vec3 color = mix(EDGE_COLOR, CORE_COLOR, band * band);
    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
