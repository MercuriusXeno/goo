#version 330

#moj_import <minecraft:globals.glsl>

// Petrify's fog (decision petrify-stone-encasement-and-calcify-map): a medusa's
// gaze miasma the player emits. Each cross-section of the cone, from the glove
// out, holds billowing grey-green dust that churns as it drifts, and bright
// bands of it wash forward from the glove in undulating waves, thinning at the
// cone's rim and at its reach. TRANSLUCENT blend, the sections stacking into a
// volume.

in float along;
in vec2 discPos;

out vec4 fragColor;

const float TAU = 6.2831853;
// GameTime is the fraction of a 24000-tick day.
const float TICKS_PER_DAY = 24000.0;
const vec3 MIASMA_COLOR = vec3(0.50, 0.56, 0.46);
const vec3 DUST_COLOR = vec3(0.74, 0.70, 0.60);
const float DUST_SCALE = 2.2;
const float CHURN_PER_TICK = 0.03;
// Waves along the cone and how fast they wash forward, in cone lengths a tick.
const float WAVE_COUNT = 2.5;
const float WAVE_SPEED = 0.035;
// Each section's own opacity; the stacked sections build the fog.
const float SECTION_OPACITY = 0.32;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float billow(vec2 p) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int octave = 0; octave < 4; octave++) {
        sum += amplitude * abs(noise(p) * 2.0 - 1.0);
        p *= 2.0;
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
    vec2 churn = vec2(ticks * CHURN_PER_TICK, -ticks * CHURN_PER_TICK * 0.7) + vec2(along * 7.0);
    float dust = billow(discPos * DUST_SCALE + churn);

    float wave = 0.5 + 0.5 * sin((along * WAVE_COUNT - ticks * WAVE_SPEED) * TAU);
    float rimFade = 1.0 - smoothstep(0.55, 1.0, radial);
    // The fog pours from the glove: it thickens over the first sliver of the cone rather than starting at range.
    float nearFade = smoothstep(0.0, 0.04, along);
    float farFade = 1.0 - smoothstep(0.8, 1.0, along);
    float alpha = dust * (0.3 + 0.7 * wave) * rimFade * nearFade * farFade * SECTION_OPACITY;
    vec3 color = mix(MIASMA_COLOR, DUST_COLOR, smoothstep(0.3, 0.8, dust) * (0.5 + 0.5 * wave));

    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
