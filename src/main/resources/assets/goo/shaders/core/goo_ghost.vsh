#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// One ghost of a blink's trail (decision ghost-trail-spans-the-blink): the vanilla
// entity vertex transform over the echoed body, its skin coordinates handed on for the
// cutout, the goo type's color and the ghost's fade in Color.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 ghostColor;
out vec4 lightMapColor;
out vec2 skinCoord;
out vec3 skinNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    ghostColor = Color;
    skinCoord = UV0;
    skinNormal = Normal;
}
