#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// A status ailment's overlay on a mob or player (decision ailment-overlay-shader-per-ailment):
// the vanilla entity vertex transform, the model lifted a hair off its skin so the
// overlay encases it. AilmentOverlayLayer hands the ailment's color in Color, its
// strength in Color's alpha, and its pattern's ordinal in the overlay coordinate's U.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 ailmentColor;
out vec4 lightMapColor;
out vec2 skinCoord;
out vec3 skinNormal;
flat out int pattern;

// Blocks the overlay stands off the skin.
const float OVERLAY_INFLATE = 0.02;

void main() {
    vec3 encased = Position + normalize(Normal) * OVERLAY_INFLATE;
    gl_Position = ProjMat * ModelViewMat * vec4(encased, 1.0);

    sphericalVertexDistance = fog_spherical_distance(encased);
    cylindricalVertexDistance = fog_cylindrical_distance(encased);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    ailmentColor = Color;
    skinCoord = UV0;
    skinNormal = Normal;
    pattern = UV1.x;
}
