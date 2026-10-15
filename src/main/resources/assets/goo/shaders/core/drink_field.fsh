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
const float BOX_BLEND = 0.15;
const float MEET_BLEND = 0.2;
const float REGION_SPAN = 1.0;
const int STREAM_VEC4S = 6;
const int COAT_SLOT = 0;
const int BLOCK_COLOR_SLOT = 1;
const int GOO_COLOR_SLOT = 2;
const int RUN_LOW_SLOT = 3;
const int RUN_HIGH_SLOT = 4;
const int SIDE_SLOT = 5;
const int LOBES = 2;
const float SPIN_DEPTH = 0.25;
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

// Whether two of the block's rings are of one stream: a stream's rings are
// copied together, with the ring before and after the segments it lists.
bool sameStream(int ring, int other) {
    return other >= 0 && other < int(Counts.y) && Rings[2 * other + 1].z == Rings[2 * ring + 1].z;
}

// The direction a stream runs through a ring, from the ring before it to the
// ring after it, so the two segments meeting there share it.
vec3 tangentAt(int ring) {
    int before = sameStream(ring, ring - 1) ? ring - 1 : ring;
    int after = sameStream(ring, ring + 1) ? ring + 1 : ring;
    vec3 run = Rings[2 * after].xyz - Rings[2 * before].xyz;
    return dot(run, run) > 0.0 ? normalize(run) : vec3(0.0, 1.0, 0.0);
}

float radiusSlopeAt(int ring) {
    int before = sameStream(ring, ring - 1) ? ring - 1 : ring;
    int after = sameStream(ring, ring + 1) ? ring + 1 : ring;
    return after == before ? 0.0 : (Rings[2 * after].w - Rings[2 * before].w) / float(after - before);
}

float hermite(float start, float end, float startSlope, float endSlope, float t) {
    float t2 = t * t;
    float t3 = t2 * t;
    return (2.0 * t3 - 3.0 * t2 + 1.0) * start + (t3 - 2.0 * t2 + t) * startSlope
        + (3.0 * t2 - 2.0 * t3) * end + (t3 - t2) * endSlope;
}

vec3 hermite3(vec3 start, vec3 end, vec3 startSlope, vec3 endSlope, float t) {
    float t2 = t * t;
    float t3 = t2 * t;
    return (2.0 * t3 - 3.0 * t2 + 1.0) * start + (t3 - 2.0 * t2 + t) * startSlope
        + (3.0 * t2 - 2.0 * t3) * end + (t3 - t2) * endSlope;
}

// The signed distance to the hull of a segment's two ring spheres, a cone
// tangent to both, which a point no segment owns falls back on.
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

// A point's angle about a stream's run at its foot, from the stream's side
// laid square to the run.
float angleAbout(int stream, vec3 axis, vec3 off) {
    vec3 run = dot(axis, axis) > 0.0 ? normalize(axis) : vec3(0.0, 1.0, 0.0);
    vec3 side = streamSlot(stream, SIDE_SLOT).xyz;
    vec3 across = side - run * dot(side, run);
    if (dot(across, across) == 0.0) {
        return 0.0;
    }
    vec3 first = normalize(across);
    return atan(dot(off, cross(run, first)), dot(off, first));
}

// How far out the cyclone's skin stands at an angle about the stream's axis
// (decision unmake-stream-is-a-shrinking-waterspout): its lobes at the radius
// turned by the phase, the grooves between them falling in, so the skin
// spins about the stream's own axis as the goo travels.
float cycloneAt(float angle, float phase) {
    return 1.0 - SPIN_DEPTH * (1.0 - cos(float(LOBES) * angle - phase)) / 2.0;
}

// The share of the radius the cyclone's skin stands at in a point's
// direction, its grooves fading toward the run's own line past an end of the
// stream, where no angle about the run is had.
float grooved(int stream, vec3 axis, vec3 off, float phase) {
    vec3 run = dot(axis, axis) > 0.0 ? normalize(axis) : vec3(0.0, 1.0, 0.0);
    float len2 = dot(off, off);
    vec3 square = off - run * dot(off, run);
    float share = len2 == 0.0 ? 0.0 : dot(square, square) / len2;
    return 1.0 - (1.0 - cycloneAt(angleAbout(stream, run, off), phase)) * share;
}

