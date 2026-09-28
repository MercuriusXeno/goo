#version 330

// Nether goo's burnout explosion (decision elemental-explosion-per-type):
// an inward rush. The shrinking shell breaks into streaking specks that
// brighten from 8B0000 through B32828 to white-hot as they near the center,
// matter falling into the hole as it opens. LIGHTNING blend (SRC_ALPHA,
// ONE); the hole's occluder, drawn with depth write, covers the center.

in vec3 viewPos;
in vec3 viewNormal;
in vec3 surfaceDir;
in float progress;
in float nearness;
in float remaining;

out vec4 fragColor;

const vec3 DEEP_COLOR = vec3(0.545, 0.0, 0.0);
const vec3 BRIGHT_COLOR = vec3(0.702, 0.157, 0.157);
const vec3 HOT_COLOR = vec3(1.0, 0.92, 0.85);
const float SPECK_SCALE = 14.0;
const float SPECK_DRIFT = 3.0;
const float STREAK_STRETCH = 4.0;
const float RUSH_INTENSITY = 1.6;

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

    vec3 color = mix(DEEP_COLOR, BRIGHT_COLOR, smoothstep(0.0, 0.6, nearness));
    color = mix(color, HOT_COLOR, smoothstep(0.6, 1.0, nearness));
    float rim = 1.0 - facing;
    float alpha = (streak + 0.25 * rim) * remaining;
    fragColor = vec4(color * RUSH_INTENSITY, clamp(alpha, 0.0, 1.0));
}
