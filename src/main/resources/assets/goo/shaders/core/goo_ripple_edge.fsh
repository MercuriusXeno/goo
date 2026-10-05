#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// The perimeter of an afterimage's silhouettes (decision afterimage-is-one-shared-effect):
// each channel of the ripple buffer holds one silhouette's mask at its fade. A pixel is
// perimeter where a channel differs from a neighbor within EDGE_RADIUS pixels, by as much
// as that silhouette's fade, so only the outline of each silhouette's 2D projection
// shows, never a seam inside it. ColorModulator carries the goo type's color.

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

// Pixels the perimeter reaches to either side of the silhouette's border.
const int EDGE_RADIUS = 2;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    vec4 center = texture(InSampler, texCoord);
    vec4 change = vec4(0.0);
    for (int dx = -EDGE_RADIUS; dx <= EDGE_RADIUS; dx++) {
        for (int dy = -EDGE_RADIUS; dy <= EDGE_RADIUS; dy++) {
            if (dx * dx + dy * dy > EDGE_RADIUS * EDGE_RADIUS) {
                continue;
            }
            change = max(change, abs(center - texture(InSampler, texCoord + vec2(dx, dy) * texel)));
        }
    }
    float strength = min(1.0, change.r + change.g + change.b + change.a);
    if (strength < 0.004) {
        discard;
    }
    fragColor = vec4(ColorModulator.rgb, strength);
}
