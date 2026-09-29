#version 330

// Blaze goo's tunnel wave (decision elemental-explosion-per-type): round
// orange heat rings pulsing down the tunnel ahead of the smelting, seen
// through the wall. Each ring's band glows white-yellow at its middle,
// FF8E28 toward its edges and feathers to nothing, shimmering as heat
// wavers around it. TRANSLUCENT blend, depth test off.

in float progress;
in vec2 ringPos;
in float strength;

out vec4 fragColor;

const vec3 CORE_COLOR = vec3(1.0, 0.95, 0.7);
const vec3 BODY_COLOR = vec3(1.0, 0.557, 0.157);
const vec3 EDGE_COLOR = vec3(0.6, 0.08, 0.02);
const float SHIMMER_WAVES = 9.0;
const float SHIMMER_SPEED = 14.0;
const float RING_OPACITY = 0.9;
const float PI = 3.14159265;

void main() {
    float band = sin(ringPos.y * PI);
    float shimmer = 0.5 + 0.5 * sin(ringPos.x * SHIMMER_WAVES * 2.0 * PI + progress * SHIMMER_SPEED);
    float heat = band * band;

    vec3 color = mix(EDGE_COLOR, BODY_COLOR, smoothstep(0.0, 0.5, heat));
    color = mix(color, CORE_COLOR, smoothstep(0.6, 1.0, heat));
    float alpha = RING_OPACITY * heat * (0.6 + 0.4 * shimmer);
    fragColor = vec4(color, clamp(alpha * strength, 0.0, 1.0));
}
