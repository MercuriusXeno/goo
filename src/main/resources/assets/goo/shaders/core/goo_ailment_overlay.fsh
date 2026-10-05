#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import "mingle_noise.glsl"

// A status ailment's overlay over a whole model (decision ailment-overlay-shader-per-ailment):
// one field per pattern, laid over the model's skin coordinates so it rides the body,
// tinted by the ailment's color. The glints draw unlit, like the enchantment glint;
// the stone and the frost encase the model and take its light.

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 ailmentColor;
in vec4 lightMapColor;
in vec2 skinCoord;
in vec3 skinNormal;
flat in int pattern;

out vec4 fragColor;

// Must match AilmentPattern's order.
const int PATTERN_GLINT = 0;
const int PATTERN_FACETS = 1;
const int PATTERN_SHIMMER = 2;
const int PATTERN_STONE = 3;
const int PATTERN_FROST = 4;

const float TAU = 6.2831853;
// GameTime is the fraction of a 24000-tick day: 300 cycles a day is one every 80 ticks.
const float GLINT_CYCLES_PER_DAY = 300.0;
// Glint texture repeats across the skin's coordinates.
const float GLINT_TILES = 6.0;
// The glint's two sweeps cross at these angles, in radians.
const float GLINT_ANGLE_A = 0.17;
const float GLINT_ANGLE_B = -0.9;
// Diamond facets across the skin's coordinates.
const float FACET_CELLS = 24.0;
const float FACET_EDGE = 0.08;
const float FACET_FLASH_CYCLES_PER_DAY = 600.0;
// Noise cells of the ender speckle across the skin's coordinates, and how fast it churns.
const float SHIMMER_CELLS = 40.0;
const float SHIMMER_CYCLES_PER_DAY = 900.0;
// Noise cells of the stone grain and of the frost crystals.
const float STONE_CELLS = 18.0;
const float FROST_CELLS = 30.0;

vec2 turned(vec2 uv, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec2(c * uv.x - s * uv.y, s * uv.x + c * uv.y);
}

float glintTexel(vec2 uv) {
    return dot(textureLod(Sampler0, fract(uv), 0.0).rgb, vec3(0.299, 0.587, 0.114));
}

// Two glint sweeps crossing, as the enchantment glint layers two passes.
float glintField(vec2 uv, float time) {
    float sweep = time * GLINT_CYCLES_PER_DAY;
    float first = glintTexel(turned(uv * GLINT_TILES, GLINT_ANGLE_A) + vec2(sweep, 0.0));
    float second = glintTexel(turned(uv * GLINT_TILES, GLINT_ANGLE_B) - vec2(0.0, sweep * 0.7));
    return clamp(first + second, 0.0, 1.0);
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

// Speckle churning in place, the ender warp's glitter.
float shimmerField(vec2 uv, float time) {
    float churn = mingleValueNoise(vec3(uv * SHIMMER_CELLS, time * SHIMMER_CYCLES_PER_DAY));
    return smoothstep(0.62, 0.85, churn);
}

void main() {
    float strength = ailmentColor.a;
    vec3 tint = ailmentColor.rgb;
    vec4 color;
    if (pattern == PATTERN_STONE) {
        float grain = mingleField(vec3(skinCoord * STONE_CELLS, 0.0));
        float crack = 1.0 - smoothstep(0.0, 0.04, abs(grain - 0.5));
        vec4 stone = vec4(tint * mix(0.7, 1.2, grain) * (1.0 - 0.5 * crack), 0.95 * strength);
        color = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(skinNormal), stone) * lightMapColor;
    } else if (pattern == PATTERN_FROST) {
        float crystals = smoothstep(0.55, 0.8, mingleValueNoise(vec3(skinCoord * FROST_CELLS, 0.0)));
        vec4 frost = vec4(mix(tint, vec3(1.0), crystals * 0.6), (0.35 + 0.5 * crystals) * strength);
        color = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(skinNormal), frost) * lightMapColor;
    } else {
        float shine;
        if (pattern == PATTERN_FACETS) {
            shine = facetField(skinCoord, GameTime);
        } else if (pattern == PATTERN_SHIMMER) {
            shine = shimmerField(skinCoord, GameTime);
        } else {
            shine = glintField(skinCoord, GameTime);
        }
        color = vec4(tint * (0.5 + 1.2 * shine), (0.2 + 0.7 * shine) * strength);
    }
    if (color.a < 0.01) {
        discard;
    }
    fragColor = apply_fog(color * ColorModulator, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
