#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// A portable hole's mouth carved out of the world's depth
// (decision hole-items-are-a-three-dimensional-soup-sunk-in-the-hole): the mouth's
// oval laid just off its face, camera relative.

in vec3 Position;
in vec4 Color;

out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
}
