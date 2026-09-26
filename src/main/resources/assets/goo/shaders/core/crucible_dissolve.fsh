#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import "mingle_noise.glsl"

// The dissolve of decision dissolve-shader-on-item: the mingle noise field over
// world position is thresholded so the share of the item left standing is the
// share not yet dissolved; fragments below the threshold are discarded, and a
// thin band just above it glows.
//
// The glow's color follows decision glow-color-from-mingling: the item draws
// once per goo type, largest first. Layer 0 draws the body and its glow; every
// later layer draws only the band fragments its own mingle field picks, over
// the layers before it, so each fragment glows in one type's color, the types
// covering the band in their volume ratio and shifting as the fields drift.

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;
in vec3 dissolveWorldPos;
flat in float dissolveFraction;
flat in vec3 glowColor;
flat in float glowShare;
flat in float glowLayer;

out vec4 fragColor;

// Noise cells per block: an item spans a fraction of a block, so the field runs
// finer than the surface's blobs to break it into several flecks.
const float DISSOLVE_CELLS_PER_BLOCK = 12.0;
// Width of the glowing band above the threshold, in field units.
const float GLOW_BAND = 0.06;
// The band's brightness at the threshold, above one so it reads as emissive.
const float GLOW_STRENGTH = 1.6;
// Scales world position into the type pick's field, so a few type patches run
// along the rim of one item rather than one surface blob covering it.
const float PICK_SCALE = 2.4;
// A later layer takes a fragment where its mingle opacity passes one half.
const float PICK_OPACITY = 0.5;

void main() {
    float threshold = mingleThreshold(1.0 - dissolveFraction);
    float field = mingleField(dissolveWorldPos * DISSOLVE_CELLS_PER_BLOCK);
    if (field < threshold) {
        discard;
    }
    float edge = dissolveFraction > 0.0 ? 1.0 - smoothstep(threshold, threshold + GLOW_BAND, field) : 0.0;
    if (glowLayer > 0.5) {
        if (edge <= 0.0) {
            discard;
        }
        float picked = mingleOpacity(dissolveWorldPos * PICK_SCALE, GameTime, glowShare, glowLayer);
        if (picked < PICK_OPACITY) {
            discard;
        }
    }

    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    color *= vertexColor * ColorModulator;
    color *= lightMapColor;

    // Premultiplied: the band blends the body toward this layer's glow alone.
    vec3 lit = mix(color.rgb * color.a, glowColor * GLOW_STRENGTH, edge);
    float alpha = mix(color.a, 1.0, edge);

    fragColor = apply_fog(vec4(lit, alpha), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
