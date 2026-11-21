#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec4 vertColor;
layout(location = 1) in vec3 reflectedColor;

layout(location = 0) out vec4 fragColor;

// Base opacity
const float BASE_ALPHA = 0.8;

void main() {
    float density = vertColor.r;
    float animFade = vertColor.g;
    float alpha = BASE_ALPHA * max(density, 0.2) * animFade;

    fragColor = vec4(reflectedColor, alpha);
}
