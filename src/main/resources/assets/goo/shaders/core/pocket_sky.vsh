#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// The pocket's sky stars (decision outsider-room-draws-a-skybox-of-stars): each star is a small quad on a
// sphere round the camera, drawn under the view's rotation alone, so where a star sits on screen follows the
// view direction and never the camera's place. The color carries the star's brightness, twinkle rate and
// twinkle phase for the fragment.

in vec3 Position;
in vec4 Color;

out vec4 starColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    starColor = Color;
}
