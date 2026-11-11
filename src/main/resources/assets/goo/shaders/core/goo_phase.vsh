#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// A phased entity's body (decision phased-things-draw-as-a-ghostly-image): the vanilla
// entity vertex transform, its skin coordinates handed on for the cutout, and the way from
// the vertex back to the eye, which the fragment shader reads for the body's faint outline.

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
out vec3 towardEye;

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
