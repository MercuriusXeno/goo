#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// One splat of an Unmake drink (decision unmake-drink-draws-as-a-screen-space-fluid):
// a corner of the camera-facing quad covering one sphere, or one block's
// rounded cube. Position is camera-relative, and is the eye ray the fragment
// meets the shape along.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec4 Shape;

out vec3 rayPoint;
out vec4 splatColor;
out vec4 shape;
out float rounding;
flat out ivec2 light;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    rayPoint = Position;
    splatColor = Color;
    shape = Shape;
    rounding = UV0.x;
    light = UV2;
}
