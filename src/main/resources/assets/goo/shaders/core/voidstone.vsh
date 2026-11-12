#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

// Voidstone's faces (decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform):
// the stars are placed by world position. Position is camera-relative and small, so it stays exact; the
// vertex hands the fragment its offset from the camera's block, and the fragment adds the camera's block
// in integers, since a float a million blocks out cannot place a sixteenth of a block.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 voidLocal;
flat out vec3 voidNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    voidLocal = Position - CameraOffset;
    voidNormal = Normal;
}
