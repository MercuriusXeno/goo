#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// The vanilla entity vertex shader under EMISSIVE, NO_OVERLAY and
// NO_CARDINAL_LIGHTING, plus the lift of decision undulating-fluid-surface.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec2 worldXZ;
flat out vec2 band;

// Must match RenderContext.AMPLITUDE_UNITS_PER_BLOCK: UV1.x carries the
// vertex's ripple amplitude in 1/4096 block, zero on every flat vertex.
const float AMPLITUDE_UNITS_PER_BLOCK = 4096.0;
// Must match TypeBand.BAND_UNITS: the surface is emissive, so UV2 carries
// the type band [lo, hi) of decision noise-mingled-type-textures instead of light.
const float BAND_UNITS = 16384.0;

const float TAU = 6.2831853;
// GameTime is the fraction of a 24000-tick day, so whole cycles per day
// keep the wave seamless when the day wraps: 600 = 40 ticks, 420 ~ 57 ticks.
const float PRIMARY_CYCLES_PER_DAY = 600.0;
const float SECONDARY_CYCLES_PER_DAY = 420.0;
// Spatial frequencies in radians per block along each diagonal.
const float PRIMARY_WAVENUMBER = 9.0;
const float SECONDARY_WAVENUMBER = 13.0;

float ripple(vec2 worldXZ) {
    float primary = sin(dot(worldXZ, vec2(0.8, 0.6)) * PRIMARY_WAVENUMBER
        + GameTime * TAU * PRIMARY_CYCLES_PER_DAY);
    float secondary = sin(dot(worldXZ, vec2(-0.5, 0.87)) * SECONDARY_WAVENUMBER
        - GameTime * TAU * SECONDARY_CYCLES_PER_DAY);
    return 0.5 * (primary + secondary);
}

void main() {
    // Position is camera-relative; terrain.vsh shows the camera globals'
    // relation, from which the world position is recovered.
    vec3 worldPos = Position + vec3(CameraBlockPos) - CameraOffset;
    float amplitude = float(UV1.x) / AMPLITUDE_UNITS_PER_BLOCK;
    vec3 lifted = Position + vec3(0.0, amplitude * ripple(worldPos.xz), 0.0);

    gl_Position = ProjMat * ModelViewMat * vec4(lifted, 1.0);

    sphericalVertexDistance = fog_spherical_distance(lifted);
    cylindricalVertexDistance = fog_cylindrical_distance(lifted);
    vertexColor = Color;
    texCoord0 = UV0;
    worldXZ = worldPos.xz;
    band = vec2(UV2) / BAND_UNITS;
}
