#version 330

// Dive's warden senses: the world goes dark and is heard rather than seen.
// Where depth breaks, at the edges of open space, a sculk-teal contour
// draws, brightened by echo rings sweeping out from the diver. Near the
// diver the scene's own colors show faintly, the blocks still told apart;
// past that solid space is a dark sea. What the diver sensed, drawn white
// by the outlines, burns teal at full strength wherever it stands.
// decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks

uniform sampler2D InSampler;
uniform sampler2D InDepthSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform DiveConfig {
    // (seconds, nearReach, far, near): nearReach in blocks where the scene still shows
    vec4 DiveParams;
};

out vec4 fragColor;

const vec3 SCULK = vec3(0.06, 0.78, 0.82);
const vec3 DEEP_SEA = vec3(0.0, 0.025, 0.04);
const float NEAR_SHARE = 0.35;
const float EDGE_GAIN = 14.0;
const float ECHO_SPEED = 12.0;
const float ECHO_SPACING = 24.0;
const float ECHO_WIDTH = 2.5;
const float SENSED_FLOOR = 0.92;

float linearDepth(vec2 uv, float near, float far) {
    float z = texture(InDepthSampler, uv).r * 2.0 - 1.0;
    return 2.0 * near * far / (far + near - z * (far - near));
}

void main() {
    float seconds = DiveParams.x;
    float nearReach = DiveParams.y;
    float far = DiveParams.z;
    float near = DiveParams.w;
    vec2 texel = 1.0 / OutSize;
    vec3 scene = texture(InSampler, texCoord).rgb;

    float center = linearDepth(texCoord, near, far);
    float east = linearDepth(texCoord + vec2(texel.x, 0.0), near, far);
    float west = linearDepth(texCoord - vec2(texel.x, 0.0), near, far);
    float north = linearDepth(texCoord + vec2(0.0, texel.y), near, far);
    float south = linearDepth(texCoord - vec2(0.0, texel.y), near, far);
    float breakAcross = abs(east + west - 2.0 * center) + abs(north + south - 2.0 * center);
    float contour = clamp(breakAcross / max(center, near) * EDGE_GAIN, 0.0, 1.0);

    float sky = step(far * 0.99, center);
    float echoAt = mod(center - seconds * ECHO_SPEED, ECHO_SPACING);
    float echo = 1.0 - smoothstep(0.0, ECHO_WIDTH, min(echoAt, ECHO_SPACING - echoAt));
    float fade = 1.0 - smoothstep(far * 0.25, far * 0.6, center);

    float nearness = 1.0 - smoothstep(nearReach * 0.4, nearReach, center);
    vec3 dark = mix(DEEP_SEA, scene * NEAR_SHARE, nearness);
    vec3 heard = SCULK * contour * (0.45 + 0.55 * echo) * fade * (1.0 - sky);
    vec3 color = dark + heard;

    float sensed = step(SENSED_FLOOR, min(scene.r, min(scene.g, scene.b)));
    fragColor = vec4(mix(color, SCULK * 1.4, sensed), 1.0);
}
