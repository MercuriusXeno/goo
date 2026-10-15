#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

// One region of an Unmake drink, drawn by marching the drink's field
// (decision unmake-waves-dissolve-by-crucible-cost): inside the region's box
// the ray from the camera steps through the metaball field of the bodies that
// reach the region, every stream a smooth tube along a curve through its
// rings and every standing block a rounded box, each stream's run read only
// where the point is within the box its bodies reach, the gap to the others'
// boxes being the jump, and the two nearest streams filleted together, until
// the field reaches the iso;
// the hit is refined by bisection, shaded from the gradient of the whole
// field so skin two streams share is one smooth shape, painted as the skins
// of the two streams most present there blended by their presence so a
// tributary folding into its trunk shows no seam, lit by the lightmap and
// the cardinal lights; its depth is written so the world occludes it.
// Each stream's skin is a cyclone, grooves twisting about its own axis by the
// phase its rings carry and turning as the goo travels.
// No texture anywhere (decision unmake-skin-is-a-solid-sampled-color): each
// stream's skin is one solid color, its block's sampled color at the block
// crossfading to its goo's color at the hand by the point's share of the
// route, so there is nothing to stretch. The hit glistens with a specular
// highlight and a rim brightening so the blob reads as a lit 3D liquid
// (decision unmake-metaball-glistens-and-shades). The constants mirror
// DrinkField, DrinkUpload and DrinkShaderTest, which checks them.

uniform sampler2D Sampler2;

layout(std140) uniform DrinkRegion {
    vec4 Region;
    vec4 Counts;
    vec4 Streams[240];
    ivec4 Table[64];
    vec4 Boxes[64];
    vec4 Rings[576];
};

in vec3 rayPoint;

out vec4 fragColor;

const float DEPTH = 0.05;
const float REACH = 0.24;
const float FULL_RADIUS = 0.1;
const float SOFTNESS = 0.08;
const float REGION_SPAN = 1.0;
const int STREAM_VEC4S = 6;
const int COAT_SLOT = 0;
const int BLOCK_COLOR_SLOT = 1;
const int GOO_COLOR_SLOT = 2;
const int RUN_LOW_SLOT = 3;
const int RUN_HIGH_SLOT = 4;
const int SIDE_SLOT = 5;
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
const float GLISTEN_POWER = 48.0;
const float GLISTEN_STRENGTH = 0.35;
const float RIM_POWER = 3.0;
const float RIM_STRENGTH = 0.25;

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

// The signed distance to the hull of a segment's two ring spheres, a cone
// tangent to both.
float roundCone(vec3 rel, vec3 line, float first, float last) {
    float len2 = dot(line, line);
    float fall = first - last;
    float slant = len2 - fall * fall;
    float toFirst = length(rel) - first;
    if (slant <= 0.0) {
        return first >= last ? toFirst : length(rel - line) - last;
    }
    float along = dot(rel, line);
    float past = along - len2;
    vec3 sideways = rel * len2 - line * along;
    float side = dot(sideways, sideways);
    float alongSquared = along * along * len2;
    float pastSquared = past * past * len2;
    float edge = sign(fall) * fall * fall * side;
    if (sign(past) * slant * pastSquared > edge) {
        return sqrt(side + pastSquared) / len2 - last;
    }
    if (sign(along) * slant * alongSquared < edge) {
        return toFirst;
    }
    return (sqrt(side * slant / len2) + along * fall) / len2 - first;
}

// A segment's reading at a point: the sphere at each of its two rings swept
// from one to the other, the round cone hull of the two, so a stream is
// spheres all along its path, each as wide as the stream is there, and two
// segments meeting at a ring share that ring's sphere exactly. read.x is the
// scaled signed distance, read.y the gap to the segment's field; past its
// reach the segment reads REACH without the cone.
void segmentRead(int segment, vec3 p, out vec2 read) {
    vec4 a = Rings[2 * segment];
    vec4 b = Rings[2 * segment + 2];
    vec3 line = b.xyz - a.xyz;
    float len2 = dot(line, line);
    float widest = max(a.w, b.w);
    float lineT = len2 == 0.0 ? 0.0 : clamp(dot(p - a.xyz, line) / len2, 0.0, 1.0);
    read.y = length(p - a.xyz - line * lineT) - widest - REACH * min(1.0, widest / FULL_RADIUS);
    read.x = read.y > 0.0 ? REACH : scaled(roundCone(p - a.xyz, line, a.w, b.w), mix(a.w, b.w, lineT));
}

