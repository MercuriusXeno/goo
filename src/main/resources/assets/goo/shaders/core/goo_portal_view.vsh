#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>

// A gate's face showing the view through it (decision portals-link-by-looking-through-portal-vision):
// the face's quad placed as world geometry, its corners mapped zero to one across for the oval,
// its color on for the rim; the fragment shader reads the view through by screen point. Each
// corner carries the face's texel count, sixteen a block, for the fragment shader's grid
// (decision portals-and-wormhole-draw-sixteen-texels-a-block).

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in ivec2 UV2;
layout(location = 3) in vec4 Color;

layout(location = 0) out vec2 frameCoord;
layout(location = 1) flat out vec2 texels;
layout(location = 2) out vec4 frameColor;

void main() {
    gl_Position = ProjMat * (ModelViewMat * vec4(Position, 1.0));
    frameCoord = UV0;
    texels = vec2(UV2);
    frameColor = Color;
}
