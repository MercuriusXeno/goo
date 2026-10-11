#version 330

// Unstable goo's burnout explosion (decision elemental-explosion-per-type):
// a neon fireball whose white-green core fades to a 39FF14 rim, its surface
// crackling and flickering with animated noise, and a shockwave ring
// running out ahead of it. Both fade to nothing as progress reaches 1.
// LIGHTNING blend (SRC_ALPHA, ONE), so alpha scales what is added. A held
// ghost's fireball holds unfaded with no flash and evenly bright, crackling
// on the game clock (decision held-visual-ghosts-the-landing-in-two-passes).

in vec3 viewPos;
in vec3 viewNormal;
in vec3 surfaceDir;
in float progress;
in float opacity;
in float ringFlag;
in float ringRadial;
in float held;
in float clock;

out vec4 fragColor;

const vec3 CORE_COLOR = vec3(0.86, 1.0, 0.74);
const vec3 RIM_COLOR = vec3(0.224, 1.0, 0.078);
const vec3 BRIGHT_COLOR = vec3(0.533, 1.0, 0.267);
const float PI = 3.14159265;
const float CRACKLE_SCALE = 6.0;
const float CRACKLE_SPEED = 24.0;
const float FLICKER_SPEED = 40.0;
const float RING_INTENSITY = 1.4;
const float FLASH_END = 0.35;

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
    float fade = held > 0.5 ? 1.0 : (1.0 - progress) * (1.0 - progress);

    if (ringFlag > 0.5) {
        float band = sin(ringRadial * PI);
        fragColor = vec4(BRIGHT_COLOR * RING_INTENSITY, band * fade * opacity);
        return;
    }

    float facing = abs(dot(normalize(viewNormal), normalize(-viewPos)));
    float rim = 1.0 - facing;
    float crackle = noise(surfaceDir * CRACKLE_SCALE + vec3(clock * CRACKLE_SPEED));
    float flicker = 0.6 + 0.4 * noise(vec3(clock * FLICKER_SPEED, 0.5, 0.5));
    float flash = (1.0 - held) * (1.0 - smoothstep(0.0, FLASH_END, progress)) * facing;

    vec3 color = mix(CORE_COLOR, RIM_COLOR, smoothstep(0.1, 0.9, rim + (1.0 - fade) * 0.5));
    color += flash * vec3(0.3);
    // A held ghost glows evenly, so whatever of it is not behind a wall reads at its pass's
    // full opacity; the landing's fireball keeps its rim-weighted glow.
    float strength = held > 0.5 ? 1.0 : mix(0.35 + 0.65 * rim, 1.0, flash);
    float alpha = fade * flicker * strength * (0.55 + 0.45 * crackle);
    fragColor = vec4(color, clamp(alpha, 0.0, 1.0) * opacity);
}
