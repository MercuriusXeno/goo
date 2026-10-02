#version 330

// Crystal goo's burnout explosion (decision elemental-explosion-per-type): a
// prism burst. The glass shell is cut into voronoi facets whose edges split
// light into a thin rainbow band about 77E9FF, with bright cyan glints;
// as it shatters, facets drop out one by one by their cell's hash.
// TRANSLUCENT blend.

in vec3 viewPos;
in vec3 viewNormal;
in vec3 surfaceDir;
in float progress;
in float opacity;
in float shattered;

out vec4 fragColor;

const vec3 GLASS_COLOR = vec3(0.467, 0.914, 1.0);
const vec3 GLINT_COLOR = vec3(0.85, 1.0, 1.0);
const float FACET_SCALE = 4.0;
const float EDGE_WIDTH = 0.08;
const float GLASS_OPACITY = 0.18;
const float EDGE_OPACITY = 0.8;
const float RAINBOW_SPREAD = 6.0;

vec3 hash3(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)), dot(p, vec3(269.5, 183.3, 246.1)), dot(p, vec3(113.5, 271.9, 124.6)));
    return fract(sin(p) * 43758.5453);
}

// The nearest facet's cell, the distance to its center, and to the
// second nearest center; their gap is small along a facet's edge.
vec3 voronoi(vec3 x, out vec3 nearestCell) {
    vec3 cell = floor(x);
    vec3 local = fract(x);
    float nearest = 8.0;
    float second = 8.0;
    nearestCell = cell;
    for (int k = -1; k <= 1; k++) {
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                vec3 offset = vec3(float(i), float(j), float(k));
                vec3 toPoint = offset + hash3(cell + offset) - local;
                float d = dot(toPoint, toPoint);
                if (d < nearest) {
                    second = nearest;
                    nearest = d;
                    nearestCell = cell + offset;
                } else if (d < second) {
                    second = d;
                }
            }
        }
    }
    return vec3(sqrt(nearest), sqrt(second), 0.0);
}

vec3 rainbow(float t) {
    return 0.5 + 0.5 * cos(6.28318 * (t + vec3(0.0, 0.33, 0.67)));
}

void main() {
    vec3 cell;
    vec3 cells = voronoi(surfaceDir * FACET_SCALE, cell);
    if (hash3(cell).z < shattered) {
        discard;
    }
    float gap = cells.y - cells.x;
    float edge = 1.0 - smoothstep(0.0, EDGE_WIDTH, gap);

    float facing = abs(dot(normalize(viewNormal), normalize(-viewPos)));
    vec3 prism = rainbow(gap * RAINBOW_SPREAD + facing + progress);
    vec3 color = mix(GLASS_COLOR, prism, edge * 0.7);
    float glint = step(0.93, hash3(cell + vec3(floor(progress * 8.0))).x) * (1.0 - facing);
    color = mix(color, GLINT_COLOR, glint);

    float alpha = GLASS_OPACITY + EDGE_OPACITY * edge + glint * 0.5;
    fragColor = vec4(color, clamp(alpha, 0.0, 1.0) * opacity);
}
