#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:globals.glsl>

// Drain's miasma vortex (decision drain-casts-a-filled-tornadic-miasma-vortex-along-the-look): the cone
// Drain strikes, from the glove hand along the look, filled with clumps of miasma being pulled into the
// hand. The miasma flows: down the cone toward the hand, faster as it nears it, spiralling in toward the
// axis, the inner part turning faster than the outer, so it swirls in like water down a drain rather than
// turning as one piece. Two phases of that flow, each carried a short way and then started over, blend into
// each other so the miasma keeps changing without smearing. Its body is clumps and thin spots with holes
// between them, rippled by a warp, dark at a clump's thick core and paler at its thin edge, with fine
// rifled streaks, the portal swirl band's (decision tornadic-halo-spirals-out-from-the-rim), wound through
// it and glowing as they near the hand. Its rim and far end fray rather than stop. It forms out from the
// hand when the hold starts and breaks up when it ends. Each fragment marches its ray front to back through
// the cone, stopping at whatever the world drew there, so it lies over floor, wall and sky alike and stands
// in front of a mob inside it. TRANSLUCENT blend, capped so a mob inside stays visible.
// The cone is the one it is handed (decision spray-abilities-render-procedurally-bound-to-their-cone): its
// section takes the shape's widths across and up and down, tested as StreamCone.contains tests a point, so a
// flat shape draws a flattened vortex.

// Must match MiasmaVortexPass.pack: seven std140 rows.
layout(std140) uniform MiasmaVortex {
    vec4 ApexSideHalf; // the hand or nozzle, camera relative, and half the shape's width across, in radians
    vec4 AxisLength;   // the unit look, and blocks the cone reaches along it
    vec4 SideDepth;    // the unit direction across the section, and whether depth runs 0 to 1
    vec4 BodyColor;    // the goo's wheel color, the clumps'
    vec4 StreakColor;  // the goo's bright color, the streaks' and the glow's
    vec4 Shown;        // how thick it shows, how far out from the hand it has formed, as shares, and 1 for a band
    vec4 LiftHalf;     // the unit direction up and down the section, and half the shape's width that way, in radians
};

uniform sampler2D MiasmaDepthSampler;

layout(location = 0) in vec3 rayPoint;

layout(location = 0) out vec4 fragColor;

// GameTime runs from zero to one over a day of 24000 ticks; this many seconds make one day.
const float SECONDS_PER_DAY = 1200.0;
const float TAU = 6.28318530718;
// Samples along each ray through the cone.
const int STEPS = 32;

// Seconds one phase of the flow carries the miasma before it starts over; divides the day whole.
const float FLOW_SECONDS = 2.0;
// Blocks a second the miasma pours toward the hand: a steady pull, and more the nearer it is.
const float PULL = 1.1;
const float PULL_NEAR = 1.4;
const float PULL_NEAR_SOFTEN = 0.4;
// How fast it closes in on the axis: the share of the radius it keeps each second, as a log.
const float CLOSE_IN = 0.35;
// Turns a second at the rim, how much faster at the axis, and how much faster again at the hand.
const float TURNS = 0.22;
const float TURNS_AT_AXIS = 1.6;
const float TURNS_AT_HAND = 0.8;

// The body: noise cells across the cone's width and per block along it, how far the warp ripples it, and
// where a clump starts, as a noise level, with how softly; the rest is the holes between clumps.
const float BODY_ACROSS = 2.6;
const float BODY_ALONG = 0.6;
const float RIPPLE_TURNS = 0.09;
const float RIPPLE_RADIUS = 0.22;
const float RIPPLE_CELLS = 1.3;
const float RIPPLE_SECONDS = 3.0;
const float CLUMP_FROM = 0.5;
const float CLUMP_SOFT = 0.08;
// With nothing showing, the clumps start at this noise level, which nothing reaches, so they break up first.
const float CLUMP_GONE = 1.05;
// How far over the clump level its thick core starts.
const float CORE_DEPTH = 0.15;

// The streaks: cells around the axis (whole, so they close on themselves), how far they wind per log of the
// radius (a spiral into the axis) and per block along (the rifling's screw), cells along per block, and how
// sharply a streak stands out, higher thinner.
const float STREAKS_AROUND = 22.0;
const float STREAK_CURL = 0.35;
const float STREAK_SCREW = 0.3;
const float STREAK_ALONG = 0.7;
const float STREAK_SHARPNESS = 4.0;

