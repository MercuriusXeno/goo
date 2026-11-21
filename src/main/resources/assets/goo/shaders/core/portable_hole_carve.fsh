#version 330
#extension GL_ARB_separate_shader_objects : require

// A portable hole's mouth carved out of the world's depth
// (decision hole-items-are-a-three-dimensional-soup-sunk-in-the-hole): on the pixels the mark
// set, where the mouth's oval is the nearest thing seen, the depth is pushed to the far plane so
// the pit and the items sunk behind the face draw there; the pipeline's stencil test drops every
// other fragment, so the world stays as it is wherever anything nearer covers the oval.
// Depth runs inverted, nearer reading larger and the far plane at zero
// (decision minecraft-26-3-update-is-a-port-to-parity).

layout(location = 0) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vertexColor;
    gl_FragDepth = 0.0;
}
