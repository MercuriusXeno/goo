#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

// One splat of an Unmake drink into the fluid target (decision
// unmake-drink-draws-as-a-screen-space-fluid): the eye ray meets the splat's
// sphere, or the block's rounded cube by stepping its signed distance from
// the bounding sphere's entry, a miss is discarded, and the hit writes the
// shape's own surface depth, not the quad's, with its color in the level's
// light and its radius over RADIUS_SCALE as its coverage, which the blur sizes
// its kernel by, against the level's depth copied into the target so walls
// hide the goo. Overlapping splats keep the nearest surface; the blur makes
// them one skin.

uniform sampler2D Sampler2;

layout(std140) uniform DrinkFrame {
    vec4 Frame;
};

in vec3 rayPoint;
in vec4 splatColor;
in vec4 shape;
in float rounding;
flat in ivec2 light;

out vec4 fragColor;

const int BOX_STEPS = 24;
const float BOX_HIT = 0.0005;
const float CORNER = 1.7320508;
const float RADIUS_SCALE = 0.5;
const float LEAST_COVERAGE = 0.004;

float roundedBox(vec3 rel, float halfWidth, float round) {
    vec3 q = abs(rel) - (halfWidth - round);
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - round;
}

void main() {
    vec3 rd = normalize(rayPoint);
    vec3 center = shape.xyz;
    float halfWidth = shape.w;
    float bound = (halfWidth - rounding) * CORNER + rounding;
    float along = dot(rd, center);
    float h = along * along - dot(center, center) + bound * bound;
    if (h < 0.0) {
        discard;
    }
    float t = along - sqrt(h);
    if (t <= 0.0) {
        discard;
    }
    if (rounding < halfWidth) {
        float exit = along + sqrt(h);
        bool hit = false;
        for (int i = 0; i < BOX_STEPS && !hit && t <= exit; i++) {
            float d = roundedBox(rd * t - center, halfWidth, rounding);
            hit = d < BOX_HIT;
            t += hit ? 0.0 : d;
        }
        if (!hit) {
            discard;
        }
    }
    vec3 p = rd * t;
    float coverage = max(halfWidth / RADIUS_SCALE, LEAST_COVERAGE);
    fragColor = vec4(splatColor.rgb * sample_lightmap(Sampler2, light).rgb, coverage);
    vec4 clip = ProjMat * ModelViewMat * vec4(p, 1.0);
    float ndc = clip.z / clip.w;
    gl_FragDepth = Frame.x > 0.5 ? ndc : ndc * 0.5 + 0.5;
}
