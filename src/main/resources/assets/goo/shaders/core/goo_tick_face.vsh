#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Tick's face overlay (decision tick-channel-marches-squares-on-the-face).
// TickFaceOverlay packs the vertex color: red and green are the vertex's
// place on the face, each of [0, 1]; blue is the march rate, rings a tick
// over TickFaceOverlay.MAX_RINGS_PER_TICK; alpha is how strongly it draws,
// a drip's splash fading out (decision tick-drip-splashes-a-small-tick-effect).

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec2 facePos;
out float rate;
out float strength;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    facePos = Color.rg * 2.0 - 1.0;
    rate = Color.b;
    strength = Color.a;
}
