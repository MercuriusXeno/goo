#version 330

#moj_import <minecraft:globals.glsl>

// Reap's swell (decision reap-breeze-harvests-and-replants): a whole sphere of
// Growth's breeze, torn green wisps over its surface churning as it swells out
// to Reap's radius, thinning away once it has reached it. Every pattern is noise
// over the surface's direction and time, so nothing slides or bands. LIGHTNING
// blend, so the swell glows.

in vec3 surfaceDir;
in float strength;

out vec4 fragColor;

// GameTime is the fraction of a 24000-tick day.
const float TICKS_PER_DAY = 24000.0;
const vec3 DEEP_GREEN = vec3(0.12, 0.55, 0.16);
const vec3 LEAF_GREEN = vec3(0.30, 0.85, 0.28);
const vec3 BRIGHT_GREEN = vec3(0.75, 1.00, 0.55);
const float WISP_SCALE = 3.2;
const float CHURN_PER_TICK = 0.06;
const float WARP = 1.1;
const float SURFACE_GLOW = 0.55;

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float near = mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x),
                     mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y);
    float far = mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
                    mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y);
    return mix(near, far, f.z);
}

// Turbulence: folded octaves, sharp creases where the wind tears.
float turbulence(vec3 p) {
    float sum = 0.0;
    float amplitude = 0.55;
    for (int octave = 0; octave < 5; octave++) {
        sum += amplitude * abs(noise(p) * 2.0 - 1.0);
        p = p * 2.03 + vec3(1.7, -3.1, 0.6);
        amplitude *= 0.5;
    }
    return sum;
}

void main() {
    vec3 dir = normalize(surfaceDir);
    float ticks = GameTime * TICKS_PER_DAY;
    vec3 churn = vec3(0.0, 0.0, ticks * CHURN_PER_TICK);
    vec3 tear = vec3(noise(dir * 1.6 + churn * 0.4), noise(dir * 1.6 + 5.2 - churn * 0.3), 0.0) - 0.5;
    float field = turbulence(dir * WISP_SCALE + churn + tear * WARP);
    float threshold = 0.3 + 0.25 * noise(dir * 2.0 + vec3(ticks * 0.03));
    float wisp = smoothstep(threshold, threshold + 0.35, field);

    vec3 color = mix(DEEP_GREEN, LEAF_GREEN, wisp);
    color = mix(color, BRIGHT_GREEN, wisp * wisp * 0.6);
    float glow = (0.15 + wisp * 0.85) * SURFACE_GLOW * strength;
    fragColor = vec4(color, clamp(glow, 0.0, 1.0));
}
