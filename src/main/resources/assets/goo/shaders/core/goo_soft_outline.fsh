#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// A soft blurred outline around the bodies the red channel of the ripple buffer holds, each
// at its strength (decision sculker-sight-persists-where-echoes-have-touched): the mask is
// blurred across a disc of SOFT_RADIUS pixels, and the glow paints where the blur spreads past
// the body, fading out from its edge, with the body itself only faintly lit inside.
// ColorModulator carries the glow's color.

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

// Pixels the glow spreads out from the body's edge.
const int SOFT_RADIUS = 8;
// Pixels between the blur's taps.
const int TAP_STEP = 2;
// How strongly the blur paints, and how much of it shows inside the body.
const float GLOW_GAIN = 1.8;
const float INSIDE_SHARE = 0.25;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    float center = texture(InSampler, texCoord).r;
    float sum = 0.0;
    float weights = 0.0;
    for (int dx = -SOFT_RADIUS; dx <= SOFT_RADIUS; dx += TAP_STEP) {
        for (int dy = -SOFT_RADIUS; dy <= SOFT_RADIUS; dy += TAP_STEP) {
            float reach = float(dx * dx + dy * dy) / float(SOFT_RADIUS * SOFT_RADIUS);
            if (reach > 1.0) {
                continue;
            }
            float weight = 1.0 - reach;
            sum += texture(InSampler, texCoord + vec2(dx, dy) * texel).r * weight;
            weights += weight;
        }
    }
    float blurred = sum / weights;
    float outside = center > 0.0 ? INSIDE_SHARE : 1.0;
    float strength = min(1.0, blurred * GLOW_GAIN) * outside;
    if (strength < 0.004) {
        discard;
    }
    fragColor = vec4(ColorModulator.rgb, strength);
}
