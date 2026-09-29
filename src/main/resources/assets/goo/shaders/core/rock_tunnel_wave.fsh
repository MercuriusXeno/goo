#version 330

// Rock goo's tunnel wave (decision elemental-explosion-per-type): round tan
// shock rings pulsing down the tunnel ahead of the breaking, seen through
// the wall. Each ring's band peaks soft in its middle and feathers to
// nothing at both edges, C2A868 with EAD090 crests, its brightness broken
// by dust noise running around the ring. TRANSLUCENT blend, depth test off.

in float progress;
in vec2 ringPos;
in float strength;

out vec4 fragColor;

const vec3 DUST_COLOR = vec3(0.761, 0.659, 0.408);
const vec3 CREST_COLOR = vec3(0.918, 0.816, 0.565);
const float DUST_SCALE = 3.0;
const float RING_OPACITY = 0.85;
const float PI = 3.14159265;

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

void main() {
    float band = pow(sin(ringPos.y * PI), 2.0);
    // The ring's angle on a circle, so the noise joins seamlessly around it.
    float angle = ringPos.x * 2.0 * PI;
    vec2 around = vec2(cos(angle), sin(angle)) * DUST_SCALE + vec2(progress * 4.0);
    float dust = noise(around) * 0.6 + noise(around * 2.3) * 0.4;

    vec3 color = mix(DUST_COLOR, CREST_COLOR, smoothstep(0.4, 0.8, band * dust + 0.3 * band));
    float alpha = RING_OPACITY * band * (0.55 + 0.45 * dust);
    fragColor = vec4(color, clamp(alpha * strength, 0.0, 1.0));
}
