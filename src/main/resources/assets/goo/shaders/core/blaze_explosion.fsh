#version 330

// Blaze goo's burnout explosion (decision elemental-explosion-per-type): a
// flame bloom. Noise scrolls outward along the placed face's normal so
// tongues of flame lick out of the dome, graded from a white-yellow base
// through FF8E28 to a deep red tip, burning off as the strength the
// visual packs falls to nothing. The
// half of the sphere behind the face is discarded. LIGHTNING blend
// (SRC_ALPHA, ONE), so the flame lights what it covers.

in vec3 surfaceDir;
in float progress;
in float opacity;
in float strength;
flat in vec3 faceUp;

out vec4 fragColor;

const vec3 BASE_COLOR = vec3(1.0, 0.95, 0.7);
const vec3 BODY_COLOR = vec3(1.0, 0.557, 0.157);
const vec3 TIP_COLOR = vec3(0.6, 0.08, 0.02);
const float TONGUE_SCALE = 4.0;
const float TONGUE_SPEED = 6.0;
const float FLAME_INTENSITY = 1.3;

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

void main() {
    float height = dot(normalize(surfaceDir), faceUp);
    if (height < 0.0) {
        discard;
    }
    vec3 flow = surfaceDir * TONGUE_SCALE - faceUp * progress * TONGUE_SPEED;
    float tongues = noise(flow) * 0.65 + noise(flow * 2.3) * 0.35;

    // Flames thin toward the dome's crown: a tongue survives higher up
    // only where the noise runs strong.
    float flame = smoothstep(height * 0.8, height * 0.8 + 0.25, tongues);

    vec3 color = mix(BASE_COLOR, BODY_COLOR, smoothstep(0.0, 0.4, height));
    color = mix(color, TIP_COLOR, smoothstep(0.4, 1.0, height));
    fragColor = vec4(color * FLAME_INTENSITY, clamp(flame * strength, 0.0, 1.0) * opacity);
}
