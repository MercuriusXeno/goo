#version 330

// Broil's heat wave: the view ripples like air over a fire, strongest at
// the crosshair the cone runs along and fading toward the screen's edge,
// with a faint warm cast where it is strongest.
// decision broil-heat-wave-ring-cooks-ahead

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform HeatConfig {
    // (seconds, strength, reachUv, aspect): strength 0 passes the view through
    vec4 HeatParams;
};

out vec4 fragColor;

const vec3 WARM = vec3(1.0, 0.45, 0.1);
const float WARM_SHARE = 0.08;

void main() {
    float seconds = HeatParams.x;
    float strength = HeatParams.y;
    float reach = HeatParams.z;
    float aspect = HeatParams.w;
    if (strength <= 0.0) {
        fragColor = texture(InSampler, texCoord);
        return;
    }
    vec2 fromCenter = texCoord - vec2(0.5);
    float dist = length(vec2(fromCenter.x * aspect, fromCenter.y));
    float mask = 1.0 - smoothstep(0.0, reach, dist);

    // two rising bands of waves crossing, so the ripple never reads as one sine
    vec2 ripple = vec2(
        sin(texCoord.y * 90.0 - seconds * 9.0) + 0.5 * sin(texCoord.y * 37.0 - seconds * 5.3),
        0.6 * sin(texCoord.x * 70.0 + seconds * 7.0) + 0.4 * sin((texCoord.x + texCoord.y) * 50.0 - seconds * 11.0));
    vec2 uv = clamp(texCoord + ripple * strength * mask, vec2(0.0), vec2(1.0));
    vec3 scene = texture(InSampler, uv).rgb;
    fragColor = vec4(mix(scene, scene * WARM * 2.0, WARM_SHARE * mask), 1.0);
}
