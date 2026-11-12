#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

// Bore's vortex (decisions bore-shows-a-big-drill-from-the-glove, bore-breaks-what-is-in-the-vortex): a
// vortex of earth magic held out in front of the player, marched as Drain's vortex is (decision
// drain-casts-a-filled-tornadic-miasma-vortex-along-the-look). From a little below the line of sight it
// runs out to Bore's reach on the line of sight, from a wide mouth where it is held narrowing toward a
// point, never wider than a block, its tip tattering a little. It is a sparse, chaotically patchy swirl of
// earthy dust, as Drain's is: clumps with holes between them, rippled by a warp, poured outward and swirled
// round the axis, dark at a clump's thick core and paler at its thin edge. Its cone reads by its shell:
// the dust thickens into a wall just inside the vortex's edge, crisp at the edge, cut by rifling, pale
// bands wound round the cone and turning as it bores, which show through the translucent dust from inside
// and out. It thickens over its first stretch, so it fades in where it is held rather than starting hard.
// Each ray marches smoothly through it, as Drain's does.
// Each ray stops at whatever the world drew there when that surface stands outside the vortex, so what
// stands in front of it hides it; a surface inside the vortex is a block Bore is breaking, which hides
// nothing. TRANSLUCENT blend, capped so what it drills still shows.

// Must match BoreDrillPass.pack: four std140 rows.
layout(std140) uniform BoreDrill {
    vec4 ApexRadius;  // where the vortex is held, camera relative, and its radius there
    vec4 AxisLength;  // the unit axis from where it is held to the far end, and its length
    vec4 SideDepth;   // a unit direction square to the axis, and whether depth runs 0 to 1
    vec4 Grid;        // unread in xyz; the vortex's radius at its far end in w
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
const float CLUMP_SOFT = 0.05;
const float CORE_DEPTH = 0.15;

// How much dust a block of clump holds, and of the haze in the holes.
const float CLUMP_DENSITY = 1.8;
const float HAZE_DENSITY = 0.08;
// The shell: the share of the radius it thickens from and is full by, and how much dust a block of it
// holds between the rifling's bands and on them.
const float SHELL_FROM = 0.7;
const float SHELL_FULL = 0.86;
const float SHELL_DENSITY = 5.0;
const float RIFLED_DENSITY = 14.0;
// The rifling: bands round the cone, the turns each makes a block along it, how fast they turn (whole
// turns a day), and a band's half width as a share of its period.
const float RIFLES = 3.0;
const float RIFLE_TURNS_PER_BLOCK = 1.5;
const float RIFLE_TURNS_PER_SECOND = 1.0;
const float RIFLE_HALF = 0.22;
// The rim frays inward from this share of the radius, ragged by the body noise this far, little, so the
// cone's edge stays crisp; no dust stands past the vortex's edge, so the vortex is never drawn wider than
// it breaks.
const float RIM_FROM = 0.92;
const float FRAY = 0.12;
// The tip tatters from this share of the length out, torn into strands and gaps by a fine,
// fast noise flowing outward, ragged this far, so the end is messy and never holds still.
const float FAR_FROM = 0.6;
const float TATTER = 0.45;
const float TATTER_ACROSS = 5.0;
const float TATTER_ALONG = 2.4;
const float TATTER_FLOW = 1.5;
// Blocks from where it is held the vortex thickens across, so it fades in rather than starting hard.
const float HELD_THICKEN = 0.5;
// How far past the vortex, in blocks, a surface still counts as inside it: a texel, so the face of a
// block the vortex just touches still reads as a block it is breaking.
const float SURFACE_SLACK = 0.0625;
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
    return mix(ApexRadius.w, Grid.w, clamp(along / AxisLength.w, 0.0, 1.0));
}

// The distance from the eye to whatever the world drew at this fragment, camera relative.
float sceneDistance(vec3 eye, mat4 viewToWorld) {
    float depth = texelFetch(BoreDrillDepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    float ndcDepth = SideDepth.w > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 view = inverse(ProjMat) * vec4(gl_FragCoord.xy / ScreenSize * 2.0 - 1.0, ndcDepth, 1.0);
    vec3 world = (viewToWorld * vec4(view.xyz / view.w, 1.0)).xyz;
    return length(world - eye);
}

// Whether a point stands inside the vortex, a texel's slack included: a surface there is a block Bore is
// breaking.
bool insideVortex(vec3 point) {
    vec3 offset = point - ApexRadius.xyz;
    float along = dot(offset, AxisLength.xyz);
    if (along < -SURFACE_SLACK || along > AxisLength.w + SURFACE_SLACK) {
        return false;
    }
    return length(offset - along * AxisLength.xyz) <= radiusAt(along) + SURFACE_SLACK;
}

// Where the ray enters and leaves the ball holding the whole vortex, as distances along it.
vec2 throughBall(vec3 eye, vec3 dir) {
    vec3 middle = ApexRadius.xyz + AxisLength.xyz * (0.5 * AxisLength.w);
    float radius = length(vec2(0.5 * AxisLength.w, max(ApexRadius.w, Grid.w))) + 0.1;
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

    float clump = smoothstep(CLUMP_FROM - CLUMP_SOFT, CLUMP_FROM + CLUMP_SOFT, body);
    float core = smoothstep(CLUMP_FROM, CLUMP_FROM + CORE_DEPTH, body);

    float rifled = smoothstep(RIFLE_HALF, RIFLE_HALF * 0.5, abs(fract((theta / TAU + along * RIFLE_TURNS_PER_BLOCK
            - seconds * RIFLE_TURNS_PER_SECOND) * RIFLES) - 0.5));
    float shell = smoothstep(SHELL_FROM, SHELL_FULL, share);
    float wall = shell * (SHELL_DENSITY + RIFLED_DENSITY * rifled);

    float ragged = body - 0.5;
    float tatter = noise(vec3(section * TATTER_ACROSS, (along - seconds * TATTER_FLOW) * TATTER_ALONG) + 17.0);
    float torn = 0.6 * (tatter - 0.5) + 0.4 * ragged;
    float frayed = (1.0 - smoothstep(RIM_FROM, 1.0, share + FRAY * ragged))
            * (1.0 - smoothstep(FAR_FROM, 1.0, along / vortexLength + TATTER * torn))
            * smoothstep(0.0, HELD_THICKEN, along);
    float density = frayed * (CLUMP_DENSITY * clump + HAZE_DENSITY + wall);
    float pale = RIFLED_DENSITY * rifled * shell * frayed;
    return vec3(density, core * (1.0 - shell), clamp(pale / max(density, 1e-3), 0.0, 1.0));
}

void main() {
    mat4 viewToWorld = inverse(ModelViewMat);
    vec3 eye = (viewToWorld * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    vec3 dir = normalize(rayPoint - eye);
    vec2 span = throughBall(eye, dir);
    float near = max(span.x, 0.0);
    float scene = sceneDistance(eye, viewToWorld);
    float far = insideVortex(eye + dir * scene) ? span.y : min(span.y, scene);
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
