#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
#moj_import "mingle_noise.glsl"

// One stream of an Unmake drink, drawn by marching the drink's field
// (decision unmake-waves-dissolve-by-crucible-cost): inside each proxy box
// the ray from the camera steps through the metaball field of the bodies that
// reach the box, every stream a soft capsule chain and every standing block a
// soft rounded box, each skeleton read at its least distance and all summed,
// until the field reaches the iso; the hit is refined by bisection, shaded
// from the field's gradient, lit by the lightmap and the cardinal lights,
// textured along the liquid and round the nearest segment or over the block's
// world axes, with the block's goo types mingled over it in blotches, and its
// depth written so the world occludes it. The constants mirror DrinkField,
// DrinkStream and DrinkRenderer, which DrinkShaderTest checks.

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

layout(std140) uniform DrinkStream {
    vec4 Coat;
    vec4 Tint;
    vec4 Sprite;
    vec4 Counts;
    vec4 LayerTint[3];
    vec4 LayerSprite[3];
    vec4 LayerShare[3];
    vec4 Frames[16];
    vec4 ProxyLow[64];
    vec4 ProxyHigh[64];
    ivec4 Table[384];
    vec4 Boxes[16];
    vec4 Rings[384];
};

in vec3 rayPoint;
flat in int proxy;

out vec4 fragColor;

const float DEPTH = 0.05;
const float REACH = 0.24;
const float FULL_RADIUS = 0.1;
const float THINNEST = 0.02;
const float GOO_REACH = 0.45;
const int RUN_START = 65536;
const int BOX_BASE = 4096;
const int MAX_STEPS = 96;
const float MIN_STEP = 0.01;
const float MAX_STEP = 0.05;
const float SLOPE_BOUND = 12.0;
const int REFINE_STEPS = 6;
const float NORMAL_STEP = 0.004;
const float FAR = 1.0e9;
const float STRAIGHT = 1.0e-6;

float falloff(float signedDistance) {
    float t = clamp((signedDistance + DEPTH) / (DEPTH + REACH), 0.0, 1.0);
    return 1.0 - t * t * (3.0 - 2.0 * t);
}

float scaled(float signedDistance, float radius) {
    float scale = min(1.0, radius / FULL_RADIUS);
    return scale > 0.0 ? signedDistance / scale : REACH;
}

int entryAt(int index) {
    return Table[index >> 2][index & 3];
}

// A body's reading at a point: x its scaled signed distance, y the gap to
// its field, positive where the point is outside the body's reach.
vec2 segmentRead(int segment, vec3 p) {
    vec4 a = Rings[2 * segment];
    vec4 b = Rings[2 * segment + 2];
    vec3 line = b.xyz - a.xyz;
    vec3 rel = p - a.xyz;
    float len2 = dot(line, line);
    float t = len2 == 0.0 ? 0.0 : clamp(dot(rel, line) / len2, 0.0, 1.0);
    float radius = mix(a.w, b.w, t);
    float d = length(rel - line * t) - radius;
    return vec2(scaled(d, radius), d - REACH * min(1.0, radius / FULL_RADIUS));
}

vec2 boxRead(int box, vec3 p) {
    vec4 centerHalf = Boxes[2 * box];
    float rounding = Boxes[2 * box + 1].x;
    vec3 q = abs(p - centerHalf.xyz) - (centerHalf.w - rounding);
    float outside = length(max(q, 0.0));
    float inside = min(max(q.x, max(q.y, q.z)), 0.0);
    float d = outside + inside - rounding;
    return vec2(scaled(d, centerHalf.w), d - REACH * min(1.0, centerHalf.w / FULL_RADIUS));
}

vec2 readBody(int entry, vec3 p) {
    int body = entry & (RUN_START - 1);
    return body >= BOX_BASE ? boxRead(body - BOX_BASE, p) : segmentRead(body, p);
}

// The field at a point over the proxy's bodies, each skeleton's run read at
// its least distance and the runs summed; gap is the least gap to any body's field.
float fieldAt(vec3 p, int first, int count, out float gap) {
    float value = 0.0;
    float least = REACH;
    gap = FAR;
    for (int e = 0; e < count; e++) {
        int entry = entryAt(first + e);
        if (e > 0 && (entry & RUN_START) != 0) {
            value += falloff(least);
            least = REACH;
        }
        vec2 read = readBody(entry, p);
        least = min(least, read.x);
        gap = min(gap, read.y);
    }
    return value + falloff(least);
}

float fieldOnly(vec3 p, int first, int count) {
    float gap;
    return fieldAt(p, first, count, gap);
}

int nearestEntry(vec3 p, int first, int count) {
    int nearest = 0;
    float least = FAR;
    for (int e = 0; e < count; e++) {
        int entry = entryAt(first + e);
        float d = readBody(entry, p).x;
        if (d < least) {
            least = d;
            nearest = entry;
        }
    }
    return nearest;
}

vec3 normalAt(vec3 p, int first, int count) {
    vec2 e = vec2(NORMAL_STEP, 0.0);
    vec3 gradient = vec3(
        fieldOnly(p + e.xyy, first, count) - fieldOnly(p - e.xyy, first, count),
        fieldOnly(p + e.yxy, first, count) - fieldOnly(p - e.yxy, first, count),
        fieldOnly(p + e.yyx, first, count) - fieldOnly(p - e.yyx, first, count));
    return dot(gradient, gradient) > 0.0 ? -normalize(gradient) : vec3(0.0, 1.0, 0.0);
}

