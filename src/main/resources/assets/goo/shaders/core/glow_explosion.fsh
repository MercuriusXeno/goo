#version 330

// Glow goo's burnout explosion (decisions elemental-explosion-per-type,
// burnouts-are-whole-spheres): an aurora bloom, a whole sphere. Vertical
// aurora bands run up world up, FFFF28 at the bottom shading to FFD700 and
// a pale white crown, sliding slowly around the sphere like the fade walls'
// curtains. LIGHTNING blend (SRC_ALPHA, ONE).

in vec3 surfaceDir;
in float progress;
in float opacity;
in float brightness;

out vec4 fragColor;

const vec3 BASE_COLOR = vec3(1.0, 1.0, 0.157);
const vec3 BODY_COLOR = vec3(1.0, 0.843, 0.0);
const vec3 CROWN_COLOR = vec3(1.0, 0.98, 0.88);
const float BAND_COUNT = 9.0;
const float BAND_SLIDE = 1.5;
const float AURORA_INTENSITY = 1.2;
const float PI = 3.14159265;

void main() {
    vec3 dir = normalize(surfaceDir);
    // Height runs from the sphere's bottom at 0 to its top at 1.
    float height = 0.5 + 0.5 * dir.y;
    float azimuth = atan(dir.z, dir.x);

    float wave = azimuth * BAND_COUNT / (2.0 * PI) + progress * BAND_SLIDE + sin(height * 4.0 + azimuth * 2.0) * 0.3;
    float band = pow(0.5 + 0.5 * sin(wave * 2.0 * PI), 3.0);
    // The curtains hang thick low and thin toward the crown.
    float curtain = band * (1.0 - smoothstep(0.5, 1.0, height)) + 0.15 * (1.0 - height);

    vec3 color = mix(BASE_COLOR, BODY_COLOR, smoothstep(0.0, 0.5, height));
    color = mix(color, CROWN_COLOR, smoothstep(0.5, 1.0, height));
    fragColor = vec4(color * AURORA_INTENSITY, clamp(curtain * brightness, 0.0, 1.0) * opacity);
}
