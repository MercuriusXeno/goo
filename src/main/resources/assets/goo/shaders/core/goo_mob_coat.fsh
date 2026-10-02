#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import "mingle_noise.glsl"

// The goo splat on a struck mob (decision shader-coat-on-every-mob-landing): the
// goo type's animated fluid texture, under the type's tint, over the half block
// around the struck point and nowhere else, shaded as a raised blob thickest at
// the hit point. The texture is laid by the fragment's offset from the hit point
// in the model's own space, TILES_PER_BLOCK tiles to a block, on the plane the
// face turns toward, so it rides the body and never smears along the mob's skin
// UVs. The splat's edge is broken by value noise over the same offset, so it
// reads as a splat rather than a disc.

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec3 gooTint;
in vec4 lightMapColor;
in vec3 viewPosition;
in vec3 skinNormal;
in vec3 splatOffset;
flat in vec2 spriteOrigin;
flat in float splatKeep;

out vec4 fragColor;

// Half the operator's half-block splat: the splat's radius in blocks.
const float SPLAT_RADIUS = 0.25;
// Noise cells per block over the splat's edge.
const float EDGE_CELLS_PER_BLOCK = 12.0;
// How far the noise pushes the edge in or out, as a share of the radius.
const float EDGE_NOISE = 0.45;
// Texels each fluid frame spans; every goo fluid texture is 16 wide.
const float SPRITE_TEXELS = 16.0;
// Fluid tiles to a block: the splat shows two whole tiles across.
const float TILES_PER_BLOCK = 4.0;
// The blob's height at the hit point, in blocks; it falls off as the square of
// the distance to nothing at the radius.
const float BLOB_HEIGHT = 0.1;
// How much darker the blob's thin edge reads than its thick middle.
const float THIN_EDGE_SHADE = 0.8;

// The offset over the radius: 0 at the hit point, 1 at the splat's plain edge.
float splatDistance(vec3 offset) {
    return length(offset) / SPLAT_RADIUS;
}

// The splat's reach at a fragment: below 1 inside the noise-broken edge.
float splatReach(vec3 offset) {
    float noise = mingleValueNoise(offset * EDGE_CELLS_PER_BLOCK) - 0.5;
    return splatDistance(offset) + EDGE_NOISE * 2.0 * noise;
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

// The blob's surface normal: the skin's normal tilted outward from the hit point
// by the dome's slope, h = BLOB_HEIGHT (1 - d^2), its gradient along the skin read
// from the screen derivatives of the view position and of the distance d.
vec3 blobNormal(vec3 normal, float reachFromHit) {
    vec3 across = dFdx(viewPosition);
    vec3 down = dFdy(viewPosition);
    float area = dot(normal, cross(across, down));
    if (abs(area) < 1e-12) {
        return normal;
    }
    vec3 distanceGradient = (dFdx(reachFromHit) * cross(down, normal) + dFdy(reachFromHit) * cross(normal, across)) / area;
    return normalize(normal + 2.0 * BLOB_HEIGHT * reachFromHit * distanceGradient);
}

void main() {
    // Derivatives read before any fragment discards, while the whole quad still runs.
    vec2 tile = fract(faceCoordinates(splatOffset) * TILES_PER_BLOCK);
    float fromHit = splatDistance(splatOffset);
    vec3 normal = blobNormal(normalize(skinNormal), fromHit);
    // The keep falls from 1 to 0 as the splat dissolves (decision splat-holds-then-dissolves-dripping):
    // the noise holes open at the edge first and close in on the hit point, the edge plain goo throughout.
    // At 0 the threshold sits below the lowest reach the noise can pull the hit point to, so nothing is left.
    if (splatReach(splatOffset) >= mix(-EDGE_NOISE, 1.0, splatKeep)) {
        discard;
    }
    vec2 spriteSpan = SPRITE_TEXELS / vec2(textureSize(Sampler0, 0));
    // Level 0 alone: the tile wraps inside the sprite, so a mip pick across the wrap would bleed the atlas.
    vec4 goo = textureLod(Sampler0, spriteOrigin + tile * spriteSpan, 0.0);
    float thickness = mix(THIN_EDGE_SHADE, 1.0, 1.0 - clamp(fromHit * fromHit, 0.0, 1.0));
    vec4 litTint = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, vec4(gooTint * thickness, 1.0));
    vec4 color = goo * litTint * ColorModulator * lightMapColor;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
