#version 330

// One silhouette's mask in an afterimage's ripple (decision afterimage-is-one-shared-effect):
// the fade fills every channel, and the pipeline's write mask keeps the silhouette's own.

in float fade;

out vec4 fragColor;

void main() {
    fragColor = vec4(fade);
}
