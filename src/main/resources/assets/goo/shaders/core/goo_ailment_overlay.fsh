#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import "mingle_noise.glsl"

// A status ailment's overlay over a whole model (decision ailment-overlay-shader-per-ailment):
// one field per pattern, laid over the model's skin coordinates so it rides the body,
// tinted by the ailment's color. The facets and the shimmer draw unlit; the stone, the frost
// and the gel encase the model and take its light. The ailments that mark a mob rather than
// coat it wear the pulsing outline instead (decision ailment-glisten-becomes-a-pulsing-outline).

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 ailmentColor;
in vec4 lightMapColor;
in vec2 skinCoord;
in vec3 skinNormal;
flat in int pattern;

out vec4 fragColor;

// Must match AilmentPattern's order.
const int PATTERN_FACETS = 0;
const int PATTERN_SHIMMER = 1;
const int PATTERN_STONE = 2;
const int PATTERN_FROST = 3;
const int PATTERN_WOBBLE = 4;
// Bubbles drifting in Weird's gel, and how fast they drift.
// GameTime is the fraction of a 24000-tick day: 300 cycles a day is one every 80 ticks.
const float GEL_CELLS = 14.0;
const float GEL_CYCLES_PER_DAY = 300.0;

const float TAU = 6.2831853;
// Diamond facets across the skin's coordinates.
const float FACET_CELLS = 24.0;
const float FACET_EDGE = 0.08;
const float FACET_FLASH_CYCLES_PER_DAY = 600.0;
// Noise cells of the ender speckle across the skin's coordinates, and how fast it swirls;
// a cell spans four skin texels (decision mingle-noise-snaps-to-texels).
const float SHIMMER_CELLS = 16.0;
const float SHIMMER_CYCLES_PER_DAY = 900.0;
// Noise cells of the stone grain and of the frost crystals, each several skin texels across.
const float STONE_CELLS = 12.0;
const float FROST_CELLS = 16.0;

vec2 turned(vec2 uv, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec2(c * uv.x - s * uv.y, s * uv.x + c * uv.y);
}

// Diamond cells along their edges, each flashing in its own turn.
float facetField(vec2 uv, float time) {
    vec2 diamond = turned(uv * FACET_CELLS, 0.7853982);
    vec2 inCell = fract(diamond);
    float toEdge = min(min(inCell.x, 1.0 - inCell.x), min(inCell.y, 1.0 - inCell.y));
    float edge = 1.0 - smoothstep(0.0, FACET_EDGE, toEdge);
    float phase = mingleHash(ivec3(floor(diamond), 0)) * TAU;
    float flash = pow(0.5 + 0.5 * sin(time * TAU * FACET_FLASH_CYCLES_PER_DAY + phase), 8.0);
    return max(edge, flash);
}

// Speckle swirling in place, the ender warp's glitter.
float shimmerField(vec2 uv, float time) {
    float swirl = mingleValueNoise(vec3(uv * SHIMMER_CELLS, time * SHIMMER_CYCLES_PER_DAY));
    return smoothstep(0.62, 0.85, swirl);
}

void main() {
    float strength = ailmentColor.a;
    vec3 tint = ailmentColor.rgb;
    // The noise patterns take one value per skin texel; the facets read no noise and keep the
    // smooth coordinate (decision mingle-noise-snaps-to-texels).
    vec2 skinTexel = mingleTexelSnap(skinCoord, MINGLE_SKIN_TEXELS);
    vec4 color;
    if (pattern == PATTERN_STONE) {
        float grain = mingleField(vec3(skinTexel * STONE_CELLS, 0.0));
        float crack = 1.0 - smoothstep(0.0, 0.04, abs(grain - 0.5));
        vec4 stone = vec4(tint * mix(0.7, 1.2, grain) * (1.0 - 0.5 * crack), 0.95 * strength);
        color = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(skinNormal), stone) * lightMapColor;
    } else if (pattern == PATTERN_FROST) {
        float crystals = smoothstep(0.55, 0.8, mingleValueNoise(vec3(skinTexel * FROST_CELLS, 0.0)));
        vec4 frost = vec4(mix(tint, vec3(1.0), crystals * 0.6), (0.35 + 0.5 * crystals) * strength);
        color = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(skinNormal), frost) * lightMapColor;
    } else if (pattern == PATTERN_WOBBLE) {
        float bubbles = smoothstep(0.6, 0.85, mingleValueNoise(vec3(skinTexel * GEL_CELLS, GameTime * GEL_CYCLES_PER_DAY)));
        vec4 gel = vec4(mix(tint, vec3(1.0), bubbles * 0.5), (0.4 + 0.3 * bubbles) * strength);
        color = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(skinNormal), gel) * lightMapColor;
    } else {
        float shine = 0.0;
        if (pattern == PATTERN_FACETS) {
            shine = facetField(skinCoord, GameTime);
        } else if (pattern == PATTERN_SHIMMER) {
            shine = shimmerField(skinTexel, GameTime);
        }
        color = vec4(tint * (0.5 + 1.2 * shine), (0.2 + 0.7 * shine) * strength);
    }
    if (color.a < 0.01) {
        discard;
    }
    fragColor = apply_fog(color * ColorModulator, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
