#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

// Voidstone's faces (decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform):
// the stars are placed by world position, so the vertex hands the fragment its world point and its face.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 voidWorldPos;
flat out vec3 voidNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // Position is camera-relative; the camera globals recover the world position.
    voidWorldPos = Position + vec3(CameraBlockPos) - CameraOffset;
    voidNormal = Normal;
}
