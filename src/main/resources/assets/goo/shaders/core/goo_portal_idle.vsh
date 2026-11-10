#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// A portal with no view to show (decision portals-link-by-looking-through-portal-vision): the
// frame's quad placed as world geometry, its corners mapped zero to one across, its color on.
// Each corner carries the face's texel count, sixteen a block, for the fragment shader's grid
// (decision portals-and-wormhole-draw-sixteen-texels-a-block).

in vec3 Position;
in vec2 UV0;
in ivec2 UV2;
in vec4 Color;

out vec2 frameCoord;
flat out vec2 texels;
out vec4 frameColor;

void main() {
    gl_Position = ProjMat * (ModelViewMat * vec4(Position, 1.0));
    frameCoord = UV0;
    texels = vec2(UV2);
    frameColor = Color;
}
