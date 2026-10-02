#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import "mingle_noise.glsl"

// The goo splat on a struck mob (decision shader-coat-on-every-mob-landing): the
// goo type's fluid texture, under the type's tint, over the half block around the
// struck point and nowhere else. The texture is laid by the fragment's offset from
// the hit point in the model's own space, one tile per block as the goo shows in a
// vat, on the plane the face turns toward, so it rides the body and never smears
// along the mob's skin UVs. The splat's edge is broken by value noise over the same
// offset, so it reads as a splat rather than a disc.

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec3 splatOffset;
flat in vec2 spriteOrigin;

out vec4 fragColor;

// Half the operator's half-block splat: the splat's radius in blocks.
const float SPLAT_RADIUS = 0.25;
// Noise cells per block over the splat's edge.
const float EDGE_CELLS_PER_BLOCK = 12.0;
// How far the noise pushes the edge in or out, as a share of the radius.
const float EDGE_NOISE = 0.45;
// Texels each fluid frame spans; every goo fluid texture is 16 wide.
const float SPRITE_TEXELS = 16.0;

// The splat's reach at a fragment: below 1 inside the noise-broken edge.
float splatReach(vec3 offset) {
    float noise = mingleValueNoise(offset * EDGE_CELLS_PER_BLOCK) - 0.5;
    return length(offset) / SPLAT_RADIUS + EDGE_NOISE * 2.0 * noise;
}

// The offset laid flat on the face's plane: the face's normal in model space is the
// cross of the offset's screen derivatives, and its largest axis is dropped.
vec2 faceCoordinates(vec3 offset) {
    vec3 facing = abs(cross(dFdx(offset), dFdy(offset)));
    if (facing.x >= facing.y && facing.x >= facing.z) {
        return offset.zy;
    }
    if (facing.y >= facing.z) {
        return offset.xz;
    }
    return offset.xy;
}

void main() {
    // Derivatives read before any fragment discards, while the whole quad still runs.
    vec2 tile = fract(faceCoordinates(splatOffset));
    if (splatReach(splatOffset) >= 1.0) {
        discard;
    }
    vec2 spriteSpan = SPRITE_TEXELS / vec2(textureSize(Sampler0, 0));
    // Level 0 alone: the tile wraps inside the sprite, so a mip pick across the wrap would bleed the atlas.
    vec4 goo = textureLod(Sampler0, spriteOrigin + tile * spriteSpan, 0.0);
    vec4 color = goo * vertexColor * ColorModulator * lightMapColor;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
