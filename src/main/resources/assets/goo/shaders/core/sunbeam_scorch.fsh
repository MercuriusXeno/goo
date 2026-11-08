#version 330

// Sunbeam's scorch (decision sunbeam-leaves-a-glowing-scorch-that-trails-and-fades):
// one burnt spot as a ragged disc. A fresh mark glows white-hot across the
// disc; as it cools the glow draws in to a shrinking core and runs through
// yellow and orange to a dull red, leaving a dark char ring, and in the last
// share of its life the char itself fades away. Premultiplied blend: the char
// darkens the face it lies on and the glow adds light onto it, the block
// showing through, so a mark reads as a burn on the block rather than a decal.

in vec2 acrossMark;
in float heat;
in float seed;

out vec4 fragColor;

const vec3 WHITE_HOT = vec3(1.0, 0.97, 0.86);
const vec3 YELLOW = vec3(1.0, 0.78, 0.30);
const vec3 ORANGE = vec3(1.0, 0.42, 0.06);
const vec3 EMBER = vec3(0.55, 0.09, 0.02);
const vec3 CHAR = vec3(0.07, 0.05, 0.04);
const float CHAR_OPACITY = 0.78;
// The share of a mark's life, at its cold end, over which its char fades out.
const float CHAR_FADE = 0.3;
const float EDGE_LOBES = 5.0;
const float EDGE_RAGGEDNESS = 0.12;
const float TWO_PI = 6.2831853;

// The glow's color at a heat: white-hot through yellow and orange to a dull ember.
vec3 glowAt(float h) {
    vec3 color = mix(EMBER, ORANGE, smoothstep(0.15, 0.45, h));
    color = mix(color, YELLOW, smoothstep(0.45, 0.75, h));
    return mix(color, WHITE_HOT, smoothstep(0.75, 0.95, h));
}

void main() {
    float radius = length(acrossMark);
    float angle = atan(acrossMark.y, acrossMark.x);
    float edge = 1.0 - EDGE_RAGGEDNESS * (0.5 + 0.5 * sin(angle * EDGE_LOBES + seed * TWO_PI))
            - EDGE_RAGGEDNESS * 0.5 * (0.5 + 0.5 * sin(angle * (EDGE_LOBES + 2.0) - seed * TWO_PI * 3.0));
    float disc = 1.0 - smoothstep(edge * 0.7, edge, radius);
    if (disc <= 0.0) {
        discard;
    }
    // The glow fills the disc while white-hot and draws in to its middle as it cools.
    float glowReach = edge * mix(0.15, 0.95, heat);
    float glow = (1.0 - smoothstep(glowReach * 0.5, glowReach, radius)) * smoothstep(0.05, 0.3, heat);
    float charOpacity = CHAR_OPACITY * smoothstep(0.0, CHAR_FADE, heat);
    // The char covers the face where no glow burns; the glow lifts the face beneath it.
    float cover = charOpacity * (1.0 - glow) * disc;
    fragColor = vec4(CHAR * cover + glowAt(heat) * glow * disc, cover);
}
