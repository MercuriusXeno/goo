#version 330

// Blaze goo's tunnel wave (decision elemental-explosion-per-type): an orange
// heat shimmer driving into the wall ahead of the smelting, seen through
// the wall. Wavering bands of heat distortion ripple across the plate,
// FF8E28 glowing to a hot white-yellow at the plate's center and deep red
// at its rim. TRANSLUCENT blend, depth test off.

in float progress;
in vec2 platePos;
in float strength;

out vec4 fragColor;

const vec3 CORE_COLOR = vec3(1.0, 0.95, 0.7);
const vec3 BODY_COLOR = vec3(1.0, 0.557, 0.157);
const vec3 RIM_COLOR = vec3(0.6, 0.08, 0.02);
const float SHIMMER_BANDS = 6.0;
const float SHIMMER_SPEED = 14.0;
const float BASE_OPACITY = 0.3;
const float SHIMMER_OPACITY = 0.35;
const float PI = 3.14159265;

void main() {
    vec2 centered = platePos * 2.0 - 1.0;
    float radial = min(1.0, length(centered));
    // Heat bands waver sideways as they rise, the way air shimmers over a fire.
    float waver = sin(centered.y * 7.0 + progress * SHIMMER_SPEED) * 0.12;
    float shimmer = 0.5 + 0.5 * sin((centered.x + waver) * SHIMMER_BANDS * PI + progress * SHIMMER_SPEED);

    vec3 color = mix(CORE_COLOR, BODY_COLOR, smoothstep(0.0, 0.5, radial));
    color = mix(color, RIM_COLOR, smoothstep(0.6, 1.0, radial));
    float alpha = BASE_OPACITY * (1.0 - 0.5 * radial) + SHIMMER_OPACITY * shimmer;
    fragColor = vec4(color, clamp(alpha * strength, 0.0, 1.0));
}
