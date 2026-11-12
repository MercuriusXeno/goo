#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Petrify's stone over a mob (decision petrify-stone-encasement-and-calcify-map):
// the vanilla entity vertex transform with no lift, so the stone lies flush on
// every face of the model and its layers, drawn at the model's own depth the way
// the enchantment glint is, with no floating and no gap at a cube's edges.
// EncasementLayer hands the share turned to stone, or frozen, in Color's alpha,
// and the mob's skin size in the overlay coordinates.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 stoneColor;
out vec4 lightMapColor;
out vec2 skinCoord;
out vec3 skinNormal;
// The skin's texels across and down: ModelSkinSize packs them in the overlay coordinates, the width in UV1.x
// and the height in UV1.y (decision diagnose-and-correct-petrify-stone-stretching-off-native-texels).
flat out vec2 skinTexels;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    stoneColor = Color;
    skinCoord = UV0;
    skinNormal = Normal;
    skinTexels = vec2(UV1);
}