// The smooth union of two readings, by the same soft minimum as the drink's
// streams: a lone reading's own value, nearer where the two crowd together.
float softUnion(float a, float b) {
    float none = exp(-REACH / SOFTNESS);
    return -SOFTNESS * log(max(0.0, exp(-a / SOFTNESS) - none) + max(0.0, exp(-b / SOFTNESS) - none) + none);
}

vec2 boxRead(int box, vec3 p, out vec3 facing) {
    vec4 centerHalf = Boxes[2 * box];
    float rounding = Boxes[2 * box + 1].x;
    vec3 rel = p - centerHalf.xyz;
    vec3 q = abs(rel) - (centerHalf.w - rounding);
    vec3 beyond = max(q, 0.0);
    float outside = length(beyond);
    float inside = min(max(q.x, max(q.y, q.z)), 0.0);
    float d = outside + inside - rounding;
    if (outside > 0.0) {
        facing = sign(rel) * beyond / outside;
    } else if (q.x >= q.y && q.x >= q.z) {
        facing = vec3(sign(rel.x), 0.0, 0.0);
    } else if (q.y >= q.z) {
        facing = vec3(0.0, sign(rel.y), 0.0);
    } else {
        facing = vec3(0.0, 0.0, sign(rel.z));
    }
    return vec2(scaled(d, centerHalf.w), d - REACH * min(1.0, centerHalf.w / FULL_RADIUS));
}

// A body's scaled signed distance at a point, for picking a stream's nearest body.
float readBody(int entry, vec3 p) {
    int body = entry & (RUN_START - 1);
    vec3 facing;
    if (body >= BOX_BASE) {
        return boxRead(body - BOX_BASE, p, facing).x;
    }
    vec2 read;
    segmentRead(body, p, read);
    return read.x;
}

// One stream's reading at a point: the nearest of its swept spheres, its
// block's body unioned onto them by the soft minimum, where the point is
// within the box its bodies reach; nothing outside it, the gap then measured
// to the box so the march can jump to it.
float runField(vec3 p, int stream, inout float gap, out vec3 facing) {
    facing = vec3(0.0, 1.0, 0.0);
    vec4 low = streamSlot(stream, RUN_LOW_SLOT);
    vec4 high = streamSlot(stream, RUN_HIGH_SLOT);
    float outside = length(max(max(low.xyz - p, p - high.xyz), vec3(0.0)));
    if (outside > 0.0) {
        gap = min(gap, outside);
        return REACH;
    }
    float least = REACH;
    float boxed = REACH;
    int first = int(low.w);
    int end = first + int(high.w);
    for (int e = first; e < end; e++) {
        int body = entryAt(e) & (RUN_START - 1);
        if (body >= BOX_BASE) {
            vec3 boxFacing;
            vec2 read = boxRead(body - BOX_BASE, p, boxFacing);
            gap = min(gap, read.y);
            boxed = min(read.x, REACH);
            continue;
        }
        vec2 read;
        segmentRead(body, p, read);
        gap = min(gap, read.y);
        least = min(least, read.x);
    }
    return softUnion(least, boxed);
}

// The field at a point over the region's streams: every stream within reach
// unioned smoothly at once by the soft minimum, so the skin is one surface
// however many streams crowd a place; gap is the least gap to any body's
// field, or to a run's box the point is outside.
float fieldAt(vec3 p, int streams, out float gap) {
    gap = FAR;
    float none = exp(-REACH / SOFTNESS);
    float sum = 0.0;
    for (int s = 0; s < streams; s++) {
        vec3 facing;
        float d = runField(p, s, gap, facing);
        sum += max(0.0, exp(-d / SOFTNESS) - none);
    }
    return falloff(-SOFTNESS * log(sum + none));
}

float fieldOnly(vec3 p, int streams) {
    float gap;
    return fieldAt(p, streams, gap);
}

float runOnly(vec3 p, int stream) {
    float gap = FAR;
    vec3 facing;
    return falloff(runField(p, stream, gap, facing));
}

// The body of one stream nearest a point, by its scaled signed distance.
int nearestBodyOf(vec3 p, int stream) {
    vec4 low = streamSlot(stream, RUN_LOW_SLOT);
    vec4 high = streamSlot(stream, RUN_HIGH_SLOT);
    int first = int(low.w);
    int end = first + int(high.w);
    int nearest = entryAt(first) & (RUN_START - 1);
    float least = FAR;
    for (int e = first; e < end; e++) {
        int entry = entryAt(e) & (RUN_START - 1);
        float d = readBody(entry, p);
        if (d < least) {
            least = d;
            nearest = entry;
        }
    }
    return nearest;
}

