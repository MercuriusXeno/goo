#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

// Bore's drill (decision bore-shows-a-big-drill-from-the-glove): a drill-shaped vortex of earth magic,
// marched as Drain's vortex is (decision drain-casts-a-filled-tornadic-miasma-vortex-along-the-look).
// Wide at the glove and pointed at the tip, it is a churning swirl of earthy dust: clumps and thin spots
// carried forward toward the tip and round the axis, two flutes of thicker, paler dust wound round it
// as a drill's are, screwing forward as it spins, its rim ragged rather than round. Each sample is read
// at the middle of the world's sixteenth-of-a-block cell it falls in, as Flames' fire field is, so the
// dust stands in blocky texels. Each ray stops at whatever the world drew there when that surface stands
// outside the bit, so what stands in front of the bit hides it; a surface inside the bit is a block Bore
// is breaking, which hides nothing, so the bit draws over the blocks it breaks rather than under them.
// TRANSLUCENT blend, capped so what it drills still shows.

// Must match BoreDrillPass.pack: four std140 rows.
layout(std140) uniform BoreDrill {
    vec4 ApexRadius;  // the glove, camera relative, and the bit's radius there
    vec4 AxisLength;  // the unit axis from the glove to the tip, and the bit's length along it
    vec4 SideDepth;   // a unit direction square to the axis, and whether depth runs 0 to 1
    vec4 Grid;        // the camera's world position modulo 16, so the texels hold to the world
};

uniform sampler2D BoreDrillDepthSampler;

in vec3 rayPoint;

out vec4 fragColor;

// GameTime runs from zero to one over a day of 24000 ticks; this many seconds make one day.
const float SECONDS_PER_DAY = 1200.0;
const float TAU = 6.28318530718;
// Samples along each ray through the bit, and the world's cells to the block each sample snaps to.
const int STEPS = 48;
const float TEXELS_PER_BLOCK = 16.0;

// Seconds one phase of the flow carries the dust before it starts over; divides the day whole.
const float FLOW_SECONDS = 2.0;
// Blocks a second the dust pours toward the tip, and turns a second it swirls round the axis.
const float POUR = 1.6;
const float SWIRL_TURNS = 0.8;

// The flutes: how many wind round the bit, the turns each makes a block along it, how fast the bit spins
// (whole turns a day), and a flute's half width as a share of its period round the bit.
const float FLUTES = 2.0;
const float FLUTE_TURNS_PER_BLOCK = 0.9;
const float SPIN_TURNS_PER_SECOND = 1.5;
const float FLUTE_HALF = 0.22;

// The body: noise cells across the bit's width and per block along it, and where a clump starts, as a
// noise level, with how softly; the rest is the thin spots between clumps.
const float BODY_ACROSS = 2.2;
const float BODY_ALONG = 1.4;
const float CLUMP_FROM = 0.45;
const float CLUMP_SOFT = 0.1;
const float CORE_DEPTH = 0.18;

// How much dust a block of clump holds, of flute, and of the haze between.
const float CLUMP_DENSITY = 3.0;
const float FLUTE_DENSITY = 5.0;
const float HAZE_DENSITY = 0.6;
// The rim frays from this share of the radius, ragged by the body noise this far.
const float RIM_FROM = 0.7;
const float FRAY = 0.45;
// A sliver of width the bit keeps at its tip, in blocks, so the point frays rather than vanishing.
const float TIP_WIDTH = 0.04;
// How far past the bit, in blocks, a surface still counts as inside it: a texel, so the face of a block
// the bit just touches still reads as a block it is breaking.
const float SURFACE_SLACK = 0.0625;
// Blocks from the glove the dust thickens over, so the glove itself stays clear.
const float GLOVE_CLEAR = 0.25;
// The most the dust covers what lies behind it, so the blocks it drills still show.
const float MOST_COVER = 0.9;

// Rock's tones (rock_explosion.fsh, rock_bore.png): pale dust, tan dust, dark earth, stone grey.
const vec3 PALE_DUST = vec3(0.918, 0.816, 0.565);
const vec3 TAN_DUST = vec3(0.761, 0.659, 0.408);
const vec3 DARK_EARTH = vec3(0.345, 0.247, 0.165);
const vec3 STONE = vec3(0.561, 0.561, 0.561);
// How far the glove end glows toward the pale dust, as the earth magic pours from it.
const float GLOVE_GLOW = 0.5;

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

