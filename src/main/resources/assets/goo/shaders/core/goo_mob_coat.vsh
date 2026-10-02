#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// The goo splat on a struck mob (decision shader-coat-on-every-mob-landing): the
// vanilla entity vertex transform, lifted off the skin along its normal, with the
// lighting left to the fragment shader, which shades the splat as a raised blob.
// SplatVertices re-encodes each vertex: UV0 holds the fluid sprite's atlas origin,
// and the vertex's offset from the hit point, in the model's own space, rides UV1
// (x, y) and the high bytes of UV2 (z), in OFFSET_UNITS per block. The color's
// alpha carries the splat's remaining reach, 1 whole and 0 dissolved.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec3 gooTint;
out vec4 lightMapColor;
out vec3 viewPosition;
out vec3 skinNormal;
out vec3 splatOffset;
flat out vec2 spriteOrigin;
flat out float splatKeep;

// Must match SplatVertices.OFFSET_UNITS_PER_BLOCK.
const float OFFSET_UNITS = 1024.0;
// Blocks the splat stands off the skin.
const float COAT_INFLATE = 0.03;

void main() {
    vec3 coated = Position + normalize(Normal) * COAT_INFLATE;
    gl_Position = ProjMat * ModelViewMat * vec4(coated, 1.0);

    sphericalVertexDistance = fog_spherical_distance(coated);
    cylindricalVertexDistance = fog_cylindrical_distance(coated);
    lightMapColor = texelFetch(Sampler2, (UV2 & 0xFF) / 16, 0);
    viewPosition = coated;
    skinNormal = Normal;

    // The tint's alpha carries the reach the splat keeps as it dissolves
    // (decision splat-holds-then-dissolves-dripping); the goo itself draws opaque.
    gooTint = Color.rgb;
    splatKeep = Color.a;

    int offsetZ = ((UV2.y >> 8) << 8) | ((UV2.x >> 8) & 0xFF);
    splatOffset = vec3(float(UV1.x), float(UV1.y), float(offsetZ)) / OFFSET_UNITS;
    spriteOrigin = UV0;
}
