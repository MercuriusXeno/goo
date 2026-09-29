#version 330

// Frost goo's tunnel wave (decision elemental-explosion-per-type): round
// white-blue fog rings pulsing down the tunnel ahead of the freezing, seen
// through the wall. Each ring's band is soft fog billowing around it, with a
// crisp frost-white line along its outer edge, feathered to nothing inside.
// TRANSLUCENT blend, depth test off.

in float progress;
in vec2 ringPos;
in float strength;

out vec4 fragColor;

const vec3 FOG_COLOR = vec3(0.80, 0.90, 0.96);
const vec3 SHADE_COLOR = vec3(0.62, 0.78, 0.90);
const vec3 EDGE_COLOR = vec3(0.97, 0.99, 1.0);
const float FOG_SCALE = 3.0;
const float FOG_OPACITY = 0.7;
const float EDGE_OPACITY = 0.6;
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
    float band = sin(ringPos.y * PI);
    float angle = ringPos.x * 2.0 * PI;
    vec2 around = vec2(cos(angle), sin(angle)) * FOG_SCALE + vec2(progress * 3.0, ringPos.y);
    float billows = noise(around) * 0.6 + noise(around * 2.1) * 0.4;
    float edgeOffset = (1.0 - ringPos.y) / 0.12;
    float edge = exp(-edgeOffset * edgeOffset) * smoothstep(0.3, 0.55, billows);

    vec3 color = mix(SHADE_COLOR, FOG_COLOR, smoothstep(0.3, 0.8, billows));
    color = mix(color, EDGE_COLOR, edge);
    float alpha = FOG_OPACITY * band * smoothstep(0.2, 0.7, billows) + EDGE_OPACITY * edge;
    fragColor = vec4(color, clamp(alpha * strength, 0.0, 1.0));
}
