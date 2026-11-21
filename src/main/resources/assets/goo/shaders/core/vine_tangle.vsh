#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// Vines tangled over a rooted mob (decision vines-unpack-root-and-thorn):
// the vanilla entity vertex transform with no lift, so the vines lie flush on
// every face of the model, drawn at the model's own depth the way Petrify's
// stone is. TangleLayer hands the foliage tint in Color's rgb, the
// share of the mob the vines cover in its alpha, and the mob's skin size in
// the overlay coordinates.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vineColor;
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec2 skinCoord;
layout(location = 5) out vec3 skinNormal;
// The skin's texels across and down: ModelSkinSize packs them in the overlay coordinates, the width in UV1.x
// and the height in UV1.y (decision diagnose-and-correct-vines-and-ailment-overlay-assuming-a-square-skin).
layout(location = 6) flat out vec2 skinTexels;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    vineColor = Color;
    skinCoord = UV0;
    skinNormal = Normal;
    skinTexels = vec2(UV1);
}
