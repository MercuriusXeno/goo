#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// A phased entity's body (decision phased-things-draw-as-a-ghostly-image): the vanilla
// entity vertex transform, its skin coordinates handed on for the cutout, and the way from
// the vertex back to the eye, which the fragment shader reads for the body's faint outline.

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
layout(location = 6) out vec3 towardEye;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    ghostColor = Color;
    skinCoord = UV0;
    skinNormal = Normal;
    // Entity positions stand relative to the camera, so the eye sits at the origin.
    towardEye = -Position;
}
