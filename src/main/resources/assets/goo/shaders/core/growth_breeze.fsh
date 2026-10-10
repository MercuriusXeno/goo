#version 330

#moj_import <minecraft:globals.glsl>

// Growth's breeze (decision growth-breeze-ticks-plants): ragged wisps of
// glowing leaf green, torn and warped like wind, fill each cross-section of
// the cone and flow outward from the glove, and ripples run out along the cone
// toward the target, each crest bent and uneven across its front and rippling
// the wisps it passes. The breeze's strength never swells or fades as a whole.
// LIGHTNING blend, so the stacked sections glow.
//
// Every pattern is noise over the section's position and a third axis that
// runs along the cone and back with time, so it churns in place and flows
// outward rather than sliding across the section.

in float along;
in vec2 discPos;

out vec4 fragColor;

// GameTime is the fraction of a 24000-tick day.
const float TICKS_PER_DAY = 24000.0;
const float TAU = 6.2831853;
const vec3 DEEP_GREEN = vec3(0.12, 0.55, 0.16);
const vec3 LEAF_GREEN = vec3(0.30, 0.85, 0.28);
const vec3 BRIGHT_GREEN = vec3(0.75, 1.00, 0.55);
// Wisps across a section, and how slowly they change along the look, so each streaks out like wind.
const float WISP_SCALE = 2.4;
const float WISP_STREAK = 1.3;
// How fast the wind flows outward, in noise cells a tick, and how hard it tears the wisps.
const float FLOW_PER_TICK = 0.03;
const float WARP = 1.2;
// Ripple crests along the cone, how fast they run out in cone lengths a tick, how far each crest bends,
// how sharp a crest stands, and how hard it ripples the wisps it passes.
const float RIPPLES_ALONG = 2.2;
const float RIPPLE_SPEED = 0.03;
const float RIPPLE_BEND = 0.45;
const float CREST_SHARPNESS = 5.0;
const float RIPPLE_PUSH = 0.35;
// Each section's own glow; the stacked sections build the breeze.
const float SECTION_GLOW = 0.2;

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
    float radial = length(discPos);
    if (radial > 1.0) {
        discard;
    }
    float ticks = GameTime * TICKS_PER_DAY;

    // Ripples run out along the cone; noise over the section and time bends each crest, so no two match.
    float bend = RIPPLE_BEND * (noise(vec3(discPos * 1.4, ticks * 0.02)) - 0.5)
            + 0.25 * (noise(vec3(discPos * 3.1 + 7.0, ticks * 0.035)) - 0.5);
    float phase = (along * RIPPLES_ALONG - ticks * RIPPLE_SPEED + bend) * TAU;
    float crest = pow(0.5 + 0.5 * sin(phase), CREST_SHARPNESS);
    // A crest runs strong in places and thin in others across its front.
    float crestStrength = 0.4 + 0.9 * noise(vec3(discPos * 2.2, floor(phase / TAU) * 1.7));
    float ripple = crest * crestStrength;

    // The wind tears the wisps, and a passing ripple pushes them outward from the axis.
    float flow = along * WISP_STREAK - ticks * FLOW_PER_TICK;
    vec2 pushed = discPos * (1.0 + RIPPLE_PUSH * ripple);
    vec2 tear = vec2(noise(vec3(pushed * 0.8, ticks * 0.013)),
                     noise(vec3(pushed * 0.8 + 5.2, ticks * 0.017))) - 0.5;
    float field = turbulence(vec3(pushed * WISP_SCALE, flow) + vec3(tear * WARP, 0.0));
    float threshold = 0.35 + 0.25 * noise(vec3(discPos * 1.3, along * 3.0 - ticks * 0.03));
    float wisp = smoothstep(threshold, threshold + 0.35, field);

    float rimFade = 1.0 - smoothstep(0.45 + 0.2 * noise(vec3(discPos * 2.0, ticks * 0.04)), 1.0, radial);
    float nearFade = smoothstep(0.0, 0.05, along);
    float farFade = 1.0 - smoothstep(0.7, 1.0, along);
    float glow = (wisp * (0.8 + 0.6 * ripple) + 0.35 * ripple) * rimFade * nearFade * farFade * SECTION_GLOW;
    vec3 color = mix(DEEP_GREEN, LEAF_GREEN, wisp);
    color = mix(color, BRIGHT_GREEN, clamp(ripple, 0.0, 1.0));

    fragColor = vec4(color, clamp(glow, 0.0, 1.0));
}