// The distance from the eye to whatever the world drew at this fragment, camera relative.
float sceneDistance(vec3 eye, mat4 viewToWorld) {
    float depth = texelFetch(BoreDrillDepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    float ndcDepth = SideDepth.w > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 view = inverse(ProjMat) * vec4(gl_FragCoord.xy / ScreenSize * 2.0 - 1.0, ndcDepth, 1.0);
    vec3 world = (viewToWorld * vec4(view.xyz / view.w, 1.0)).xyz;
    return length(world - eye);
}

// Whether a point stands inside the bit, its ragged rim and a texel's slack included: a surface there is a
// block Bore is breaking.
bool insideBit(vec3 point) {
    vec3 offset = point - ApexRadius.xyz;
    float along = dot(offset, AxisLength.xyz);
    if (along < -SURFACE_SLACK || along > AxisLength.w + SURFACE_SLACK) {
        return false;
    }
    float radius = ApexRadius.w * (1.0 - clamp(along / AxisLength.w, 0.0, 1.0)) + TIP_WIDTH;
    return length(offset - along * AxisLength.xyz) <= radius * (1.0 + FRAY) + SURFACE_SLACK;
}

// Where the ray enters and leaves the ball holding the whole bit, as distances along it.
vec2 throughBall(vec3 eye, vec3 dir) {
    vec3 middle = ApexRadius.xyz + AxisLength.xyz * (0.5 * AxisLength.w);
    float radius = length(vec2(0.5 * AxisLength.w, ApexRadius.w)) + 0.1;
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

// The body's noise at a point of the bit for one phase of the flow: the point carried back to where its
// dust stood when the phase began, and the noise read there.
float bodyAt(float along, float share, float theta, float carried, float seed) {
    float back = along - POUR * carried;
    float turned = theta - TAU * SWIRL_TURNS * carried;
    vec3 at = vec3(cos(turned) * share * BODY_ACROSS, sin(turned) * share * BODY_ACROSS, back * BODY_ALONG);
    return fbm(at + seed);
}

// The dust at a point: its density, how much of it is clump core, and how much is flute.
vec3 dustAt(vec3 point, float seconds) {
    vec3 offset = point - ApexRadius.xyz;
    float along = dot(offset, AxisLength.xyz);
    float bitLength = AxisLength.w;
    if (along <= 0.0 || along > bitLength) {
        return vec3(0.0);
    }
    vec3 radial = offset - along * AxisLength.xyz;
    float radius = ApexRadius.w * (1.0 - along / bitLength) + TIP_WIDTH;
    float share = length(radial) / radius;
    if (share > 1.0 + FRAY) {
        return vec3(0.0);
    }
    vec3 lift = cross(AxisLength.xyz, SideDepth.xyz);
    float theta = atan(dot(radial, lift), dot(radial, SideDepth.xyz));

    // two phases half a flow apart, each fading in as it starts over and out as it ends, so no restart shows
    float cycle = seconds / FLOW_SECONDS;
    float firstShare = fract(cycle);
    float secondShare = fract(cycle + 0.5);
    float firstWeight = 1.0 - abs(2.0 * firstShare - 1.0);
    float secondWeight = 1.0 - firstWeight;
    float first = bodyAt(along, share, theta, firstShare * FLOW_SECONDS, floor(cycle) * 7.31);
    float second = bodyAt(along, share, theta, secondShare * FLOW_SECONDS, floor(cycle + 0.5) * 7.31 + 3.7);
    float spread = length(vec2(firstWeight, secondWeight));
    float body = 0.5 + (firstWeight * (first - 0.5) + secondWeight * (second - 0.5)) / spread;

    float clump = smoothstep(CLUMP_FROM - CLUMP_SOFT, CLUMP_FROM + CLUMP_SOFT, body);
    float core = smoothstep(CLUMP_FROM, CLUMP_FROM + CORE_DEPTH, body);
    float flutePhase = fract((theta / TAU - along * FLUTE_TURNS_PER_BLOCK + seconds * SPIN_TURNS_PER_SECOND) * FLUTES);
    float flute = smoothstep(FLUTE_HALF, FLUTE_HALF * 0.3, abs(flutePhase - 0.5)) * (0.5 + 0.5 * clump);

    float frayed = (1.0 - smoothstep(RIM_FROM, 1.0, share + FRAY * (body - 0.5)))
            * smoothstep(0.0, GLOVE_CLEAR, along);
    float density = frayed * (CLUMP_DENSITY * clump + FLUTE_DENSITY * flute + HAZE_DENSITY);
    return vec3(density, core, clamp(FLUTE_DENSITY * flute / max(density / max(frayed, 1e-3), 1e-3), 0.0, 1.0));
}

void main() {
    mat4 viewToWorld = inverse(ModelViewMat);
    vec3 eye = (viewToWorld * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    vec3 dir = normalize(rayPoint - eye);
    vec2 span = throughBall(eye, dir);
    float near = max(span.x, 0.0);
    float scene = sceneDistance(eye, viewToWorld);
    float far = insideBit(eye + dir * scene) ? span.y : min(span.y, scene);
    if (far <= near) {
        discard;
    }
    float seconds = GameTime * SECONDS_PER_DAY;
    float stride = (far - near) / float(STEPS);
    vec4 gathered = vec4(0.0);
    for (int index = 0; index < STEPS && gathered.a < MOST_COVER; index++) {
        vec3 point = eye + dir * (near + (float(index) + 0.5) * stride);
        vec3 snapped = (floor((point + Grid.xyz) * TEXELS_PER_BLOCK) + 0.5) / TEXELS_PER_BLOCK - Grid.xyz;
        vec3 dust = dustAt(snapped, seconds);
        if (dust.x <= 0.0) {
            continue;
        }
        float reach = clamp(dot(snapped - ApexRadius.xyz, AxisLength.xyz) / AxisLength.w, 0.0, 1.0);
        vec3 earth = mix(mix(TAN_DUST, STONE, 0.35), DARK_EARTH, dust.y);
        vec3 color = mix(earth, PALE_DUST, dust.z);
        color = mix(color, PALE_DUST, GLOVE_GLOW * (1.0 - reach) * (1.0 - reach) * (1.0 - dust.y));
        float taken = 1.0 - exp(-dust.x * stride);
        gathered.rgb += (1.0 - gathered.a) * taken * color;
        gathered.a += (1.0 - gathered.a) * taken;
    }
    if (gathered.a <= 0.0) {
        discard;
    }
    fragColor = vec4(gathered.rgb / gathered.a, min(gathered.a, MOST_COVER));
}
