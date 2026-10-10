#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
#moj_import "mingle_noise.glsl"

// One region of an Unmake drink, drawn by marching the drink's field
// (decision unmake-waves-dissolve-by-crucible-cost): inside the region's box
// the ray from the camera steps through the metaball field of the bodies that
// reach the region, every stream a soft capsule chain and every standing
// block a soft rounded box, each stream's run read at its least distance only
// where the point is within the box its bodies reach, the gap to the others'
// boxes being the jump, and the runs summed, until the field reaches the iso;
// the hit is refined by bisection, shaded from the gradient of the nearest
// stream's own field, lit by the lightmap and the cardinal lights, textured
// along the liquid and round the nearest segment or over the block's world
// axes in its own stream's coat: the block's own texture from the block
// through its funnel and the block's goo types alone past the funnel's end,
// mingled in blotches, one boundary and no blend; its depth is written so the
// world occludes it. The constants mirror DrinkField, DrinkStream, DrinkBody
// and DrinkUpload, which DrinkShaderTest checks.

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

layout(std140) uniform DrinkRegion {
    vec4 Region;
    vec4 Counts;
    vec4 Streams[256];
    ivec4 Table[256];
    vec4 Boxes[16];
    vec4 Rings[448];
};

in vec3 rayPoint;

out vec4 fragColor;

const float DEPTH = 0.05;
const float REACH = 0.24;
const float FULL_RADIUS = 0.1;
const float THINNEST = 0.02;
const float FUNNEL_END = 3.0;
const float REGION_SPAN = 1.0;
const int STREAM_VEC4S = 16;
const int COAT_SLOT = 0;
const int TINT_SLOT = 1;
const int SPRITE_SLOT = 2;
const int SIDE_SLOT = 3;
const int ACROSS_SLOT = 4;
const int LAYER_TINT_SLOT = 5;
const int LAYER_SPRITE_SLOT = 8;
const int LAYER_SHARE_SLOT = 11;
const int RUN_LOW_SLOT = 14;
const int RUN_HIGH_SLOT = 15;
const int RUN_START = 65536;
const int BOX_BASE = 4096;
const int MAX_STEPS = 96;
const float MIN_STEP = 0.01;
const float MAX_STEP = 0.08;
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

