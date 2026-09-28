#version 330

// Frost goo's burnout explosion (decision elemental-explosion-per-type): a
// frost nova. Voronoi ice facets sharpen across the sphere from a soft
// D5FFFF haze into crisp ADD8E6 edges with white glints, under a bright
// fresnel rim. TRANSLUCENT blend.

in vec3 viewPos;
in vec3 viewNormal;
in vec3 surfaceDir;
in float crystallized;
in float remaining;

out vec4 fragColor;

const vec3 HAZE_COLOR = vec3(0.835, 1.0, 1.0);
const vec3 FACET_COLOR = vec3(0.678, 0.847, 0.902);
const vec3 GLINT_COLOR = vec3(1.0, 1.0, 1.0);
const float FACET_SCALE = 5.0;
const float HAZE_OPACITY = 0.2;
const float RIM_OPACITY = 0.55;
const float EDGE_OPACITY = 0.7;

vec3 hash3(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)), dot(p, vec3(269.5, 183.3, 246.1)), dot(p, vec3(113.5, 271.9, 124.6)));
    return fract(sin(p) * 43758.5453);
}

// Distance to the nearest facet center and to the second nearest; their
// gap is small along a facet's edge.
vec2 voronoi(vec3 x) {
    vec3 cell = floor(x);
    vec3 local = fract(x);
    float nearest = 8.0;
    float second = 8.0;
    for (int k = -1; k <= 1; k++) {
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                vec3 offset = vec3(float(i), float(j), float(k));
                vec3 toPoint = offset + hash3(cell + offset) - local;
                float d = dot(toPoint, toPoint);
                if (d < nearest) {
                    second = nearest;
                    nearest = d;
                } else if (d < second) {
                    second = d;
                }
            }
        }
    }
    return vec2(sqrt(nearest), sqrt(second));
}

void main() {
    vec2 cells = voronoi(surfaceDir * FACET_SCALE);
    float gap = cells.y - cells.x;
    // The edge line narrows as the frost crystallizes: a wide soft blur
    // at first, a crisp line at the end.
    float width = mix(0.35, 0.05, crystallized);
    float edge = 1.0 - smoothstep(0.0, width, gap);
    float glint = step(0.97, hash3(floor(surfaceDir * FACET_SCALE * 3.0)).x) * crystallized;

    float facing = abs(dot(normalize(viewNormal), normalize(-viewPos)));
    float rim = pow(1.0 - facing, 2.0);

    vec3 color = mix(HAZE_COLOR, FACET_COLOR, edge * crystallized);
    color = mix(color, GLINT_COLOR, max(glint, rim * 0.6));
    float alpha = HAZE_OPACITY + RIM_OPACITY * rim + EDGE_OPACITY * edge * crystallized + glint;
    fragColor = vec4(color, clamp(alpha * remaining, 0.0, 1.0));
}
