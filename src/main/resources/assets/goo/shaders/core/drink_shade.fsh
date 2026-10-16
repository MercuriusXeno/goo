#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

// The Unmake drink's skin, shaded from the fluid target in one full-screen
// pass (decision unmake-drink-draws-as-a-screen-space-fluid): where a splat
// covered the pixel, its blurred depth is read back to the point it shows, the
// normal built from the steps to its neighbours on each axis, and its color, the
// block's sampled color crossfading to the goo's in the level's light
// (decision unmake-skin-is-a-solid-sampled-color), glistens with a specular
// highlight and a rim brightening (decision unmake-metaball-glistens-and-shades)
// before the cardinal lights and the fog; the depth is written to the main
// target so the world hides the goo behind walls and the goo hides the world
// behind it.

uniform sampler2D FluidColor;
uniform sampler2D FluidDepth;

layout(std140) uniform DrinkFrame {
    vec4 Frame;
};

in vec2 texCoord;

out vec4 fragColor;

const float COVERED = 0.002;
const float GLISTEN_POWER = 48.0;
const float GLISTEN_STRENGTH = 0.35;
const float RIM_POWER = 3.0;
const float RIM_STRENGTH = 0.25;

// The camera-relative point a pixel of the fluid target shows, from its depth.
vec3 pointAt(vec2 uv, float depth) {
    float z = Frame.x > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 view = inverse(ProjMat) * vec4(uv * 2.0 - 1.0, z, 1.0);
    return inverse(mat3(ModelViewMat)) * (view.xyz / view.w);
}

// The solid skin shaded as a lit liquid: a white-leaning Blinn-Phong
// highlight from each cardinal light and a rim brightening where the surface
// turns away from the eye. n and view are in view space.
vec4 glisten(vec4 color, vec3 n, vec3 view) {
    float shine = pow(max(dot(n, normalize(Light0_Direction + view)), 0.0), GLISTEN_POWER)
        + pow(max(dot(n, normalize(Light1_Direction + view)), 0.0), GLISTEN_POWER);
    float rim = pow(1.0 - max(dot(n, view), 0.0), RIM_POWER);
    color.rgb += vec3(shine * GLISTEN_STRENGTH + rim * RIM_STRENGTH);
    return color;
}

vec3 pointAtPixel(ivec2 pixel, ivec2 size) {
    ivec2 within = clamp(pixel, ivec2(0), size - 1);
    return pointAt((vec2(within) + 0.5) / vec2(size), texelFetch(FluidDepth, within, 0).r);
}

// The step across a pixel the normal is built from: the smaller in distance
// from the eye of the steps to its two neighbours, as DrinkBlur.slopeAt, so a
// pixel at an edge takes its slope from the surface it lies on rather than
// across the drop to the world behind.
vec3 stepAcross(vec3 before, vec3 at, vec3 after) {
    float forward = length(after) - length(at);
    float backward = length(at) - length(before);
    return abs(forward) < abs(backward) ? after - at : at - before;
}

void main() {
    vec4 skin = texture(FluidColor, texCoord);
    if (skin.a < COVERED) {
        discard;
    }
    ivec2 size = textureSize(FluidDepth, 0);
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float depth = texelFetch(FluidDepth, pixel, 0).r;
    vec3 p = pointAtPixel(pixel, size);
    vec3 across = stepAcross(pointAtPixel(pixel - ivec2(1, 0), size), p, pointAtPixel(pixel + ivec2(1, 0), size));
    vec3 up = stepAcross(pointAtPixel(pixel - ivec2(0, 1), size), p, pointAtPixel(pixel + ivec2(0, 1), size));
    vec3 n = cross(across, up);
    n = dot(n, n) > 0.0 ? normalize(n) : normalize(-p);
    n = dot(n, -p) < 0.0 ? -n : n;
    vec3 viewNormal = normalize(mat3(ModelViewMat) * n);
    vec3 toEye = normalize(mat3(ModelViewMat) * -p);
    vec4 color = glisten(vec4(skin.rgb, 1.0), viewNormal, toEye);
    color = minecraft_mix_light(Light0_Direction, Light1_Direction, viewNormal, color);
    color.a = 1.0;
    fragColor = apply_fog(color, fog_spherical_distance(p), fog_cylindrical_distance(p), FogEnvironmentalStart,
        FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
    gl_FragDepth = depth;
}
