#version 330

// Rock goo's tunnel wave (decision elemental-explosion-per-type): a tan
// pressure ripple driving into the wall ahead of the breaking, seen through
// the wall. Concentric ripples run out from the plate's center, C2A868 with
// EAD090 crests, brightest at the plate's rim where the pressure meets the
// tunnel's walls. TRANSLUCENT blend, depth test off.

in float progress;
in vec2 platePos;
in float strength;

out vec4 fragColor;

const vec3 DUST_COLOR = vec3(0.761, 0.659, 0.408);
const vec3 CREST_COLOR = vec3(0.918, 0.816, 0.565);
const float RIPPLES = 4.0;
const float RIPPLE_SPEED = 10.0;
const float BASE_OPACITY = 0.2;
const float CREST_OPACITY = 0.45;
const float RIM_OPACITY = 0.5;
const float PI = 3.14159265;

void main() {
    vec2 centered = platePos * 2.0 - 1.0;
    float radial = length(centered);
    float ripple = 0.5 + 0.5 * cos((radial * RIPPLES - progress * RIPPLE_SPEED) * 2.0 * PI);
    float crest = pow(ripple, 4.0);
    // The rim: how near the fragment sits to the plate's square edge.
    float rim = smoothstep(0.8, 1.0, max(abs(centered.x), abs(centered.y)));

    vec3 color = mix(DUST_COLOR, CREST_COLOR, crest);
    float alpha = BASE_OPACITY + CREST_OPACITY * crest + RIM_OPACITY * rim;
    fragColor = vec4(color, clamp(alpha * strength, 0.0, 1.0));
}