// The normal of the whole field, so skin two streams share is one smooth shape with no crease between them.
vec3 normalAt(vec3 p, int streams) {
    vec2 e = vec2(NORMAL_STEP, 0.0);
    vec3 gradient = vec3(
        fieldOnly(p + e.xyy, streams) - fieldOnly(p - e.xyy, streams),
        fieldOnly(p + e.yxy, streams) - fieldOnly(p - e.yxy, streams),
        fieldOnly(p + e.yyx, streams) - fieldOnly(p - e.yyx, streams));
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

// A point's share of the block's route at its foot on a segment, 0 at the
// block and 1 at the hand.
float routeOnSegment(int segment, vec3 p) {
    vec4 a = Rings[2 * segment];
    vec4 b = Rings[2 * segment + 2];
    vec3 line = b.xyz - a.xyz;
    float len2 = dot(line, line);
    float t = len2 == 0.0 ? 0.0 : clamp(dot(p - a.xyz, line) / len2, 0.0, 1.0);
    return mix(Rings[2 * segment + 1].y, Rings[2 * segment + 3].y, t);
}

// One stream's skin at a hit: its block's color crossfading to its goo's
// color by the route share of its nearest body, the standing block wearing
// the block's color whole, in its own light.
vec4 skinOf(int stream, vec3 p) {
    int body = nearestBodyOf(p, stream);
    float route = body >= BOX_BASE ? 0.0 : routeOnSegment(body, p);
    vec4 coat = streamSlot(stream, COAT_SLOT);
    vec4 color = vec4(mix(streamSlot(stream, BLOCK_COLOR_SLOT).rgb, streamSlot(stream, GOO_COLOR_SLOT).rgb, route), 1.0);
    return color * sample_lightmap(Sampler2, ivec2(int(coat.x), int(coat.y)));
}

// The skin at a hit: the skins of the two streams most present there, blended
// by their presence, so where a tributary folds into its trunk the skin
// passes smoothly from one to the other with no seam where a pick would flip.
vec4 skinAtHit(vec3 p, int streams) {
    int first = 0;
    float firstField = -1.0;
    int second = 0;
    float secondField = -1.0;
    for (int s = 0; s < streams; s++) {
        float f = runOnly(p, s);
        if (f > firstField) {
            second = first;
            secondField = firstField;
            first = s;
            firstField = f;
        } else if (f > secondField) {
            second = s;
            secondField = f;
        }
    }
    vec4 color = skinOf(first, p);
    if (secondField > 0.0) {
        color = mix(color, skinOf(second, p), secondField / (firstField + secondField));
    }
    return color;
}

// The solid skin shaded as a lit liquid (decision unmake-metaball-glistens-and-shades):
// a white-leaning Blinn-Phong highlight from each cardinal light and a rim
// brightening where the surface turns away from the eye, so the blob reads as
// round wet volume rather than a flat disc. n and view are in view space.
vec4 glisten(vec4 color, vec3 n, vec3 view) {
    float shine = pow(max(dot(n, normalize(Light0_Direction + view)), 0.0), GLISTEN_POWER)
        + pow(max(dot(n, normalize(Light1_Direction + view)), 0.0), GLISTEN_POWER);
    float rim = pow(1.0 - max(dot(n, view), 0.0), RIM_POWER);
    color.rgb += vec3(shine * GLISTEN_STRENGTH + rim * RIM_STRENGTH);
    return color;
}

void main() {
    vec3 rd = normalize(rayPoint);
    rd += vec3(lessThan(abs(rd), vec3(STRAIGHT))) * STRAIGHT;
    int streams = int(Counts.x);
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
    // Out of steps short of the region's exit: the surface may still lie
    // before it, so the exit is read and the hit refined between, or the
    // region past this one, entering inside the goo, drew a flat face there
    // and cut the goo on the block grid.
    if (!hit && t < tExit) {
        before = t;
        t = tExit;
        hit = fieldOnly(rd * t, streams) >= iso;
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
    vec3 n = normalAt(p, streams);
    vec3 viewNormal = normalize(mat3(ModelViewMat) * n);
    vec3 toEye = normalize(mat3(ModelViewMat) * -p);
    vec4 color = glisten(skinAtHit(p, streams), viewNormal, toEye);
    color = minecraft_mix_light(Light0_Direction, Light1_Direction, viewNormal, color);
    color.a = 1.0;
    fragColor = apply_fog(color, fog_spherical_distance(p), fog_cylindrical_distance(p), FogEnvironmentalStart,
        FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
    vec4 clip = ProjMat * ModelViewMat * vec4(p, 1.0);
    float ndc = clip.z / clip.w;
    gl_FragDepth = Region.w > 0.5 ? ndc : ndc * 0.5 + 0.5;
}
