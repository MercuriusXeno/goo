#version 330

#moj_import <minecraft:projection.glsl>

// One pass of the bilateral blur that makes the Unmake drink's spheres one
// skin (decision unmake-drink-draws-as-a-screen-space-fluid), along the screen
// axis Frame.yz names: a covered pixel's distance from the eye and its color
// are averaged with its covered neighbours, every pixel the kernel reaches, the
// kernel as wide on screen as the sphere there, read from the coverage channel,
// times WIDTH_SHARE up to MOST_TAPS pixels; a neighbour farther in depth than
// the radius times DEPTH_SHARE weighs nothing, so the silhouette against the
// world and between a near and a far stream stays sharp, and a pixel no splat
// covered keeps the world's depth. Every weight falls smoothly to nothing at
// its edge, so nothing the kernel gains or loses as it moves steps the
// average. The pixel keeps its own coverage, its own sphere's radius. The
// constants mirror DrinkBlur, which DrinkShaderTest checks them against.

uniform sampler2D FluidColor;
uniform sampler2D FluidDepth;

layout(std140) uniform DrinkFrame {
    vec4 Frame;
};

in vec2 texCoord;

out vec4 fragColor;

const int MOST_TAPS = 32;
const float WIDTH_SHARE = 1.0;
const float DEPTH_SHARE = 1.0;
const float RADIUS_SCALE = 0.5;
const float COVERED = 0.002;

float distanceOf(float depth) {
    float ndc = Frame.x > 0.5 ? depth : depth * 2.0 - 1.0;
    return ProjMat[3][2] / (ndc + ProjMat[2][2]);
}

float depthOf(float away) {
    float ndc = ProjMat[3][2] / away - ProjMat[2][2];
    return Frame.x > 0.5 ? ndc : ndc * 0.5 + 0.5;
}

float falloff(float share) {
    float inside = 1.0 - share * share;
    return inside <= 0.0 ? 0.0 : inside * inside;
}

float weightOf(float offset, float depthDelta, float threshold) {
    return falloff(offset) * falloff(depthDelta / threshold);
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
    float width = clamp(WIDTH_SHARE * radius * pixelsPerBlock, 1.0, float(MOST_TAPS));
    int taps = int(ceil(width));
    float threshold = DEPTH_SHARE * radius;
    float weights = 0.0;
    float distances = 0.0;
    vec3 colors = vec3(0.0);
    for (int tap = -taps; tap <= taps; tap++) {
        ivec2 at = clamp(pixel + axis * tap, ivec2(0), size - 1);
        vec4 color = texelFetch(FluidColor, at, 0);
        if (color.a >= COVERED) {
            float there = distanceOf(texelFetch(FluidDepth, at, 0).r);
            float weight = weightOf(float(tap) / width, there - here, threshold);
            weights += weight;
            distances += weight * there;
            colors += weight * color.rgb;
        }
    }
    fragColor = vec4(colors / weights, center.a);
    gl_FragDepth = depthOf(distances / weights);
}
