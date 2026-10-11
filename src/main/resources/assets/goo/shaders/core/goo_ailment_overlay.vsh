#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

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
// Must match AilmentPattern's order.
const int PATTERN_WOBBLE = 5;
// Weird's wobble (decision weird-bounces-and-softens-harm): the gel stands further off the
// skin than any other overlay and bulges in and out along it, a wave running up the body.
const float WOBBLE_EXTRA_INFLATE = 0.06;
const float WOBBLE_BULGE = 0.07;
const float WOBBLE_WAVES_PER_BLOCK = 5.0;
// GameTime is the fraction of a 24000-tick day: 1200 cycles a day is one every 20 ticks.
const float WOBBLE_CYCLES_PER_DAY = 1200.0;
const float TAU = 6.2831853;

float wobbleOffset(int ailmentPattern) {
    if (ailmentPattern != PATTERN_WOBBLE) {
        return 0.0;
    }
    float wave = sin(GameTime * WOBBLE_CYCLES_PER_DAY * TAU + Position.y * WOBBLE_WAVES_PER_BLOCK);
    return WOBBLE_EXTRA_INFLATE + WOBBLE_BULGE * wave;
}

void main() {
    vec3 encased = Position + normalize(Normal) * OVERLAY_INFLATE + normalize(Normal) * wobbleOffset(UV1.x);
    gl_Position = ProjMat * ModelViewMat * vec4(encased, 1.0);

    sphericalVertexDistance = fog_spherical_distance(encased);
    cylindricalVertexDistance = fog_cylindrical_distance(encased);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    ailmentColor = Color;
    skinCoord = UV0;
    skinNormal = Normal;
    pattern = UV1.x;
}
