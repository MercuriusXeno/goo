#version 330

#moj_import <minecraft:projection.glsl>

// One pass of the bilateral blur that makes the Unmake drink's spheres one
// skin (decision unmake-drink-draws-as-a-screen-space-fluid), along the screen
// axis Frame.yz names: a covered pixel's distance from the eye and its color
// are averaged with its covered neighbours, the kernel as wide on screen as
// the sphere there, read from the coverage channel, times WIDTH_SHARE, and a
// neighbour farther in depth than the radius times DEPTH_SHARE weighs nothing,
// so the silhouette against the world and between a near and a far stream
// stays sharp; a pixel no splat covered keeps the world's depth. The kernel
// reads every pixel up to MOST_TAPS either side, its taps spread evenly past
// that and each read between its two nearest pixels. The constants mirror
// DrinkBlur, which DrinkShaderTest checks them against.

uniform sampler2D FluidColor;
uniform sampler2D FluidDepth;

layout(std140) uniform DrinkFrame {
    vec4 Frame;
};

in vec2 texCoord;

out vec4 fragColor;

const int MOST_TAPS = 24;
const float WIDTH_SHARE = 1.0;
const float DEPTH_SHARE = 1.0;
const float RADIUS_SCALE = 0.5;
const float COVERED = 0.002;
const float SPATIAL = 2.0;

float distanceOf(float depth) {
    float ndc = Frame.x > 0.5 ? depth : depth * 2.0 - 1.0;
    return ProjMat[3][2] / (ndc + ProjMat[2][2]);
}

float depthOf(float away) {
    float ndc = ProjMat[3][2] / away - ProjMat[2][2];
    return Frame.x > 0.5 ? ndc : ndc * 0.5 + 0.5;
}

float weightOf(int tap, int taps, float depthDelta, float threshold) {
    float share = float(tap) / float(taps);
    float near = depthDelta / threshold;
    float range = abs(near) >= 1.0 ? 0.0 : (1.0 - near * near) * (1.0 - near * near);
    return exp(-SPATIAL * share * share) * range;
}

// A tap's distance and color, read between its two nearest pixels where both
// are covered and at the covered one where only one is, as DrinkBlur.readAt;
// its alpha is under COVERED where neither is.
vec4 readAt(vec2 at, ivec2 size, ivec2 axis, out float there) {
    vec2 within = clamp(at, vec2(0.0), vec2(size - 1));
    ivec2 low = ivec2(floor(within));
    ivec2 high = min(low + axis, size - 1);
    float share = clamp(dot(within - vec2(low), vec2(axis)), 0.0, 1.0);
    vec4 lowColor = texelFetch(FluidColor, low, 0);
    vec4 highColor = texelFetch(FluidColor, high, 0);
    float lowDistance = distanceOf(texelFetch(FluidDepth, low, 0).r);
    float highDistance = distanceOf(texelFetch(FluidDepth, high, 0).r);
    if (lowColor.a >= COVERED && highColor.a >= COVERED) {
        there = mix(lowDistance, highDistance, share);
        return mix(lowColor, highColor, share);
    }
    there = lowColor.a >= COVERED ? lowDistance : highDistance;
    return lowColor.a >= COVERED ? lowColor : highColor;
}

void main() {
    ivec2 size = textureSize(FluidDepth, 0);
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 center = texelFetch(FluidColor, pixel, 0);
    float depth = texelFetch(FluidDepth, pixel, 0).r;
    if (center.a < COVERED) {
        fragColor = center;
        gl_FragDepth = depth;
        return;
    }
    ivec2 axis = ivec2(Frame.yz);
    float here = distanceOf(depth);
    float radius = center.a * RADIUS_SCALE;
    float pixelsPerBlock = ProjMat[1][1] * float(size.y) * 0.5 / here;
    float width = max(1.0, WIDTH_SHARE * radius * pixelsPerBlock);
    int taps = int(min(float(MOST_TAPS), ceil(width)));
    vec2 stride = vec2(axis) * (width / float(taps));
    float threshold = DEPTH_SHARE * radius;
    float weights = 0.0;
    float distances = 0.0;
    vec4 colors = vec4(0.0);
    for (int tap = -taps; tap <= taps; tap++) {
        float there;
        vec4 color = readAt(vec2(pixel) + stride * float(tap), size, axis, there);
        if (color.a >= COVERED) {
            float weight = weightOf(tap, taps, there - here, threshold);
            weights += weight;
            distances += weight * there;
            colors += weight * color;
        }
    }
    fragColor = colors / weights;
    gl_FragDepth = depthOf(distances / weights);
}
