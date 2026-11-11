#version 330

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Bore's vortex (decisions bore-vortex-with-a-worldspace-shake,
// diagnose-and-correct-bores-small-distant-vortex).
// ConeSections packs the vertex color: red is how far along the tunnel the
// tube's ring stands, green and blue the vertex's place around the ring,
// each of [-1, 1] mapped onto [0, 1]. The normal points out from the axis;
// facing carries how squarely the view meets the wall, 0 where it grazes it.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

out float along;
out vec2 discPos;
out float facing;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;
    along = Color.r;
    discPos = Color.gb * 2.0 - 1.0;
    vec3 viewNormal = normalize(mat3(ModelViewMat) * Normal);
    facing = abs(dot(viewNormal, normalize(viewPos.xyz)));
}
