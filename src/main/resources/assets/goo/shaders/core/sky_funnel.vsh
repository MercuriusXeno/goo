#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// The sky funnel and the doorway it rises from (decision
// sky-funnel-points-down-to-the-doorway-from-any-distance): vanilla's End portal vertex shader,
// its starfield coordinates taken from the surface rather than the screen, so the stars sit on
// the walls at a size in blocks and shrink with distance, and its color carrying the vertex's
// brightness.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 surfaceCoord;
out vec4 brightness;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    surfaceCoord = UV0;
    brightness = Color;
}
