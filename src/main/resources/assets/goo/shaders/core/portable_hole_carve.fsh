#version 330

// A portable hole's mouth carved out of the world's depth
// (decision hole-items-are-a-three-dimensional-soup-sunk-in-the-hole): where the mouth's
// oval is the nearest thing seen, read against a copy of the world's depth, the depth is
// pushed to the far plane so the pit and the items sunk behind the face draw there; where
// anything nearer covers the oval, the fragment is dropped and the world stays as it is.
// Depth runs inverted, nearer reading larger and the far plane at zero
// (decision minecraft-26-3-update-is-a-port-to-parity).

uniform sampler2D HoleDepthSampler;

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float world = texelFetch(HoleDepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    if (gl_FragCoord.z < world) {
        discard;
    }
    fragColor = vertexColor;
    gl_FragDepth = 0.0;
}
