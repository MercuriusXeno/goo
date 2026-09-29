#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// The tunnel wave every tunnel-mining goo type sends into the wall at
// burnout (decision elemental-explosion-per-type): a train of round shock
// rings. TunnelWave packs the vertex color: red is the wave's progress,
// green the vertex's place around its ring in [0, 1), blue its place across
// the ring's band (0 inner edge, 1 outer edge), and alpha the ring's
// strength. ringPos carries green and blue.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out float progress;
out vec2 ringPos;
out float strength;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    progress = Color.r;
    ringPos = Color.gb;
    strength = Color.a;
}
