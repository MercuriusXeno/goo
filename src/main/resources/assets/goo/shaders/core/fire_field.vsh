#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// The box around one burning Flames jet, drawn so the fragment shader can
// march the fire field inside it (decision flames-pixelated-fire-stream).
// Position is camera-relative, and is the ray to march along.

in vec3 Position;
in vec4 Color;

out vec3 rayPoint;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    rayPoint = Position;
}
