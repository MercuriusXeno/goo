#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <goo:room_copy.glsl>

// Voidstone's faces in each copy of the room (decision room-tiles-to-render-distance-held-to-the-picture): the face
// draws at its copy's place but places its stars from the room's own, so every copy shows the room's sky of stars
// as a view through a linked portal shows the far side's. Position is laid out from the room's origin, and
// ModelOffset carries that origin camera-relative.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out vec3 voidLocal;
flat out vec3 voidNormal;

void main() {
    vec3 room = Position + ModelOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(room + roomCopyTranslation(), 1.0);
    voidLocal = room - CameraOffset;
    voidNormal = Normal;
}
