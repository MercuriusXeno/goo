#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// A portal with no view to show (decision portals-link-by-looking-through-portal-vision): the
// frame's quad placed as world geometry, its corners mapped zero to one across, its color on.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 frameCoord;
out vec4 frameColor;

void main() {
    gl_Position = ProjMat * (ModelViewMat * vec4(Position, 1.0));
    frameCoord = UV0;
    frameColor = Color;
}
