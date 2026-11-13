#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Petrify's fog (decision petrify-fog-is-billowing-roiling-dust).
// PetrifyFog packs the vertex color: red is the puff's share of its life,
// green and blue the vertex's corner on the puff's quad, each of [-1, 1]
// mapped onto [0, 1], and alpha the puff's seed.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out float age;
out vec2 corner;
flat out float seed;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    age = Color.r;
    corner = Color.gb * 2.0 - 1.0;
    seed = Color.a;
}
