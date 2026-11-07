#version 330

// Sculker's warden senses: the world dims with its distance from the diver and is heard rather than seen.
// Where depth breaks, at the edges of open space, a sculk-teal contour
// draws, brightened by echo rings sweeping out from the diver. The world
// shows at 60% of its own look within 3 blocks, 30% at 8, 10% at 16 and
// sinks into inky black by 24,
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
// the world's brightness by its distance from the diver, a share of how it looks with no effect:
// 60% within 3 blocks, 30% at 8, 10% at 16, black by 24
const float BRIGHT_NEAR = 0.6;
const float BRIGHT_ROOM = 0.3;
const float BRIGHT_NEXT = 0.1;
const float NEAR_REACH_BLOCKS = 3.0;
const float ROOM_REACH = 8.0;
const float NEXT_REACH = 16.0;
const float DARK_REACH = 24.0;
const float EDGE_GAIN = 14.0;
const float ECHO_SPEED = 12.0;
const float ECHO_SPACING = 24.0;
const float ECHO_WIDTH = 2.5;
// an echo draws sculk teal: green and blue well above red, which the dark lets through bright
const float TEAL_CHROMA = 0.12;
// a faint echo, its teal laid over the dark, still counts as an echo from here
const float TEAL_FLOOR = 0.02;
const float ECHO_GAIN = 1.1;

float brightnessAt(float distance) {
    if (distance <= NEAR_REACH_BLOCKS) {
        return BRIGHT_NEAR;
    }
    if (distance <= ROOM_REACH) {
        return mix(BRIGHT_NEAR, BRIGHT_ROOM, (distance - NEAR_REACH_BLOCKS) / (ROOM_REACH - NEAR_REACH_BLOCKS));
    }
    if (distance <= NEXT_REACH) {
        return mix(BRIGHT_ROOM, BRIGHT_NEXT, (distance - ROOM_REACH) / (NEXT_REACH - ROOM_REACH));
    }
    return mix(BRIGHT_NEXT, 0.0, clamp((distance - NEXT_REACH) / (DARK_REACH - NEXT_REACH), 0.0, 1.0));
}

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

    vec3 dark = scene * brightnessAt(center) * (1.0 - sky);
    vec3 heard = SCULK * contour * (0.45 + 0.55 * echo) * fade * (1.0 - sky);
    vec3 color = dark + heard;

    float tealness = min(scene.g, scene.b) - scene.r;
    float echoed = smoothstep(TEAL_FLOOR, TEAL_CHROMA, tealness);
    vec3 sensed = mix(color, scene * ECHO_GAIN, echoed);

    fragColor = vec4(mix(scene, sensed, presence), 1.0);
}
