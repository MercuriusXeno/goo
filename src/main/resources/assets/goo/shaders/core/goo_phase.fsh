#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// A phased entity's body (decision phase-shares-a-plane-between-the-phased), drawn in place
// of its own: the skin washed most of the way toward its grey, lit as vanilla lights an
// entity, and translucent under the vertex alpha, so a phased thing reads as half there.

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 ghostColor;
in vec4 lightMapColor;
in vec2 skinCoord;
in vec3 skinNormal;

out vec4 fragColor;

// Skin texels below this alpha are holes in the body, as vanilla's cutout reads them.
const float SKIN_CUTOUT = 0.1;
// How far the skin's color washes toward its grey: 0 keeps it, 1 is fully grey.
const float DESATURATION = 0.8;
// A faint cold cast over the grey, so the phase reads apart from plain stone-grey mobs.
const vec3 PHASE_CAST = vec3(0.92, 0.95, 1.08);

void main() {
    vec4 skin = texture(Sampler0, skinCoord);
    if (skin.a < SKIN_CUTOUT) {
        discard;
    }
    float luminance = dot(skin.rgb, vec3(0.299, 0.587, 0.114));
    vec3 washed = mix(skin.rgb, vec3(luminance), DESATURATION) * PHASE_CAST * ghostColor.rgb;
    vec4 lit = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(skinNormal), vec4(washed, ghostColor.a));
    vec4 color = vec4(lit.rgb * lightMapColor.rgb, ghostColor.a) * ColorModulator;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}