// How much miasma a block of clump holds, of streak, and of the haze in the holes.
const float CLUMP_DENSITY = 1.0;
const float STREAK_DENSITY = 1.4;
const float HAZE_DENSITY = 0.06;
// The vortex fills the whole area Drain strikes: its rim frays only in a thin band at the strike's own edge,
// as a share of the radius, ragged by the body noise this far.
const float RIM_FROM = 0.9;
const float FRAY = 0.12;
// Its far end tatters: from this share of the length out it is torn into strands and gaps by a fine, fast
// noise flowing toward the hand, ragged this far, so the end is messy and never holds still.
const float FAR_FROM = 0.62;
const float TATTER = 0.75;
const float TATTER_ACROSS = 5.0;
const float TATTER_ALONG = 2.4;
const float TATTER_FLOW = 1.5;
// The miasma thickens toward the rim, from this share of the radius out, by this much a block, so the edge
// of the hit area reads.
const float WALL_FROM = 0.75;
const float WALL_DENSITY = 0.35;
// Blocks from the hand the miasma thickens over, so the glove itself stays clear, and how far past its
// formed front it feathers.
const float HAND_CLEAR = 0.6;
const float FORM_FEATHER = 0.5;
// The most the miasma covers what lies behind it, so a mob inside still shows.
const float MOST_COVER = 0.85;
// The clump's color: its thick core this dark, its thin edge this pale; the glow toward the axis and the
// hand; and how far the far miasma pales into haze.
const float CORE_SHADE = 0.22;
const float EDGE_SHADE = 1.15;
const float AXIS_GLOW = 0.55;
const float HAND_GLOW = 0.7;
const float STREAK_HOT = 0.4;
const float FAR_PALE = 0.25;

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

// Smooth value noise, wrapping every period cells along x.
float noise(vec3 at, float period) {
    vec3 cell = floor(at);
    vec3 f = fract(at);
    f = f * f * (3.0 - 2.0 * f);
    float x0 = mod(cell.x, period);
    float x1 = mod(cell.x + 1.0, period);
    vec3 c = vec3(0.0, cell.yz);
    return mix(mix(mix(hash(c + vec3(x0, 0, 0)), hash(c + vec3(x1, 0, 0)), f.x),
                   mix(hash(c + vec3(x0, 1, 0)), hash(c + vec3(x1, 1, 0)), f.x), f.y),
               mix(mix(hash(c + vec3(x0, 0, 1)), hash(c + vec3(x1, 0, 1)), f.x),
                   mix(hash(c + vec3(x0, 1, 1)), hash(c + vec3(x1, 1, 1)), f.x), f.y), f.z);
}

// Noise that never wraps: cells kept clear of zero, under a period nothing reaches.
float plainNoise(vec3 at) {
    return noise(at + 64.0, 4096.0);
}

float fbm(vec3 at) {
    return 0.55 * plainNoise(at) + 0.3 * plainNoise(at * 2.03 + 17.0) + 0.15 * plainNoise(at * 4.07 + 41.0);
}

