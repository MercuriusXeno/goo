#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import "mingle_noise.glsl"

// The dissolve of decision dissolve-shader-on-item: the mingle noise field over
// world position is thresholded so the share of the item left standing is the
// share not yet dissolved; fragments below the threshold are discarded, and a
// thin band just above it glows in the goo's color, added over the scene.

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;
in vec3 dissolveWorldPos;
flat in float dissolveFraction;
flat in vec3 glowColor;

out vec4 fragColor;

// Noise cells per block: an item spans a fraction of a block, so the field runs
// finer than the surface's blobs to break it into several flecks.
const float DISSOLVE_CELLS_PER_BLOCK = 12.0;
// Width of the glowing band above the threshold, in field units.
const float GLOW_BAND = 0.06;
// The band's brightness at the threshold, above one so it reads as emissive.
const float GLOW_STRENGTH = 1.6;

void main() {
    float threshold = mingleThreshold(1.0 - dissolveFraction);
    float field = mingleField(dissolveWorldPos * DISSOLVE_CELLS_PER_BLOCK);
    if (field < threshold) {
        discard;
    }

    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    color *= vertexColor * ColorModulator;
    color *= lightMapColor;

    float edge = dissolveFraction > 0.0 ? 1.0 - smoothstep(threshold, threshold + GLOW_BAND, field) : 0.0;
    // Premultiplied: the body keeps its share of alpha, the glow adds with none.
    float bodyAlpha = color.a * (1.0 - edge);
    vec3 lit = color.rgb * bodyAlpha + glowColor * edge * GLOW_STRENGTH;

    fragColor = apply_fog(vec4(lit, bodyAlpha), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
