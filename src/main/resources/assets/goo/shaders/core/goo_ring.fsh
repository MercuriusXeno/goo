#version 330

// Goo's swirling ring particle (decision goo-swirl-ring-particle), made from
// the sonic ring of rock_explosion.fsh: a bright ring sweeps out across the
// disc once over the particle's life, swirled by rotating billow noise, a
// thin dust wake trailing inside it, tinted by the goo type's theme color
// and fading to nothing by the end. TRANSLUCENT blend.

in float progress;
in vec2 discPos;
in vec3 tint;

out vec4 fragColor;

const float RING_WIDTH = 0.08;
const float ARMS = 5.0;
const float TWIST = 6.0;
const float SPIN = 4.0;
const float DUST_SCALE = 3.0;
const float WAKE_LENGTH = 0.45;
const float WAKE_OPACITY = 0.55;
const float HIGHLIGHT_GAIN = 1.25;

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

vec2 rotate(vec2 p, float turn) {
    float c = cos(turn);
    float s = sin(turn);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

void main() {
    float radial = length(discPos);
    if (radial > 1.0) {
        discard;
    }
    float inverse = 1.0 - progress;
    float front = 1.0 - inverse * inverse * inverse;
    float turn = progress * SPIN + radial * TWIST;
    float angle = atan(discPos.y, discPos.x);
    float arms = 0.5 + 0.5 * sin(ARMS * angle - turn);
    float dust = billow(rotate(discPos, turn) * DUST_SCALE);

    float offset = (radial - front) / RING_WIDTH;
    float band = exp(-offset * offset) * mix(0.6, 1.0, arms);
    float behind = step(radial, front) * smoothstep(front - WAKE_LENGTH, front, radial);
    float wake = behind * dust * arms * WAKE_OPACITY;

    float fade = 1.0 - smoothstep(0.55, 1.0, progress);
    vec3 highlight = min(tint * HIGHLIGHT_GAIN, vec3(1.0));
    vec3 color = mix(tint, highlight, clamp(band + dust * 0.5, 0.0, 1.0));
    float alpha = max(band, wake) * fade;

    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