// The distance from the eye to whatever the world drew at this fragment, camera relative.
float sceneDistance(vec3 eye, mat4 viewToWorld) {
    float depth = texelFetch(MiasmaDepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    float ndcDepth = SideDepth.w > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 view = inverse(ProjMat) * vec4(gl_FragCoord.xy / ScreenSize * 2.0 - 1.0, ndcDepth, 1.0);
    vec3 world = (viewToWorld * vec4(view.xyz / view.w, 1.0)).xyz;
    return length(world - eye);
}

// Where the ray enters and leaves the ball around the apex holding the whole cone, as distances along it.
vec2 throughBall(vec3 eye, vec3 dir, float radius) {
    vec3 offset = eye - ApexSideHalf.xyz;
    float b = dot(offset, dir);
    float c = dot(offset, offset) - radius * radius;
    float h = b * b - c;
    if (h < 0.0) {
        return vec2(1.0, 0.0);
    }
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

// How far out a point stands in the cone's section, as a share of the rim there, 1 on it: for a band the
// wider of its angles across and up and down, each over its half width; for a cone its angle off the axis
// over the half width at its turn about the axis, the ellipse ConeShape.degreesAt draws between the two.
float rimShare(float along, float across, float upDown) {
    float sideHalf = ApexSideHalf.w;
    float liftHalf = LiftHalf.w;
    if (Shown.z > 0.5) {
        return max(abs(atan(across, along)) / sideHalf, abs(atan(upDown, along)) / liftHalf);
    }
    float offAxis = atan(length(vec2(across, upDown)), along);
    if (offAxis <= 1e-6) {
        return 0.0;
    }
    float turn = atan(upDown, across);
    float acrossPart = liftHalf * cos(turn);
    float upDownPart = sideHalf * sin(turn);
    float halfAtTurn = sideHalf * liftHalf / max(sqrt(acrossPart * acrossPart + upDownPart * upDownPart), 1e-6);
    return offAxis / halfAtTurn;
}

// A point of the cone in its own terms: blocks along the axis, the share of the rim out, and the angle.
struct ConePoint {
    float along;
    float share;
    float theta;
};

// The body's noise and the streaks at a cone point, for one phase of the flow: the point is carried back
// to where its miasma stood when the phase began, and the noise read there.
vec2 phaseAt(ConePoint at, float carried, float seed, float ripple) {
    float pull = PULL + PULL_NEAR / (at.along + PULL_NEAR_SOFTEN);
    float along = at.along + pull * carried;
    float share = at.share * exp(CLOSE_IN * carried);
    float inward = 1.0 - at.share;
    float turns = TURNS * (1.0 + TURNS_AT_AXIS * inward * inward) * (1.0 + TURNS_AT_HAND * (1.0 - at.along / AxisLength.w));
    float theta = at.theta - TAU * turns * carried + TAU * RIPPLE_TURNS * (ripple - 0.5);
    share *= 1.0 + RIPPLE_RADIUS * (ripple - 0.5);

    vec3 bodyAt = vec3(cos(theta) * share * BODY_ACROSS, sin(theta) * share * BODY_ACROSS, along * BODY_ALONG);
    float body = fbm(bodyAt + seed);

    float around = theta / TAU + STREAK_CURL * log(max(share, 0.02)) + STREAK_SCREW * along;
    vec3 streakAt = vec3(around * STREAKS_AROUND, along * STREAK_ALONG + seed, share * 2.5);
    float streak = pow(noise(streakAt, STREAKS_AROUND), STREAK_SHARPNESS);
    return vec2(body, streak);
}

// The miasma at a point: its density, how much of it is clump core, and how much is streak.
vec3 miasmaAt(vec3 point, float seconds) {
    vec3 offset = point - ApexSideHalf.xyz;
    float along = dot(offset, AxisLength.xyz);
    float reachLength = AxisLength.w;
    if (along <= 0.0 || along > reachLength) {
        return vec3(0.0);
    }
    vec3 radial = offset - along * AxisLength.xyz;
    float across = dot(radial, SideDepth.xyz);
    float upDown = dot(radial, LiftHalf.xyz);
    float share = rimShare(along, across, upDown);
    if (share > 1.0) {
        return vec3(0.0);
    }
    ConePoint at = ConePoint(along, share, atan(upDown, across));
    // the noise's section is squashed to the shape, so a flat vortex's clumps flatten with it
    vec2 section = vec2(across / max(along * tan(ApexSideHalf.w), 0.05), upDown / max(along * tan(LiftHalf.w), 0.05));
    float ripple = plainNoise(vec3(section * RIPPLE_CELLS, along * RIPPLE_CELLS - seconds / RIPPLE_SECONDS));

    // two phases half a flow apart, each fading in as it starts over and out as it ends, so no restart shows
    float cycle = seconds / FLOW_SECONDS;
    float firstShare = fract(cycle);
    float secondShare = fract(cycle + 0.5);
    float firstWeight = 1.0 - abs(2.0 * firstShare - 1.0);
    float secondWeight = 1.0 - firstWeight;
    vec2 first = phaseAt(at, firstShare * FLOW_SECONDS, floor(cycle) * 7.31, ripple);
    vec2 second = phaseAt(at, secondShare * FLOW_SECONDS, floor(cycle + 0.5) * 7.31 + 3.7, ripple);
    // blending two noises flattens them; dividing by the weights' length keeps the clumps as patchy midway
    float spread = length(vec2(firstWeight, secondWeight));
    float body = 0.5 + (firstWeight * (first.x - 0.5) + secondWeight * (second.x - 0.5)) / spread;
    float streak = firstWeight * first.y + secondWeight * second.y;

    float clumpFrom = mix(CLUMP_GONE, CLUMP_FROM, Shown.x);
    float clump = smoothstep(clumpFrom - CLUMP_SOFT, clumpFrom + CLUMP_SOFT, body);
    float core = smoothstep(clumpFrom, clumpFrom + CORE_DEPTH, body);

    float ragged = body - 0.5;
    float reach = along / reachLength;
    float tatter = plainNoise(vec3(section * TATTER_ACROSS, (along + seconds * TATTER_FLOW) * TATTER_ALONG));
    float torn = 0.6 * (tatter - 0.5) + 0.4 * ragged;
    float frayed = (1.0 - smoothstep(RIM_FROM, 1.0, share + FRAY * ragged))
            * (1.0 - smoothstep(FAR_FROM, 1.0, reach + TATTER * torn))
            * smoothstep(0.0, HAND_CLEAR, along)
            * (1.0 - smoothstep(0.0, FORM_FEATHER, along - Shown.y * (reachLength + FORM_FEATHER)));
    float streaked = STREAK_DENSITY * streak * (0.35 + 0.65 * clump) * Shown.x;
    float wall = WALL_DENSITY * smoothstep(WALL_FROM, RIM_FROM, share) * (0.4 + 0.6 * clump) * Shown.x;
    float density = frayed * (CLUMP_DENSITY * clump + HAZE_DENSITY * Shown.x + wall + streaked);
    return vec3(density, core, streaked / max(density / max(frayed, 1e-3), 1e-3));
}

void main() {
    if (Shown.x <= 0.0) {
        discard;
    }
    mat4 viewToWorld = inverse(ModelViewMat);
    vec3 eye = (viewToWorld * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    vec3 dir = normalize(rayPoint - eye);
    float sideTan = tan(ApexSideHalf.w);
    float liftTan = tan(LiftHalf.w);
    float ballRadius = AxisLength.w * sqrt(1.0 + sideTan * sideTan + liftTan * liftTan);
    vec2 span = throughBall(eye, dir, ballRadius);
    float near = max(span.x, 0.0);
    float far = min(span.y, sceneDistance(eye, viewToWorld));
    if (far <= near) {
        discard;
    }
    float seconds = GameTime * SECONDS_PER_DAY;
    float stride = (far - near) / float(STEPS);
    // a fixed jitter per pixel breaks the steps into grain rather than bands
    float jitter = hash(vec3(gl_FragCoord.xy, 7.0));
    vec3 body = BodyColor.rgb;
    vec3 glow = StreakColor.rgb;
    vec4 gathered = vec4(0.0);
    for (int index = 0; index < STEPS && gathered.a < MOST_COVER; index++) {
        float travelled = near + (float(index) + jitter) * stride;
        vec3 point = eye + dir * travelled;
        vec3 miasma = miasmaAt(point, seconds);
        if (miasma.x <= 0.0) {
            continue;
        }
        vec3 offset = point - ApexSideHalf.xyz;
        float along = dot(offset, AxisLength.xyz);
        vec3 radial = offset - along * AxisLength.xyz;
        float share = rimShare(max(along, 1e-3), dot(radial, SideDepth.xyz), dot(radial, LiftHalf.xyz));
        float reach = along / AxisLength.w;
        vec3 clumpColor = body * mix(EDGE_SHADE, CORE_SHADE, miasma.y);
        float lit = AXIS_GLOW * (1.0 - share) * (1.0 - share) + HAND_GLOW * (1.0 - reach) * (1.0 - reach);
        clumpColor = mix(clumpColor, glow, clamp(lit * (1.0 - miasma.y), 0.0, 1.0));
        vec3 streakColor = mix(glow, vec3(1.0), STREAK_HOT * (1.0 - reach));
        vec3 color = mix(clumpColor, streakColor, clamp(miasma.z, 0.0, 1.0));
        color = mix(color, body * EDGE_SHADE, FAR_PALE * reach);
        float taken = 1.0 - exp(-miasma.x * stride);
        gathered.rgb += (1.0 - gathered.a) * taken * color;
        gathered.a += (1.0 - gathered.a) * taken;
    }
    if (gathered.a <= 0.0) {
        discard;
    }
    fragColor = vec4(gathered.rgb / gathered.a, min(gathered.a, MOST_COVER));
}
