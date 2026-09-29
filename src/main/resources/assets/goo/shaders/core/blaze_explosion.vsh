#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Blaze goo's burnout explosion (decision elemental-explosion-per-type).
// BlazeExplosionVisual packs the vertex color: red is the explosion's
// progress, green the placed face's ordinal (Direction order: down, up,
// north, south, west, east), blue the flame's remaining strength. The
// normal is the unit direction from the dome's center.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 surfaceDir;
out float progress;
out float strength;
flat out vec3 faceUp;

vec3 faceStep(int ordinal) {
    if (ordinal == 0) return vec3(0.0, -1.0, 0.0);
    if (ordinal == 1) return vec3(0.0, 1.0, 0.0);
    if (ordinal == 2) return vec3(0.0, 0.0, -1.0);
    if (ordinal == 3) return vec3(0.0, 0.0, 1.0);
    if (ordinal == 4) return vec3(-1.0, 0.0, 0.0);
    return vec3(1.0, 0.0, 0.0);
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    surfaceDir = Normal;
    progress = Color.r;
    strength = Color.b;
    faceUp = faceStep(int(Color.g * 255.0 + 0.5));
}
