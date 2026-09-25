#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import "mingle_noise.glsl"

// The vanilla entity fragment shader under EMISSIVE and NO_OVERLAY, plus the
// layering of decision noise-mingled-type-textures: each goo type draws its
// own surface, layer 0 whole and every later layer at its mingle opacity, so
// the types form blobs that crossfade at their seams.

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 mingleWorldPos;
flat in vec2 layerShare;

out vec4 fragColor;

void main() {
    float opacity = mingleOpacity(mingleWorldPos, GameTime, layerShare.x, layerShare.y);
    if (opacity <= 0.0) {
        discard;
    }

    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    color *= vertexColor * ColorModulator;
    color.a *= opacity;

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
