#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// The vanilla entity vertex shader, lit by the lightmap, plus the dissolve of
// decision dissolve-shader-on-item: the overlay coordinates carry the dissolve
// fraction and the glow color instead of the hurt overlay.

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
out vec3 dissolveWorldPos;
flat out float dissolveFraction;
flat out vec3 glowColor;

// Must match DissolveGlow.FRACTION_UNITS: UV1.x carries the fraction dissolved.
const float FRACTION_UNITS = 4096.0;

// UV1.y carries the glow color as RGB565, read back unsigned from the short.
vec3 unpackRgb565(int packed) {
    uint bits = uint(packed) & 0xFFFFu;
    return vec3(float((bits >> 11u) & 31u) / 31.0,
                float((bits >> 5u) & 63u) / 63.0,
                float(bits & 31u) / 31.0);
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;

    // Position is camera-relative; the camera globals recover the world position.
    dissolveWorldPos = Position + vec3(CameraBlockPos) - CameraOffset;
    dissolveFraction = float(UV1.x) / FRACTION_UNITS;
    glowColor = unpackRgb565(UV1.y);
}
