#version 330

// Frost goo's burnout explosion (decision elemental-explosion-per-type): a
// rolling freeze fog. Billowing white-blue noise, soft and matte, drifts
// across the fog front; while the fog rolls, a crisp frost-white leading
// edge shows where the front meets air, and once it has rolled the front
// settles into hanging mist that fades. TRANSLUCENT blend.

in vec3 viewPos;
in vec3 viewNormal;
in vec3 surfaceDir;
in float progress;
in float rolled;
in float mist;

out vec4 fragColor;

const vec3 FOG_COLOR = vec3(0.80, 0.90, 0.96);
const vec3 SHADE_COLOR = vec3(0.62, 0.78, 0.90);
const vec3 EDGE_COLOR = vec3(0.97, 0.99, 1.0);
const float FOG_SCALE = 2.5;
const float FOG_DRIFT = 1.8;
const float FOG_OPACITY = 0.45;
const float EDGE_OPACITY = 0.75;

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

float billow(vec3 p) {
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
    vec3 dir = normalize(surfaceDir);
    float fog = billow(dir * FOG_SCALE + vec3(0.0, progress * FOG_DRIFT, progress * FOG_DRIFT * 0.5));

    float facing = abs(dot(normalize(viewNormal), normalize(-viewPos)));
    // The leading edge: a crisp band where the front turns away from the
    // eye, present while the fog is still rolling.
    float front = smoothstep(0.55, 0.85, 1.0 - facing) * (1.0 - rolled * rolled);
    float edge = front * smoothstep(0.35, 0.6, fog);

    vec3 color = mix(SHADE_COLOR, FOG_COLOR, smoothstep(0.3, 0.8, fog));
    color = mix(color, EDGE_COLOR, edge);
    float alpha = FOG_OPACITY * smoothstep(0.25, 0.75, fog) + EDGE_OPACITY * edge;
    fragColor = vec4(color, clamp(alpha * mist, 0.0, 1.0));
}
