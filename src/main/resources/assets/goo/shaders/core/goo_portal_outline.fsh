#version 330

// A portal hidden behind blocks while its owner chooses a partner (decision
// portals-link-by-looking-through-portal-vision): only a thin glowing ring at the oval's edge,
// drawn through the blocks, so the portal can be found without covering what stands before it.

in vec2 frameCoord;
in vec4 frameColor;

out vec4 fragColor;

// Where across the oval the ring starts, zero the heart and one the edge.
const float RING_START = 0.9;

void main() {
    float out_ = length(frameCoord * 2.0 - 1.0);
    if (out_ > 1.0 || out_ < RING_START) {
        discard;
    }
    float glow = 1.0 - abs(out_ - (RING_START + 1.0) * 0.5) / ((1.0 - RING_START) * 0.5);
    fragColor = vec4(frameColor.rgb * 1.3, frameColor.a * glow);
}
