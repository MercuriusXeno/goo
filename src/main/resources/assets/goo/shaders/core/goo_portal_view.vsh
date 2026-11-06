#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// A linked portal's face (decision portals-link-by-looking-through-portal-vision): the face's
// quad placed as any world geometry; the fragment shader reads the view through by screen point.

in vec3 Position;
in vec4 Color;

void main() {
    gl_Position = ProjMat * (ModelViewMat * vec4(Position, 1.0));
}
