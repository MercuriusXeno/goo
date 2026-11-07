#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// A gate's face showing the view through it (decision portals-link-by-looking-through-portal-vision):
// the face's quad placed as world geometry, its corners mapped zero to one across for the oval,
// its color on for the rim; the fragment shader reads the view through by screen point.

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