bool slab(vec3 lo, vec3 hi, vec3 rd, out float tEnter, out float tExit) {
    vec3 inv = 1.0 / rd;
    vec3 t0 = lo * inv;
    vec3 t1 = hi * inv;
    vec3 tmin = min(t0, t1);
    vec3 tmax = max(t0, t1);
    tEnter = max(max(tmin.x, tmin.y), tmin.z);
    tExit = min(min(tmax.x, tmax.y), tmax.z);
    return tExit > max(tEnter, 0.0);
}

float textureAt(float along) {
    float laid = along * 0.5;
    float wrapped = laid - floor(laid);
    return 1.0 - abs(2.0 * wrapped - 1.0);
}

vec2 uvOf(vec4 sprite, vec2 place) {
    return sprite.xy + (sprite.zw - sprite.xy) * vec2(textureAt(place.x), textureAt(place.y));
}

// A point's place on a stream's texture: along the liquid at its foot on the
// segment, and round the spine by arc length in the stream's own frame.
vec2 placeOnSegment(int segment, vec3 p, out float route) {
    vec4 a = Rings[2 * segment];
    vec4 b = Rings[2 * segment + 2];
    vec4 ma = Rings[2 * segment + 1];
    vec4 mb = Rings[2 * segment + 3];
    vec3 line = b.xyz - a.xyz;
    float len2 = dot(line, line);
    float t = len2 == 0.0 ? 0.0 : clamp(dot(p - a.xyz, line) / len2, 0.0, 1.0);
    vec3 offset = p - (a.xyz + line * t);
    int frame = int(ma.z);
    float angle = atan(dot(offset, Frames[2 * frame + 1].xyz), dot(offset, Frames[2 * frame].xyz));
    float radius = max(THINNEST, mix(a.w, b.w, t));
    route = mix(ma.y, mb.y, t);
    return vec2(mix(ma.x, mb.x, t), angle * radius);
}

vec2 placeOnBlock(vec3 world, vec3 n) {
    vec3 a = abs(n);
    if (a.y >= a.x && a.y >= a.z) {
        return world.xz;
    }
    return a.x >= a.z ? world.zy : world.xy;
}

vec4 mingled(vec4 color, vec3 world, vec2 place, float route) {
    float reach = route * GOO_REACH;
    int layers = int(Counts.w);
    for (int l = 0; l < layers; l++) {
        float lo = 1.0 - reach * (1.0 - LayerShare[l].x);
        float hi = 1.0 - reach * (1.0 - LayerShare[l].y);
        float share = hi > 0.0 ? (hi - lo) / hi : 0.0;
        float opacity = mingleOpacity(world, GameTime, share, LayerShare[l].z);
        vec4 goo = texture(Sampler0, uvOf(LayerSprite[l], place)) * vec4(LayerTint[l].rgb, 1.0);
        color.rgb = mix(color.rgb, goo.rgb, opacity * goo.a);
    }
    return color;
}

void main() {
    vec3 rd = normalize(rayPoint);
    rd += vec3(lessThan(abs(rd), vec3(STRAIGHT))) * STRAIGHT;
    int first = int(ProxyLow[proxy].w);
    int count = int(ProxyHigh[proxy].w);
    float tEnter;
    float tExit;
    if (!slab(ProxyLow[proxy].xyz, ProxyHigh[proxy].xyz, rd, tEnter, tExit)) {
        discard;
    }
    float iso = falloff(0.0);
    float t = max(tEnter, 0.0);
    float gap;
    float f = fieldAt(rd * t, first, count, gap);
    if (f >= iso) {
        discard;
    }
    float before = t;
    bool hit = false;
    for (int i = 0; i < MAX_STEPS && !hit; i++) {
        float step = gap > 0.0 ? max(gap, MIN_STEP) : clamp((iso - f) / SLOPE_BOUND, MIN_STEP, MAX_STEP);
        before = t;
        t += step;
        if (t > tExit) {
            break;
        }
        f = fieldAt(rd * t, first, count, gap);
        hit = f >= iso;
    }
    if (!hit) {
        discard;
    }
    float outside = before;
    float inside = t;
    for (int i = 0; i < REFINE_STEPS; i++) {
        float mid = 0.5 * (outside + inside);
        if (fieldOnly(rd * mid, first, count) >= iso) {
            inside = mid;
        } else {
            outside = mid;
        }
    }
    vec3 p = rd * inside;
    vec3 n = normalAt(p, first, count);
    int body = nearestEntry(p, first, count) & (RUN_START - 1);
    vec3 world = p + vec3(CameraBlockPos) - CameraOffset;
    float route = 0.0;
    vec2 place = body >= BOX_BASE ? placeOnBlock(world, n) : placeOnSegment(body, p, route);
    vec4 color = texture(Sampler0, uvOf(Sprite, place)) * vec4(Tint.rgb, 1.0);
    color = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(mat3(ModelViewMat) * n), color);
    color *= sample_lightmap(Sampler2, ivec2(int(Coat.x), int(Coat.y)));
    if (Coat.z < 0.5) {
        color = mingled(color, world, place, route);
    }
    color.a = 1.0;
    fragColor = apply_fog(color, fog_spherical_distance(p), fog_cylindrical_distance(p), FogEnvironmentalStart,
        FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
    vec4 clip = ProjMat * ModelViewMat * vec4(p, 1.0);
    float ndc = clip.z / clip.w;
    gl_FragDepth = Coat.w > 0.5 ? ndc : ndc * 0.5 + 0.5;
}
