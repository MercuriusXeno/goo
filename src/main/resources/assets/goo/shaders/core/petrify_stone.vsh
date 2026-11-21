#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// Petrify's stone over a mob (decision petrify-stone-encasement-and-calcify-map):
// the vanilla entity vertex transform with no lift, so the stone lies flush on
// every face of the model and its layers, drawn at the model's own depth the way
// the enchantment glint is, with no floating and no gap at a cube's edges.
// EncasementLayer hands the share turned to stone, or frozen, in Color's alpha,
// and the mob's skin size in the overlay coordinates.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 stoneColor;
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec2 skinCoord;
layout(location = 5) out vec3 skinNormal;
// The skin's texels across and down: ModelSkinSize packs them in the overlay coordinates, the width in UV1.x
// and the height in UV1.y (decision diagnose-and-correct-petrify-stone-stretching-off-native-texels).
layout(location = 6) flat out vec2 skinTexels;

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
