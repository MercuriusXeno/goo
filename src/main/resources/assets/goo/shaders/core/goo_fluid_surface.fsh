#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>
#include <goo:mingle_noise.glsl>
#include <minecraft:oit.glsl>

// The vanilla entity fragment shader under EMISSIVE and NO_OVERLAY, blending
// every goo type a surface holds in one pass (decision one-pass-blends-every-type):
// each row of the frame's mingle table draws its type's sprite over the rows
// before it at its mingle opacity, an over-blend that leaves each type its
// volume ratio, so the types form goo that crossfade at their seams on one flat surface.

uniform sampler2D Sampler0;
uniform sampler2D MingleTable;

// Under improved transparency the depth bounds and transmittance phases take the blend's alpha
// alone, and the accumulate phase weights its color by the transmittance in front of it
// (decision goo-translucency-draws-through-order-independent-transparency).
#ifndef OIT_ALPHA_ONLY
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in vec3 mingleWorldPos;
layout(location = 5) flat in ivec2 mingleRows;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

// Must match MingleTable: the column of each field in a row.
const int SPRITE_U0 = 0;
const int SPRITE_V0 = 1;
const int SPRITE_U1 = 2;
const int SPRITE_V1 = 3;
const int TINT = 4;
const int SHARE = 5;
const int SEED = 6;

// A texel's four bytes as the int MingleTable wrote, red lowest.
uint mingleTableBits(int row, int column) {
    uvec4 bytes = uvec4(round(texelFetch(MingleTable, ivec2(column, row), 0) * 255.0));
    return bytes.r | bytes.g << 8u | bytes.b << 16u | bytes.a << 24u;
}

float mingleTableFloat(int row, int column) {
    return uintBitsToFloat(mingleTableBits(row, column));
}

void main() {
    // Each texel takes one opacity, so a seam steps by texel while the drift still sways the pattern
    // (decision mingle-noise-snaps-to-texels).
    vec3 faceTexel = mingleFaceTexel(mingleWorldPos);
    // Read before the loop, since a row skipped by its opacity leaves the derivatives undefined inside it.
    vec2 spriteDx = dFdx(texCoord0);
    vec2 spriteDy = dFdy(texCoord0);

    vec3 premultiplied = vec3(0.0);
    float alpha = 0.0;
    for (int row = mingleRows.x; row < mingleRows.x + mingleRows.y; row++) {
        float share = mingleTableFloat(row, SHARE);
        float seed = float(int(mingleTableBits(row, SEED)));
        float opacity = mingleOpacity(faceTexel, GameTime, share, seed);
        if (opacity <= 0.0) {
            continue;
        }
        vec2 spriteMin = vec2(mingleTableFloat(row, SPRITE_U0), mingleTableFloat(row, SPRITE_V0));
        vec2 spriteSpan = vec2(mingleTableFloat(row, SPRITE_U1), mingleTableFloat(row, SPRITE_V1)) - spriteMin;
        vec4 color = textureGrad(Sampler0, spriteMin + texCoord0 * spriteSpan, spriteDx * spriteSpan,
            spriteDy * spriteSpan);
#ifdef ALPHA_CUTOUT
        if (color.a < ALPHA_CUTOUT) {
            continue;
        }
#endif
        color *= texelFetch(MingleTable, ivec2(TINT, row), 0) * vertexColor * ColorModulator;
        float over = color.a * opacity;
        premultiplied = color.rgb * over + premultiplied * (1.0 - over);
        alpha = over + alpha * (1.0 - over);
    }
    if (alpha <= 0.0) {
        discard;
    }

#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, alpha);
#else
    vec4 color = vec4(premultiplied / alpha, alpha);
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
#endif
}
