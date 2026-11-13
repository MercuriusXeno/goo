#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// The box around Bore's drill, drawn so the fragment shader can march to the bit inside it
// (decision bore-shows-a-big-drill-from-the-glove). Position is camera-relative, and is the ray to
// march along.

in vec3 Position;
in vec4 Color;

out vec3 rayPoint;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    rayPoint = Position;
}
