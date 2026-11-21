#version 330
#extension GL_ARB_separate_shader_objects : require

// A portable hole's mouth marked where it is the nearest thing seen
// (decision hole-items-are-a-three-dimensional-soup-sunk-in-the-hole): the depth test alone
// decides, and the pipeline sets the hole's stencil bit on each fragment that passes it, writing
// no color and no depth, so the carve opens the depth on those pixels alone.

layout(location = 0) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vertexColor;
}
