#version 330

// Sculker's warden senses: the world dims with its distance from the
// diver, showing at 60% of its own look within 3 blocks, 30% at 5, 10% at
// 6.5 and black by 8, the sky black with it. The whole vision eases in as
// the brew starts and out as it ends. The glass field and what the diver's
// echoes lit draw over the world once this has dimmed it, so only what a
// real echo touched lights up past the near world.
// decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
// decision sculker-sight-persists-where-echoes-have-touched

uniform sampler2D InSampler;
uniform sampler2D InDepthSampler;

in vec2 texCoord;

layout(std140) uniform SculkerConfig {
    // (unused, unused, far, near): the camera's far and near planes
    vec4 SculkerParams;
    // (presence, unused, unused, unused): presence 0 passes the view through
    vec4 SculkerLook;
};

out vec4 fragColor;

// the world's brightness by its distance from the diver, a share of how it looks with no effect:
// 60% within 3 blocks, 30% at 5, 10% at 6.5, black by 8
const float BRIGHT_NEAR = 0.6;
const float BRIGHT_ROOM = 0.3;
const float BRIGHT_NEXT = 0.1;
const float NEAR_REACH_BLOCKS = 3.0;
const float ROOM_REACH = 5.0;
const float NEXT_REACH = 6.5;
const float DARK_REACH = 8.0;

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
    float far = SculkerParams.z;
    float near = SculkerParams.w;
    float presence = SculkerLook.x;
    vec3 scene = texture(InSampler, texCoord).rgb;
    float distance = linearDepth(texCoord, near, far);
    vec3 dimmed = scene * brightnessAt(distance);
    fragColor = vec4(mix(scene, dimmed, presence), 1.0);
}
