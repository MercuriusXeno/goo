#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// The sky funnel round an Outsider room's doorway (decision
// sky-funnel-points-down-to-the-doorway-from-any-distance): vanilla's End portal vertex shader,
// its starfield read at the screen point as the doorway's is, so the pillar reads as the doorway
// it hides, and its color carrying the vertex's brightness.

in vec3 Position;
in vec4 Color;

out vec4 texProj0;
out vec4 brightness;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texProj0 = projection_from_position(gl_Position);
    brightness = Color;
}
