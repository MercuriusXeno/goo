#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec3 Position;
in vec4 Color;
in vec3 Normal;
out float opacity;

void main() {
    gl_Position = ProjMat * (ModelViewMat * vec4(Position, 1.0));
    // Alpha is the hole's opacity, faded in over its startup ramp (decision dome-fades-in-before-its-start).
    opacity = Color.a;
}
