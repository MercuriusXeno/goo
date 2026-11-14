#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

// Bore's vortex (decisions bore-shows-a-big-drill-from-the-glove, bore-breaks-what-is-in-the-vortex): a
// vortex of earth magic held out in front of the player, marched as Drain's vortex is (decision
// drain-casts-a-filled-tornadic-miasma-vortex-along-the-look). From Bore's blob, eased forward from the
// glove to hold it at an angle off the line of sight, it runs out to its point on the line of sight, the
// block the crosshair aims at, from a wide mouth at the blob narrowing toward the point, never wider than a
// block, its tip tattering a little. It is a sparse, chaotically patchy swirl of
// earthy dust, as Drain's is: clumps with holes between them, rippled by a warp, poured outward and swirled
// round the axis, dark at a clump's thick core and paler at its thin edge. Its cone reads by its shell:
// the dust thickens into a wall inside the vortex's edge, and screw threads wound round the cone stand out
// from it, pale and turning as it bores, showing through the translucent dust from inside and out. Nothing
// about it is geometrically perfect: the cone wobbles, the threads wander and vary in width, and wispy
// frills trail off the shell's outside, all within the vortex's edge. It thickens over its first stretch, so it fades in where it is held rather than starting hard.
// Each ray marches smoothly through it, as Drain's does.
// Each ray stops at whatever the world drew there, so the blocks the vortex meets clip it, and BoreDrill
// spits the block's particles where it makes contact. TRANSLUCENT blend, capped so what it drills still
// shows.

// Must match BoreDrillPass.pack: four std140 rows.
layout(std140) uniform BoreDrill {
    vec4 ApexRadius;  // where the vortex is held, camera relative, and its radius there
    vec4 AxisLength;  // the unit axis from where it is held to the far end, and its length
    vec4 SideDepth;   // a unit direction square to the axis, and whether depth runs 0 to 1
    vec4 Shown;       // how much of the vortex shows in x, 0 to 1, as it builds up and disperses; its radius
                      // at its far end in w
};

uniform sampler2D BoreDrillDepthSampler;

in vec3 rayPoint;

out vec4 fragColor;

// GameTime runs from zero to one over a day of 24000 ticks; this many seconds make one day.
const float SECONDS_PER_DAY = 1200.0;
const float TAU = 6.28318530718;
// Samples along each ray through the vortex.
const int STEPS = 64;

// Seconds one phase of the flow carries the dust before it starts over; divides the day whole.
const float FLOW_SECONDS = 2.0;
// Blocks a second the dust pours outward, and turns a second it swirls round the axis.
const float POUR = 1.6;
const float SWIRL_TURNS = 0.8;

// The body: noise cells across the vortex's width and per block along it, how far the warp ripples it,
// and where a clump starts, as a noise level, with how softly; the rest is the holes between clumps.
const float BODY_ACROSS = 2.6;
const float BODY_ALONG = 0.9;
const float RIPPLE_TURNS = 0.09;
const float RIPPLE_RADIUS = 0.22;
const float RIPPLE_CELLS = 1.3;
const float RIPPLE_SECONDS = 3.0;
const float CLUMP_FROM = 0.52;
// With nothing showing, a clump starts at this noise level, which nothing reaches, so as the vortex
// builds up its clumps form in, and as it disperses they break up, as Drain's vortex does.
const float CLUMP_GONE = 1.05;
const float CLUMP_SOFT = 0.05;
const float CORE_DEPTH = 0.15;

// How much dust a block of clump holds, and of the haze in the holes.
const float CLUMP_DENSITY = 1.8;
const float HAZE_DENSITY = 0.08;
// The shell: the share of the radius it thickens from, is full by and ends at, and how much dust a
// block of it holds.
const float SHELL_FROM = 0.3;
const float SHELL_FULL = 0.4;
const float SHELL_EDGE = 0.5;
const float SHELL_DENSITY = 5.0;
// How far the cone wobbles, as a share of its radius, so it is never a perfect cone.
const float CONE_WOBBLE = 0.2;
// The screw threads: how many wind round the cone, the turns each makes a block along it, how fast they
// turn (whole turns a day), a thread's half width as a share of its period, how far it stands out past
// the shell as a share of the radius, how far its path wanders, and how much dust a block of it holds.
const float THREADS = 2.0;
const float THREAD_TURNS_PER_BLOCK = 1.0;
const float THREAD_TURNS_PER_SECOND = 1.0;
const float THREAD_HALF = 0.13;
const float THREAD_HEIGHT = 0.5;
const float THREAD_WANDER = 0.08;
const float THREAD_DENSITY = 12.0;
// The frills: wispy strands trailing off the shell's outside, how fine round the cone and along it, how
// fast they stream backward, the noise level a strand starts at, and how much dust a block of one holds.
const float FRILL_AROUND = 9.0;
const float FRILL_ALONG = 4.0;
const float FRILL_STREAM = 2.5;
const float FRILL_FROM = 0.6;
const float FRILL_DENSITY = 5.0;
// The rim frays inward from this share of the radius, ragged by the body noise this far; no dust stands
// past the vortex's edge, so the vortex is never drawn wider than it breaks.
const float RIM_FROM = 0.8;
const float FRAY = 0.35;
// The tip tatters from this share of the length out, torn into strands and gaps by a fine,
// fast noise flowing outward, ragged this far, so the end is messy and never holds still.
const float FAR_FROM = 0.6;
const float TATTER = 0.45;
const float TATTER_ACROSS = 5.0;
const float TATTER_ALONG = 2.4;
const float TATTER_FLOW = 1.5;
// Blocks from where it is held the vortex thickens across, so it fades in rather than starting hard.
const float HELD_THICKEN = 0.5;
// The most the dust covers what lies behind it, so the blocks it drills still show.
const float MOST_COVER = 0.85;

