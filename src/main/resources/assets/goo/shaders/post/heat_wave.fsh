#version 330

// Flames' heat wave: the view shimmers like air over a fire inside a small
// box around the point the flame reaches, strongest at its center and gone
// at its edge, with a faint warm cast where it is strongest.
// decision heat-wave-shrinks-to-a-local-shimmer

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform HeatConfig {
    // (seconds, strength, unused, unused): strength 0 passes the view through
    vec4 HeatParams;
    // (centerU, centerV, halfWidthUv, halfHeightUv): the box the shimmer fills
    vec4 HeatRegion;
};

out vec4 fragColor;

const vec3 WARM = vec3(1.0, 0.45, 0.1);
const float WARM_SHARE = 0.08;
// how far into the box, from its center, the shimmer holds full before fading
const float FULL_INSIDE = 0.35;

void main() {
    float seconds = HeatParams.x;
    float strength = HeatParams.y;
    vec2 halfSize = HeatRegion.zw;
    if (strength <= 0.0 || halfSize.x <= 0.0 || halfSize.y <= 0.0) {
        fragColor = texture(InSampler, texCoord);
        return;
    }
    float dist = length((texCoord - HeatRegion.xy) / halfSize);
    if (dist >= 1.0) {
        fragColor = texture(InSampler, texCoord);
        return;
    }
    float mask = 1.0 - smoothstep(FULL_INSIDE, 1.0, dist);

    // two rising bands of waves crossing, so the ripple never reads as one sine
    vec2 ripple = vec2(
        sin(texCoord.y * 90.0 - seconds * 9.0) + 0.5 * sin(texCoord.y * 37.0 - seconds * 5.3),
        0.6 * sin(texCoord.x * 70.0 + seconds * 7.0) + 0.4 * sin((texCoord.x + texCoord.y) * 50.0 - seconds * 11.0));
    vec2 uv = clamp(texCoord + ripple * strength * mask, vec2(0.0), vec2(1.0));
    vec3 scene = texture(InSampler, uv).rgb;
    fragColor = vec4(mix(scene, scene * WARM * 2.0, WARM_SHARE * mask), 1.0);
}
