#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// The tunnel wave every tunnel-mining goo type sends into the wall at
// burnout (decision elemental-explosion-per-type). TunnelWave packs the
// vertex color: red is the wave's progress down the tunnel, green and blue
// the vertex's position across the plate, each in [0, 1], and alpha the
// wave's strength.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out float progress;
out vec2 platePos;
out float strength;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    progress = Color.r;
    platePos = Color.gb;
    strength = Color.a;
}