// Rock's tones (rock_explosion.fsh, rock_bore.png): pale dust, tan dust, dark earth.
const vec3 PALE_DUST = vec3(0.918, 0.816, 0.565);
const vec3 TAN_DUST = vec3(0.761, 0.659, 0.408);
const vec3 DARK_EARTH = vec3(0.345, 0.247, 0.165);

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 at) {
    vec3 cell = floor(at);
    vec3 f = fract(at);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(cell), hash(cell + vec3(1, 0, 0)), f.x),
                   mix(hash(cell + vec3(0, 1, 0)), hash(cell + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(cell + vec3(0, 0, 1)), hash(cell + vec3(1, 0, 1)), f.x),
                   mix(hash(cell + vec3(0, 1, 1)), hash(cell + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 at) {
    return 0.55 * noise(at + 64.0) + 0.3 * noise(at * 2.03 + 81.0) + 0.15 * noise(at * 4.07 + 105.0);
}

// The vortex's radius a distance along it: widening from its near radius to its far radius.
float radiusAt(float along) {
    return mix(ApexRadius.w, Shown.w, clamp(along / AxisLength.w, 0.0, 1.0));
}

// The distance from the eye to whatever the world drew at this fragment, camera relative.
float sceneDistance(vec3 eye, mat4 viewToWorld) {
    float depth = texelFetch(BoreDrillDepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    float ndcDepth = SideDepth.w > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 view = inverse(ProjMat) * vec4(gl_FragCoord.xy / ScreenSize * 2.0 - 1.0, ndcDepth, 1.0);
    vec3 world = (viewToWorld * vec4(view.xyz / view.w, 1.0)).xyz;
    return length(world - eye);
}

// Where the ray enters and leaves the ball holding the whole vortex, as distances along it.
vec2 throughBall(vec3 eye, vec3 dir) {
    vec3 middle = ApexRadius.xyz + AxisLength.xyz * (0.5 * AxisLength.w);
    float radius = length(vec2(0.5 * AxisLength.w, max(ApexRadius.w, Shown.w))) + 0.1;
    vec3 offset = eye - middle;
    float b = dot(offset, dir);
    float c = dot(offset, offset) - radius * radius;
    float h = b * b - c;
    if (h < 0.0) {
        return vec2(1.0, 0.0);
    }
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

// The body's noise at a point of the vortex for one phase of the flow: the point carried back to where
// its dust stood when the phase began, rippled by the warp, and the noise read there.
float bodyAt(float along, float share, float theta, float carried, float seed, float ripple) {
    float back = along - POUR * carried;
    float turned = theta - TAU * SWIRL_TURNS * carried + TAU * RIPPLE_TURNS * (ripple - 0.5);
    float spread = share * (1.0 + RIPPLE_RADIUS * (ripple - 0.5));
    vec3 at = vec3(cos(turned) * spread * BODY_ACROSS, sin(turned) * spread * BODY_ACROSS, back * BODY_ALONG);
    return fbm(at + seed);
}

// The dust at a point: its density, how much of it is clump core, and how much is rifling.
vec3 dustAt(vec3 point, float seconds) {
    vec3 offset = point - ApexRadius.xyz;
    float along = dot(offset, AxisLength.xyz);
    float vortexLength = AxisLength.w;
    if (along <= 0.0 || along > vortexLength) {
        return vec3(0.0);
    }
    vec3 radial = offset - along * AxisLength.xyz;
    float share = length(radial) / radiusAt(along);
    if (share >= 1.0) {
        return vec3(0.0);
    }
    vec3 lift = cross(AxisLength.xyz, SideDepth.xyz);
    float theta = atan(dot(radial, lift), dot(radial, SideDepth.xyz));
    vec2 section = vec2(dot(radial, SideDepth.xyz), dot(radial, lift)) / radiusAt(along);
    float ripple = noise(vec3(section * RIPPLE_CELLS, along * RIPPLE_CELLS - seconds / RIPPLE_SECONDS) + 64.0);

    // two phases half a flow apart, each fading in as it starts over and out as it ends, so no restart shows
    float cycle = seconds / FLOW_SECONDS;
    float firstShare = fract(cycle);
    float secondShare = fract(cycle + 0.5);
    float firstWeight = 1.0 - abs(2.0 * firstShare - 1.0);
    float secondWeight = 1.0 - firstWeight;
    float first = bodyAt(along, share, theta, firstShare * FLOW_SECONDS, floor(cycle) * 7.31, ripple);
    float second = bodyAt(along, share, theta, secondShare * FLOW_SECONDS, floor(cycle + 0.5) * 7.31 + 3.7,
            ripple);
    float spread = length(vec2(firstWeight, secondWeight));
    float body = 0.5 + (firstWeight * (first - 0.5) + secondWeight * (second - 0.5)) / spread;

    float clumpFrom = mix(CLUMP_GONE, CLUMP_FROM, Shown.x);
    float clump = smoothstep(clumpFrom - CLUMP_SOFT, clumpFrom + CLUMP_SOFT, body);
    float core = smoothstep(clumpFrom, clumpFrom + CORE_DEPTH, body);

    // the unit direction round the axis, free of the seam an angle carries where it wraps
    vec2 facing = section / max(length(section), 1e-4);
    // the cone wobbles, so the shell and threads stand at an uneven distance from the axis
    float wobble = noise(vec3(section * 1.5, along * 1.2 - seconds * 0.5) + 31.0) - 0.5;
    float outward = share * (1.0 - CONE_WOBBLE * wobble);
    float wander = THREAD_WANDER * (noise(vec3(section * 2.0, along * 2.0) + 7.0) - 0.5);
    float threadPhase = fract((theta / TAU + along * THREAD_TURNS_PER_BLOCK - seconds * THREAD_TURNS_PER_SECOND
            + wander) * THREADS);
    float threadWidth = THREAD_HALF * (0.7 + 0.6 * noise(vec3(facing * 2.0, along * 3.0) + 3.0));
    float thread = smoothstep(threadWidth, threadWidth * 0.4, abs(threadPhase - 0.5));
    float edge = SHELL_EDGE + THREAD_HEIGHT * thread * (0.7 + 0.6 * noise(vec3(facing * 5.0, along * 6.0 - seconds) + 41.0));
    float shell = smoothstep(SHELL_FROM, SHELL_FULL, outward) * (1.0 - smoothstep(edge - 0.05, edge, outward));
    float strand = noise(vec3(facing * FRILL_AROUND, (along + seconds * FRILL_STREAM) * FRILL_ALONG + outward * 6.0)
            + 53.0);
    float frill = smoothstep(FRILL_FROM, FRILL_FROM + 0.15, strand) * smoothstep(SHELL_EDGE - 0.1, SHELL_EDGE, outward);
    float wall = shell * (SHELL_DENSITY + THREAD_DENSITY * thread) + FRILL_DENSITY * frill;

    float ragged = body - 0.5;
    float tatter = noise(vec3(section * TATTER_ACROSS, (along - seconds * TATTER_FLOW) * TATTER_ALONG) + 17.0);
    float torn = 0.6 * (tatter - 0.5) + 0.4 * ragged;
    float frayed = (1.0 - smoothstep(RIM_FROM, 1.0, share + FRAY * ragged))
            * (1.0 - smoothstep(FAR_FROM, 1.0, along / vortexLength + TATTER * torn))
            * smoothstep(0.0, HELD_THICKEN, along);
    float density = frayed * Shown.x * (CLUMP_DENSITY * clump + HAZE_DENSITY + wall);
    float pale = (THREAD_DENSITY * thread * shell + 0.5 * FRILL_DENSITY * frill) * frayed;
    return vec3(density, core * (1.0 - shell), clamp(pale / max(density, 1e-3), 0.0, 1.0));
}

void main() {
    mat4 viewToWorld = inverse(ModelViewMat);
    vec3 eye = (viewToWorld * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    vec3 dir = normalize(rayPoint - eye);
    vec2 span = throughBall(eye, dir);
    float near = max(span.x, 0.0);
    float scene = sceneDistance(eye, viewToWorld);
    float far = min(span.y, scene);
    if (far <= near) {
        discard;
    }
    float seconds = GameTime * SECONDS_PER_DAY;
    // March the ray front to back through the vortex in even steps, each step nudged by a jitter fixed
    // for the pixel, as Drain's vortex does, so the steps break into fine grain rather than bands.
    float stride = (far - near) / float(STEPS);
    float jitter = hash(vec3(gl_FragCoord.xy, 7.0));
    vec4 gathered = vec4(0.0);
    for (int index = 0; index < STEPS && gathered.a < MOST_COVER; index++) {
        vec3 dust = dustAt(eye + dir * (near + (float(index) + jitter) * stride), seconds);
        if (dust.x <= 0.0) {
            continue;
        }
        vec3 earth = mix(mix(PALE_DUST, TAN_DUST, 0.5), DARK_EARTH, dust.y);
        vec3 color = mix(earth, PALE_DUST, dust.z);
        float taken = 1.0 - exp(-dust.x * stride);
        gathered.rgb += (1.0 - gathered.a) * taken * color;
        gathered.a += (1.0 - gathered.a) * taken;
    }
    if (gathered.a <= 0.0) {
        discard;
    }
    fragColor = vec4(gathered.rgb / gathered.a, min(gathered.a, MOST_COVER));
}
