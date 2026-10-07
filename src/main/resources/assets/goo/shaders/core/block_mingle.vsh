#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// The block transform of decision petrify-stone-encasement-and-calcify-map:
// the old block's quads, drawn over the new block, carry how far the
// transform has run in the overlay coordinates' x instead of the hurt overlay.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec2 texCoord0;
out vec3 mingleWorldPos;
flat out float mingleProgress;

// Must match BlockMingleRenderer.PROGRESS_UNITS: UV1.x carries the share of the transform run.
const float PROGRESS_UNITS = 4096.0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;

    // Position is camera-relative; the camera globals recover the world position.
    mingleWorldPos = Position + vec3(CameraBlockPos) - CameraOffset;
    mingleProgress = float(UV1.x) / PROGRESS_UNITS;
}
