#version 330

#moj_import <minecraft:globals.glsl>

// Bore's vortex (decision bore-vortex-with-a-worldspace-shake): a spiralling
// dust vortex down the tunnel. Each cross-section holds dust arms wound about
// the look, the arms turning over time and twisting further the deeper the
// section stands, so together they read as one corkscrew of rock dust boring
// forward; thin at the middle and at the rim, near the glove and at the
// reach. TRANSLUCENT blend, the sections stacking into a column.

in float along;
in vec2 discPos;

out vec4 fragColor;

const float TAU = 6.2831853;
// GameTime is the fraction of a 24000-tick day.
const float TICKS_PER_DAY = 24000.0;
const vec3 DUST_COLOR = vec3(0.761, 0.659, 0.408);
const vec3 HIGHLIGHT_COLOR = vec3(0.918, 0.816, 0.565);
// Spiral arms about the look, how fast they turn and how far they twist down the tunnel.
const float ARMS = 3.0;
const float SPIN_PER_TICK = 0.35;
const float TWIST = 6.0;
// How tightly each arm winds outward from the middle.
const float WIND = 4.0;
const float GRAIN_SCALE = 3.5;
// Each section's own opacity: thin enough that the stacked vortex shows the tunnel through it.
const float SECTION_OPACITY = 0.1;

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
    float radial = length(discPos);
    if (radial > 1.0) {
        discard;
    }
    float ticks = GameTime * TICKS_PER_DAY;
    float angle = atan(discPos.y, discPos.x);
    float turn = ticks * SPIN_PER_TICK + along * TWIST;
    float arm = 0.5 + 0.5 * cos(ARMS * angle - radial * WIND - turn);
    arm = smoothstep(0.35, 1.0, arm);
    vec2 swirled = vec2(radial * GRAIN_SCALE, angle * ARMS - turn);
    float grain = noise(swirled * vec2(1.0, 1.3)) * 0.6 + noise(swirled * 2.7) * 0.4;

    float body = smoothstep(0.08, 0.3, radial) * (1.0 - smoothstep(0.7, 1.0, radial));
    float nearFade = smoothstep(0.0, 0.15, along);
    float farFade = 1.0 - smoothstep(0.85, 1.0, along);
    float alpha = arm * grain * body * nearFade * farFade * SECTION_OPACITY;
    vec3 color = mix(DUST_COLOR, HIGHLIGHT_COLOR, grain * arm);

    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
