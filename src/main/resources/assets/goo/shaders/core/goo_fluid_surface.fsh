#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import "mingle_noise.glsl"

// The vanilla entity fragment shader under EMISSIVE and NO_OVERLAY, plus the
// band test of decision noise-mingled-type-textures: each goo type draws its
// own surface, and a fragment survives only on the surface whose band holds
// the mingle noise there, so every fragment shows exactly one type.

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec2 worldXZ;
flat in vec2 band;

out vec4 fragColor;

void main() {
    float mingle = mingleNoise(worldXZ, GameTime);
    if (mingle < band.x || mingle >= band.y) {
        discard;
    }

    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    color *= vertexColor * ColorModulator;

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
