#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// One ghost of a blink's trail (decision ghost-trail-spans-the-blink): the echoed body
// as a translucent figure in the goo type's color. The skin decides only where the body
// is, through its alpha, and how bright, through its luminance; the color is the goo's,
// so every ghost reads as the type that left it.

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
// How much of the ghost's brightness follows the skin's own shading.
const float SKIN_SHADE = 0.45;

void main() {
    vec4 skin = texture(Sampler0, skinCoord);
    if (skin.a < SKIN_CUTOUT) {
        discard;
    }
    float luminance = dot(skin.rgb, vec3(0.299, 0.587, 0.114));
    vec3 tint = ghostColor.rgb * mix(1.0, 0.5 + luminance, SKIN_SHADE);
    vec4 lit = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(skinNormal), vec4(tint, ghostColor.a));
    vec4 color = vec4(lit.rgb * lightMapColor.rgb, ghostColor.a) * ColorModulator;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
