#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// The box around Drain's cone, drawn so the fragment shader can march the miasma vortex inside it
// (decision drain-casts-a-filled-tornadic-miasma-vortex-along-the-look). Position is camera-relative,
// and is the ray to march along.

in vec3 Position;
in vec4 Color;

out vec3 rayPoint;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    rayPoint = Position;
}
