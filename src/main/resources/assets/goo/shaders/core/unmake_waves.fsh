#version 330

#moj_import <minecraft:globals.glsl>

// Unmake's destabilizing waves (decision unmake-waves-dissolve-by-crucible-cost):
// goo colored bands pulse out of the glove and sweep down the cone, each a
// bright unstable-green front trailing off behind it, the gaps between them
// clear, their edges wobbling as the matter they cross destabilizes. The bands
// thin at the cone's rim and fade out at its reach. TRANSLUCENT blend, the
// sections stacking into each band's volume.

in float along;
in vec2 discPos;

out vec4 fragColor;

const float TAU = 6.2831853;
// GameTime is the fraction of a 24000-tick day.
const float TICKS_PER_DAY = 24000.0;
const vec3 FRONT_COLOR = vec3(0.70, 1.0, 0.55);
const vec3 TRAIL_COLOR = vec3(0.224, 1.0, 0.078);
// Bands along the cone and how fast they sweep down it, in cone lengths a tick.
const float BAND_COUNT = 3.0;
const float BAND_SPEED = 0.05;
// How sharply a band's front rises; the trail behind it decays more slowly.
const float FRONT_SHARPNESS = 10.0;
const float WOBBLE = 0.06;
// Each section's own opacity; the stacked sections build the band.
const float SECTION_OPACITY = 0.22;

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
    float wobble = (noise(discPos * 3.0 + vec2(ticks * 0.08)) - 0.5) * WOBBLE;
    // Phase of this point within its band: 0 at the front, rising to 1 just ahead of the next front.
    float phase = fract((along + wobble) * BAND_COUNT - ticks * BAND_SPEED);
    float band = exp(-phase * FRONT_SHARPNESS * 0.3) * smoothstep(1.0, 0.92, phase);
    float front = exp(-phase * FRONT_SHARPNESS);

    float rimFade = 1.0 - smoothstep(0.7, 1.0, radial);
    float nearFade = smoothstep(0.0, 0.04, along);
    float farFade = 1.0 - smoothstep(0.85, 1.0, along);
    float alpha = band * rimFade * nearFade * farFade * SECTION_OPACITY;
    vec3 color = mix(TRAIL_COLOR, FRONT_COLOR, front);

    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
