#version 330

#moj_import <minecraft:globals.glsl>

// Chronosphere's veil (decision chronosphere-hastes-players-slows-mobs): a
// glassy golden sphere. Its rim glows where the surface turns away from the
// eye, so the sphere reads as round from outside and its boundary stands
// out from inside; slow bands of light sweep down it like sand through a
// glass, and twelve faint meridians mark it like a clock's hours. The middle
// stays clear enough to see the slowed mobs through. TRANSLUCENT blend.

in vec3 sphereDir;
in vec3 viewNormal;
in vec3 viewPos;
in float strength;

out vec4 fragColor;

const float TICKS_PER_DAY = 24000.0;
const vec3 STASIS_GOLD = vec3(1.0, 0.831, 0.278);
const vec3 HIGHLIGHT = vec3(1.0, 0.957, 0.788);
const float TAU = 6.2831853;
// How clear the veil's middle stays, and how bright its rim glows.
const float FACE_ALPHA = 0.06;
const float RIM_ALPHA = 0.75;
const float RIM_POWER = 2.5;
// The bands sweeping down the veil: how many stand at once, and how fast they fall.
const float BANDS = 7.0;
const float BAND_FALL_PER_TICK = 0.015;
const float BAND_ALPHA = 0.18;
// The clock-hour meridians.
const float MERIDIANS = 12.0;
const float MERIDIAN_WIDTH = 0.025;
const float MERIDIAN_ALPHA = 0.22;

void main() {
    vec3 dir = normalize(sphereDir);
    float facing = abs(dot(normalize(viewNormal), normalize(-viewPos)));
    float rim = pow(1.0 - facing, RIM_POWER);
    float ticks = GameTime * TICKS_PER_DAY;
    // A band's height on the sphere, wavering with the angle around it so the fall reads as liquid.
    float around = atan(dir.z, dir.x);
    float height = dir.y + 0.06 * sin(around * 3.0 + ticks * 0.05);
    float band = smoothstep(0.75, 1.0, 0.5 + 0.5 * sin(TAU * (height * BANDS * 0.5 + ticks * BAND_FALL_PER_TICK)));
    float hour = fract(around / TAU * MERIDIANS);
    float meridian = (1.0 - smoothstep(0.0, MERIDIAN_WIDTH, min(hour, 1.0 - hour))) * (1.0 - abs(dir.y));
    float alpha = FACE_ALPHA + rim * RIM_ALPHA + band * BAND_ALPHA + meridian * MERIDIAN_ALPHA;
    vec3 color = mix(STASIS_GOLD, HIGHLIGHT, clamp(rim * 0.8 + band * 0.6, 0.0, 1.0));
    fragColor = vec4(color, clamp(alpha, 0.0, 1.0) * strength);
}
