#version 330

// Rock goo's burnout explosion (decision elemental-explosion-per-type): a
// dust shock disc. Billowing dust in C2A868 with EAD090 highlights drifts
// outward, thinning at the edge and over time, and a thin bright sonic
// ring crosses the disc once. TRANSLUCENT blend, so the dust hides what is
// behind it.

in float progress;
in vec2 discPos;

out vec4 fragColor;

const vec3 DUST_COLOR = vec3(0.761, 0.659, 0.408);
const vec3 HIGHLIGHT_COLOR = vec3(0.918, 0.816, 0.565);
const vec3 SONIC_COLOR = vec3(1.0, 0.96, 0.85);
// Must match RockExplosionVisual.SONIC_SPAN.
const float SONIC_SPAN = 0.5;
const float SONIC_WIDTH = 0.04;
const float DUST_SCALE = 3.0;
const float DUST_DRIFT = 2.0;
const float DUST_OPACITY = 0.85;

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
    vec2 outward = radial > 0.0 ? discPos / radial : vec2(0.0);
    float dust = billow(discPos * DUST_SCALE - outward * progress * DUST_DRIFT);

    float edgeFade = 1.0 - smoothstep(0.6, 1.0, radial);
    float timeFade = 1.0 - smoothstep(0.4, 1.0, progress);
    float alpha = dust * edgeFade * timeFade * DUST_OPACITY;
    vec3 color = mix(DUST_COLOR, HIGHLIGHT_COLOR, smoothstep(0.3, 0.8, dust));

    float ringPos = clamp(progress / SONIC_SPAN, 0.0, 1.0);
    float offset = (radial - ringPos) / SONIC_WIDTH;
    float band = exp(-offset * offset) * (1.0 - ringPos);
    color = mix(color, SONIC_COLOR, band);
    alpha = max(alpha, band);

    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
