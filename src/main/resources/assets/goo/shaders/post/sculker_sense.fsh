#version 330

// Sculker's warden senses: the world dims to a cave and is heard rather than seen.
// Where depth breaks, at the edges of open space, a sculk-teal contour
// draws, brightened by echo rings sweeping out from the diver. Near the
// diver the scene's own colors show faintly, the blocks still told apart;
// the near world keeps that clarity, then sinks into inky black with distance,
// and sculk tendrils curl in from the screen's edge. The whole vision
// eases in as the brew starts and out as it ends. Solid space is a dim sea. What the diver's echoes lit, the faces
// and outlines drawn sculk teal, shows bright wherever it stands, as faint
// as its fade with distance drew it.
// decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
// decision sculker-sight-persists-where-echoes-have-touched

uniform sampler2D InSampler;
uniform sampler2D InDepthSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform SculkerConfig {
    // (seconds, nearReach, far, near): nearReach in blocks where the scene still shows
    vec4 SculkerParams;
    // (presence, unused, dimReach, unused): presence 0 passes the view through,
    // dimReach in blocks where the world has all but gone dark
    vec4 SculkerLook;
};

out vec4 fragColor;

const vec3 SCULK = vec3(0.06, 0.78, 0.82);
const vec3 DEEP_SEA = vec3(0.03, 0.07, 0.09);
// the scene's own colors kept everywhere, so the world reads as a dim cave rather than black
const float DIM_SHARE = 0.22;
// how far out, in blocks, the near world keeps its clarity before it starts sinking into black
const float CLEAR_REACH = 8.0;
const float NEAR_SHARE = 0.6;
const float EDGE_GAIN = 14.0;
const float ECHO_SPEED = 12.0;
const float ECHO_SPACING = 24.0;
const float ECHO_WIDTH = 2.5;
// an echo draws sculk teal: green and blue well above red, which the dark lets through bright
const float TEAL_CHROMA = 0.22;
const float ECHO_GAIN = 1.1;

float linearDepth(vec2 uv, float near, float far) {
    float z = texture(InDepthSampler, uv).r * 2.0 - 1.0;
    return 2.0 * near * far / (far + near - z * (far - near));
}

void main() {
    float seconds = SculkerParams.x;
    float nearReach = SculkerParams.y;
    float far = SculkerParams.z;
    float near = SculkerParams.w;
    float presence = SculkerLook.x;
    float dimReach = SculkerLook.z;
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
    float fade = 1.0 - smoothstep(dimReach * 0.5, dimReach * 1.5, center);
    float distanceLight = 1.0 - smoothstep(CLEAR_REACH, dimReach, center);

    float nearness = 1.0 - smoothstep(nearReach * 0.4, nearReach, center);
    vec3 dark = mix(DEEP_SEA + scene * DIM_SHARE, scene * NEAR_SHARE, nearness) * distanceLight;
    vec3 heard = SCULK * contour * (0.45 + 0.55 * echo) * fade * (1.0 - sky);
    vec3 color = dark + heard;

    float tealness = min(scene.g, scene.b) - scene.r;
    float echoed = smoothstep(TEAL_CHROMA * 0.5, TEAL_CHROMA, tealness);
    vec3 sensed = mix(color, scene * ECHO_GAIN, echoed);

    fragColor = vec4(mix(scene, sensed, presence), 1.0);
}
