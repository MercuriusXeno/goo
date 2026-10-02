#version 330

// Glow goo's burnout explosion (decision elemental-explosion-per-type): an
// aurora bloom. Vertical aurora bands run up the dome, FFFF28 at the base
// shading to FFD700 and a pale white crown, sliding slowly around it like
// the fade walls' curtains. The half of the sphere behind the face is
// discarded. LIGHTNING blend (SRC_ALPHA, ONE).

in vec3 surfaceDir;
in float progress;
in float opacity;
in float brightness;
flat in vec3 faceUp;

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
    float height = dot(dir, faceUp);
    if (height < 0.0) {
        discard;
    }
    // The angle about the face's normal, measured in a basis square to it,
    // places each band around the dome.
    vec3 side = abs(faceUp.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 across = normalize(cross(faceUp, side));
    vec3 around = cross(faceUp, across);
    float azimuth = atan(dot(dir, around), dot(dir, across));

    float wave = azimuth * BAND_COUNT / (2.0 * PI) + progress * BAND_SLIDE + sin(height * 4.0 + azimuth * 2.0) * 0.3;
    float band = pow(0.5 + 0.5 * sin(wave * 2.0 * PI), 3.0);
    // The curtains hang thick at the base and thin toward the crown.
    float curtain = band * (1.0 - smoothstep(0.5, 1.0, height)) + 0.15 * (1.0 - height);

    vec3 color = mix(BASE_COLOR, BODY_COLOR, smoothstep(0.0, 0.5, height));
    color = mix(color, CROWN_COLOR, smoothstep(0.5, 1.0, height));
    fragColor = vec4(color * AURORA_INTENSITY, clamp(curtain * brightness, 0.0, 1.0) * opacity);
}
