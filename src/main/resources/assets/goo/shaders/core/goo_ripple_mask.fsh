#version 330
#extension GL_ARB_separate_shader_objects : require

// One silhouette's mask in an afterimage's ripple (decision afterimage-is-one-shared-effect):
// the fade fills every channel, and the pipeline's write mask keeps the silhouette's own.

layout(location = 0) in float fade;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(fade);
}