vec4 streamSlot(int stream, int slot) {
    return Streams[stream * STREAM_VEC4S + slot];
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

// One stream's part of the field at a point: its run read at its least
// distance where the point is within the box its bodies reach, nothing
// outside it, the gap then measured to the box so the march can jump to it.
float runField(vec3 p, int stream, inout float gap) {
    vec4 low = streamSlot(stream, RUN_LOW_SLOT);
    vec4 high = streamSlot(stream, RUN_HIGH_SLOT);
    float outside = length(max(max(low.xyz - p, p - high.xyz), vec3(0.0)));
    if (outside > 0.0) {
        gap = min(gap, outside);
        return 0.0;
    }
    float least = REACH;
    int first = int(low.w);
    int end = first + int(high.w);
    for (int e = first; e < end; e++) {
        vec2 read = readBody(entryAt(e), p);
        least = min(least, read.x);
        gap = min(gap, read.y);
    }
    return falloff(least);
}

// The field at a point over the region's streams, the runs summed; gap is
// the least gap to any body's field, or to a run's box the point is outside.
float fieldAt(vec3 p, int streams, out float gap) {
    float value = 0.0;
    gap = FAR;
    for (int s = 0; s < streams; s++) {
        value += runField(p, s, gap);
    }
    return value;
}

float fieldOnly(vec3 p, int streams) {
    float gap;
    return fieldAt(p, streams, gap);
}

float runOnly(vec3 p, int stream) {
    float gap = FAR;
    return runField(p, stream, gap);
}

int nearestBody(vec3 p, int count) {
    int nearest = 0;
    float least = FAR;
    for (int e = 0; e < count; e++) {
        int entry = entryAt(e);
        float d = readBody(entry, p).x;
        if (d < least) {
            least = d;
            nearest = entry;
        }
    }
    return nearest & (RUN_START - 1);
}

// The normal from the nearest stream's own field alone, the stream the hit wears.
vec3 normalAt(vec3 p, int stream) {
    vec2 e = vec2(NORMAL_STEP, 0.0);
    vec3 gradient = vec3(
        runOnly(p + e.xyy, stream) - runOnly(p - e.xyy, stream),
        runOnly(p + e.yxy, stream) - runOnly(p - e.yxy, stream),
        runOnly(p + e.yyx, stream) - runOnly(p - e.yyx, stream));
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
// segment, and round the spine by arc length in the stream's own frame;
// along is the foot's blocks along the block's route.
vec2 placeOnSegment(int segment, int stream, vec3 p, out float along) {
    vec4 a = Rings[2 * segment];
    vec4 b = Rings[2 * segment + 2];
    vec4 ma = Rings[2 * segment + 1];
    vec4 mb = Rings[2 * segment + 3];
    vec3 line = b.xyz - a.xyz;
    float len2 = dot(line, line);
    float t = len2 == 0.0 ? 0.0 : clamp(dot(p - a.xyz, line) / len2, 0.0, 1.0);
    vec3 offset = p - (a.xyz + line * t);
    float angle = atan(dot(offset, streamSlot(stream, ACROSS_SLOT).xyz), dot(offset, streamSlot(stream, SIDE_SLOT).xyz));
    float radius = max(THINNEST, mix(a.w, b.w, t));
    along = mix(ma.w, mb.w, t);
    return vec2(mix(ma.x, mb.x, t), angle * radius);
}

vec2 placeOnBlock(vec3 world, vec3 n) {
    vec3 a = abs(n);
    if (a.y >= a.x && a.y >= a.z) {
        return world.xz;
    }
    return a.x >= a.z ? world.zy : world.xy;
}

// The block's goo types over the skin past the funnel: each type's blotches
// cover its share of what the types before it left, so together they cover
// the skin whole and the block's texture shows nowhere.
vec4 mingled(vec4 color, int stream, vec3 world, vec2 place) {
    int layers = int(streamSlot(stream, COAT_SLOT).z);
    for (int l = 0; l < layers; l++) {
        vec4 share = streamSlot(stream, LAYER_SHARE_SLOT + l);
        float conditional = share.y > 0.0 ? (share.y - share.x) / share.y : 0.0;
        float opacity = mingleOpacity(world, GameTime, conditional, share.z);
        vec4 goo = texture(Sampler0, uvOf(streamSlot(stream, LAYER_SPRITE_SLOT + l), place))
            * vec4(streamSlot(stream, LAYER_TINT_SLOT + l).rgb, 1.0);
        color.rgb = mix(color.rgb, goo.rgb, opacity * goo.a);
    }
    return color;
}

void main() {
    vec3 rd = normalize(rayPoint);
    rd += vec3(lessThan(abs(rd), vec3(STRAIGHT))) * STRAIGHT;
    int streams = int(Counts.x);
    int count = int(Counts.w);
    float tEnter;
    float tExit;
    if (!slab(Region.xyz, Region.xyz + vec3(REGION_SPAN), rd, tEnter, tExit)) {
        discard;
    }
    float iso = falloff(0.0);
    float t = max(tEnter, 0.0);
    float gap;
    float f = fieldAt(rd * t, streams, gap);
    // Inside the field at the entry: from another region the surface is at the entry itself, which that region's
    // last step may have missed; from the camera the eye is inside the goo and sees through it.
    if (f >= iso && tEnter <= 0.0) {
        discard;
    }
    bool hit = f >= iso;
    float before = t;
    for (int i = 0; i < MAX_STEPS && !hit; i++) {
        float step = gap > 0.0 ? max(gap, MIN_STEP) : clamp((iso - f) / SLOPE_BOUND, MIN_STEP, MAX_STEP);
        before = t;
        t = min(t + step, tExit);
        f = fieldAt(rd * t, streams, gap);
        hit = f >= iso;
        if (!hit && t >= tExit) {
            break;
        }
    }
    if (!hit) {
        discard;
    }
    float outside = before;
    float inside = t;
    for (int i = 0; i < REFINE_STEPS; i++) {
        float mid = 0.5 * (outside + inside);
        if (fieldOnly(rd * mid, streams) >= iso) {
            inside = mid;
        } else {
            outside = mid;
        }
    }
    vec3 p = rd * inside;
    int body = nearestBody(p, count);
    bool onBlock = body >= BOX_BASE;
    int stream = int(onBlock ? Boxes[2 * (body - BOX_BASE) + 1].y : Rings[2 * body + 1].z);
    vec3 n = normalAt(p, stream);
    vec3 world = p + vec3(CameraBlockPos) - CameraOffset;
    float along = 0.0;
    vec2 place = onBlock ? placeOnBlock(world, n) : placeOnSegment(body, stream, p, along);
    vec4 coat = streamSlot(stream, COAT_SLOT);
    vec4 color = texture(Sampler0, uvOf(streamSlot(stream, SPRITE_SLOT), place))
        * vec4(streamSlot(stream, TINT_SLOT).rgb, 1.0);
    // The block's own texture through the funnel, the goo's wholly past the funnel's end: one boundary, no blend.
    if (!onBlock && along >= FUNNEL_END) {
        color = mingled(color, stream, world, place);
    }
    color = minecraft_mix_light(Light0_Direction, Light1_Direction, normalize(mat3(ModelViewMat) * n), color);
    color *= sample_lightmap(Sampler2, ivec2(int(coat.x), int(coat.y)));
    color.a = 1.0;
    fragColor = apply_fog(color, fog_spherical_distance(p), fog_cylindrical_distance(p), FogEnvironmentalStart,
        FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
    vec4 clip = ProjMat * ModelViewMat * vec4(p, 1.0);
    float ndc = clip.z / clip.w;
    gl_FragDepth = Region.w > 0.5 ? ndc : ndc * 0.5 + 0.5;
}
