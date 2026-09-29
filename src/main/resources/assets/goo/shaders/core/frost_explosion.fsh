#version 330

// Frost goo's burnout explosion (decision elemental-explosion-per-type): a
// fog ring. White-blue fog billows across the disc, drifting outward, dense
// at the leading edge and thinning behind it, with a crisp frost-white band
// where the edge meets air. TRANSLUCENT blend.

in float progress;
in vec2 discPos;
in float fog;

out vec4 fragColor;

const vec3 FOG_COLOR = vec3(0.80, 0.90, 0.96);
const vec3 SHADE_COLOR = vec3(0.62, 0.78, 0.90);
const vec3 EDGE_COLOR = vec3(0.97, 0.99, 1.0);
const float FOG_SCALE = 3.0;
const float FOG_DRIFT = 1.6;
const float FOG_OPACITY = 0.6;
const float EDGE_WIDTH = 0.08;

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
        sum += amplitude * noise(p);
        p *= 2.1;
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
    float billows = billow(discPos * FOG_SCALE - outward * progress * FOG_DRIFT);

    // Dense at the leading edge, thinning toward the marker behind it.
    float density = smoothstep(0.25, 0.75, billows) * mix(0.3, 1.0, radial * radial);
    float offset = (1.0 - radial) / EDGE_WIDTH;
    float edge = exp(-offset * offset) * smoothstep(0.3, 0.55, billows);

    vec3 color = mix(SHADE_COLOR, FOG_COLOR, smoothstep(0.3, 0.8, billows));
    color = mix(color, EDGE_COLOR, edge);
    float alpha = FOG_OPACITY * density + edge;
    fragColor = vec4(color, clamp(alpha * fog, 0.0, 1.0));
}
