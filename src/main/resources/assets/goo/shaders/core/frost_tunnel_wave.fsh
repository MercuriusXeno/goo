#version 330

// Frost goo's tunnel wave (decision elemental-explosion-per-type): a
// white-blue fog front driving into the wall ahead of the freezing, seen
// through the wall. The fog billows across the plate, drifting as the wave
// travels, with a crisp frost-white band at the plate's rim where the front
// meets the tunnel's walls. TRANSLUCENT blend, depth test off.

in float progress;
in vec2 platePos;
in float strength;

out vec4 fragColor;

const vec3 FOG_COLOR = vec3(0.80, 0.90, 0.96);
const vec3 SHADE_COLOR = vec3(0.62, 0.78, 0.90);
const vec3 EDGE_COLOR = vec3(0.97, 0.99, 1.0);
const float FOG_SCALE = 3.0;
const float FOG_DRIFT = 3.0;
const float FOG_OPACITY = 0.55;
const float EDGE_OPACITY = 0.6;

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
    vec2 centered = platePos * 2.0 - 1.0;
    float billows = billow(centered * FOG_SCALE + vec2(progress * FOG_DRIFT, progress * FOG_DRIFT * 0.6));
    float edge = smoothstep(0.8, 1.0, max(abs(centered.x), abs(centered.y))) * smoothstep(0.3, 0.55, billows);

    vec3 color = mix(SHADE_COLOR, FOG_COLOR, smoothstep(0.3, 0.8, billows));
    color = mix(color, EDGE_COLOR, edge);
    float alpha = FOG_OPACITY * smoothstep(0.25, 0.75, billows) + EDGE_OPACITY * edge;
    fragColor = vec4(color, clamp(alpha * strength, 0.0, 1.0));
}
