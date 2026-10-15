#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import "mingle_noise.glsl"

// The vanilla entity fragment shader under EMISSIVE and NO_OVERLAY, blending
// every goo type a surface holds in one pass (decision one-pass-blends-every-type):
// each row of the frame's mingle table draws its type's sprite over the rows
// before it at its mingle opacity, the over-blend the lifted layers drew, so
// the types form goo that crossfade at their seams on one flat surface.

uniform sampler2D Sampler0;
uniform sampler2D MingleTable;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 mingleWorldPos;
flat in ivec2 mingleRows;

out vec4 fragColor;

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

    fragColor = apply_fog(vec4(premultiplied / alpha, alpha), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
