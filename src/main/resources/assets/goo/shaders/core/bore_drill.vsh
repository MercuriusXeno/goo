#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Bore's drill (decision bore-shows-a-big-drill-from-the-glove).
// ConeSections packs the vertex color: red is how far from the glove to the
// tip the bit's ring stands, green and blue the vertex's place around the
// ring, each of [-1, 1] mapped onto [0, 1]. The normal is square to the bit's
// surface; the fragment lights the bit by it in view space.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out float along;
out vec2 discPos;
out vec3 viewNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    along = Color.r;
    discPos = Color.gb * 2.0 - 1.0;
    viewNormal = mat3(ModelViewMat) * Normal;
}
