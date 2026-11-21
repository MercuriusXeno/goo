#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// One ghost of a blink's trail (decision ghost-trail-spans-the-blink): the vanilla
// entity vertex transform over the echoed body, its skin coordinates handed on for the
// cutout, the goo type's color and the ghost's fade in Color.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 ghostColor;
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec2 skinCoord;
layout(location = 5) out vec3 skinNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    ghostColor = Color;
    skinCoord = UV0;
    skinNormal = Normal;
}
