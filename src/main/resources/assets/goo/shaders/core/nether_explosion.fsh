#version 330

// The black hole's burnout explosion (decision elemental-explosion-per-type):
// an inward rush. The shrinking shell breaks into streaking black specks,
// matter falling into the hole as it opens (decision
// black-hole-build-up-draws-black). TRANSLUCENT blend (SRC_ALPHA,
// ONE_MINUS_SRC_ALPHA), so the black covers what lies behind it; the hole's
// occluder, drawn with depth write, covers the center.

in vec3 viewPos;
in vec3 viewNormal;
in vec3 surfaceDir;
in float progress;
in float nearness;
in float strength;

out vec4 fragColor;

const vec3 STREAK_COLOR = vec3(0.0);
const float SPECK_SCALE = 14.0;
const float SPECK_DRIFT = 3.0;
const float STREAK_STRETCH = 4.0;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash(i), hash(i + vec3(1.0, 0.0, 0.0)), f.x),
            mix(hash(i + vec3(0.0, 1.0, 0.0)), hash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
        mix(mix(hash(i + vec3(0.0, 0.0, 1.0)), hash(i + vec3(1.0, 0.0, 1.0)), f.x),
            mix(hash(i + vec3(0.0, 1.0, 1.0)), hash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y),
        f.z);
}

void main() {
    // Stretch the noise along the view's line through the shell, so the
    // specks smear into streaks pointing at the center.
    vec3 viewDir = normalize(-viewPos);
    float facing = abs(dot(normalize(viewNormal), viewDir));
    vec3 coord = surfaceDir * SPECK_SCALE + vec3(progress * SPECK_DRIFT);
    float specks = noise(coord) * noise(coord * vec3(1.0, STREAK_STRETCH, 1.0));
    float streak = smoothstep(0.25, 0.55, specks);

    float rim = 1.0 - facing;
    float alpha = (streak + 0.25 * rim) * strength;
    fragColor = vec4(STREAK_COLOR, clamp(alpha, 0.0, 1.0));
}