// A segment's reading at a point (decision diagnose-then-fix-unmake-seam-and-ribbing):
// the segment owns the points between the planes square to its stream's run
// at its two rings, so a ring's plane parts its two segments and both read
// the same there, value and facing alike, and the point's foot is its share
// of the way between the planes on the curve leaving each ring along that
// run, the radius easing between the rings the same way. read.x is the scaled
// signed distance, read.y the gap to the segment's field. Returns whether the
// segment owns the point; one that does not reads its round cone.
bool segmentRead(int segment, vec3 p, out vec2 read, out vec3 facing) {
    vec4 a = Rings[2 * segment];
    vec4 b = Rings[2 * segment + 2];
    vec3 line = b.xyz - a.xyz;
    float chord = length(line);
    vec3 leaving = tangentAt(segment);
    vec3 arriving = tangentAt(segment + 1);
    float widest = max(a.w, b.w);
    vec3 along = chord > 0.0 ? line / chord : leaving;
    float sag = chord / 3.0 * max(length(cross(leaving, along)), length(cross(arriving, along)));
    float before = dot(p - a.xyz, leaving);
    float after = dot(p - b.xyz, arriving);
    bool owned = (before >= 0.0 || !sameStream(segment, segment - 1))
        && (after <= 0.0 || !sameStream(segment + 1, segment + 2));
    float lineT = chord == 0.0 ? 0.0 : clamp(dot(p - a.xyz, line) / (chord * chord), 0.0, 1.0);
    float bound = length(p - a.xyz - line * lineT) - widest - sag;
    read.y = bound - REACH * min(1.0, widest / FULL_RADIUS);
    if (!owned) {
        float radius = mix(a.w, b.w, lineT);
        read.x = scaled(roundCone(p - a.xyz, line, a.w, b.w), radius);
        vec3 off = p - a.xyz - line * lineT;
        facing = dot(off, off) > 0.0 ? normalize(off) : vec3(0.0, 1.0, 0.0);
        return false;
    }
    float t = before <= 0.0 ? 0.0 : after >= 0.0 ? 1.0 : before / (before - after);
    vec3 foot = hermite3(a.xyz, b.xyz, leaving * chord, arriving * chord, t);
    float radius = clamp(hermite(a.w, b.w, radiusSlopeAt(segment), radiusSlopeAt(segment + 1), t),
        min(a.w, b.w), widest);
    vec3 off = p - foot;
    float offLength = length(off);
    float phase = mix(Rings[2 * segment + 1].w, Rings[2 * segment + 3].w, t);
    float skin = radius * grooved(int(Rings[2 * segment + 1].z), mix(leaving, arriving, t), off, phase);
    read.x = scaled(offLength - skin, radius);
    facing = offLength > 0.0 ? off / offLength : vec3(0.0, 1.0, 0.0);
    return true;
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

// The smooth union of two readings: the nearer, less a fillet where they come
// within its width of each other, the facing turning between theirs across it.
float blend(float a, vec3 facingA, float b, vec3 facingB, float width, out vec3 facing) {
    float apart = abs(a - b);
    if (width <= 0.0 || apart >= width) {
        facing = b < a ? facingB : facingA;
        return min(a, b);
    }
    float h = (width - apart) / width;
    float towardA = clamp((1.0 + (b - a) / width) / 2.0, 0.0, 1.0);
    vec3 mixed = facingA * towardA + facingB * (1.0 - towardA);
    facing = dot(mixed, mixed) > 0.0 ? normalize(mixed) : facingA;
    return min(a, b) - h * h * width / 4.0;
}

// A body's scaled signed distance at a point, for picking a stream's nearest
// body: a segment not owning the point reads a reach farther, so its owner wins.
float readBody(int entry, vec3 p) {
    int body = entry & (RUN_START - 1);
    vec3 facing;
    if (body >= BOX_BASE) {
        return boxRead(body - BOX_BASE, p, facing).x;
    }
    vec2 read;
    return segmentRead(body, p, read, facing) ? read.x : read.x + REACH;
}

// One stream's reading at a point: its segments read at the least distance of
// those owning the point, its block's box filleted onto them, where the point
// is within the box its bodies reach; nothing outside it, the gap then
// measured to the box so the march can jump to it.
float runField(vec3 p, int stream, inout float gap, out vec3 facing) {
    facing = vec3(0.0, 1.0, 0.0);
    vec4 low = streamSlot(stream, RUN_LOW_SLOT);
    vec4 high = streamSlot(stream, RUN_HIGH_SLOT);
    float outside = length(max(max(low.xyz - p, p - high.xyz), vec3(0.0)));
    if (outside > 0.0) {
        gap = min(gap, outside);
        return REACH;
    }
    float owned = FAR;
    vec3 ownedFacing = facing;
    float unowned = REACH;
    vec3 unownedFacing = facing;
    float boxed = FAR;
    vec3 boxFacing = facing;
    int first = int(low.w);
    int end = first + int(high.w);
    for (int e = first; e < end; e++) {
        int body = entryAt(e) & (RUN_START - 1);
        vec3 bodyFacing;
        if (body >= BOX_BASE) {
            vec2 read = boxRead(body - BOX_BASE, p, bodyFacing);
            gap = min(gap, read.y);
            boxed = read.x;
            boxFacing = bodyFacing;
            continue;
        }
        vec2 read;
        bool owns = segmentRead(body, p, read, bodyFacing);
        gap = min(gap, read.y);
        if (owns && read.x < owned) {
            owned = read.x;
            ownedFacing = bodyFacing;
        } else if (!owns && read.x < unowned) {
            unowned = read.x;
            unownedFacing = bodyFacing;
        }
    }
    float least = owned < FAR ? min(owned, REACH) : unowned;
    facing = owned < FAR ? ownedFacing : unownedFacing;
    if (boxed < FAR && least >= REACH) {
        if (boxed < least) {
            least = boxed;
            facing = boxFacing;
        }
    } else if (boxed < FAR) {
        vec3 blended;
        least = blend(least, facing, boxed, boxFacing, BOX_BLEND, blended);
        facing = blended;
    }
    return least;
}

// The field at a point over the region's streams (decision diagnose-then-fix-unmake-seam-and-ribbing):
// the two streams nearest it blended over a fillet as wide as their surfaces
// face apart, so streams lying on one another are one stream's width and
// streams meeting across a gap merge; gap is the least gap to any body's
// field, or to a run's box the point is outside.
float fieldAt(vec3 p, int streams, out float gap) {
    gap = FAR;
    float first = REACH;
    vec3 firstFacing = vec3(0.0, 1.0, 0.0);
    float second = REACH;
    vec3 secondFacing = firstFacing;
    for (int s = 0; s < streams; s++) {
        vec3 facing;
        float d = runField(p, s, gap, facing);
        if (d < first) {
            second = first;
            secondFacing = firstFacing;
            first = d;
            firstFacing = facing;
        } else if (d < second) {
            second = d;
            secondFacing = facing;
        }
    }
    vec3 facing;
    return falloff(blend(first, firstFacing, second, secondFacing,
        MEET_BLEND * (1.0 - dot(firstFacing, secondFacing)) / 2.0, facing));
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
