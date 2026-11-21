#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// The vanilla entity vertex shader, lit by the lightmap, plus the dissolve of
// decisions dissolve-shader-on-item and glow-color-from-mingling: the overlay
// coordinates carry the dissolve fraction and the layer's glow color instead of
// the hurt overlay, and the lightmap coordinates' high bytes carry the layer's
// share and index beside the light in their low bytes. The low nibble of each
// light byte, which the lightmap lookup drops, carries half the melt's seed
// (decision diagnose-then-fix-dissolve-repeat).

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec2 texCoord0;
layout(location = 5) flat out float dissolveFraction;
layout(location = 6) flat out vec3 glowColor;
layout(location = 7) flat out float glowShare;
layout(location = 8) flat out float glowLayer;
layout(location = 9) flat out float dissolveSeed;

// Must match DissolveGlow.FRACTION_UNITS: UV1.x carries the fraction dissolved.
const float FRACTION_UNITS = 4096.0;
// Must match DissolveGlow.SHARE_UNITS: UV2.x's high byte carries the layer's share.
const float SHARE_UNITS = 127.0;
// Must match DissolveGlow.SEED_NIBBLE_BITS and SEED_NIBBLE_MASK: UV2.x's low nibble
// carries the seed's low bits and UV2.y's its high bits.
const int SEED_NIBBLE_BITS = 4;
const int SEED_NIBBLE_MASK = 15;

// UV1.y carries the glow color as RGB565, read back unsigned from the short.
vec3 unpackRgb565(int rgb565) {
    uint bits = uint(rgb565) & 0xFFFFu;
    return vec3(float((bits >> 11u) & 31u) / 31.0,
                float((bits >> 5u) & 63u) / 63.0,
                float(bits & 31u) / 31.0);
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    lightMapColor = texelFetch(Sampler2, (UV2 & 0xFF) / 16, 0);
    texCoord0 = UV0;

    dissolveFraction = float(UV1.x) / FRACTION_UNITS;
    glowColor = unpackRgb565(UV1.y);
    glowShare = float(UV2.x >> 8) / SHARE_UNITS;
    glowLayer = float(UV2.y >> 8);
    dissolveSeed = float((UV2.x & SEED_NIBBLE_MASK) | (UV2.y & SEED_NIBBLE_MASK) << SEED_NIBBLE_BITS);
}
