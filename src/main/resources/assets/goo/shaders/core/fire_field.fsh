#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

// Flames' fire (decision flames-draws-as-particle-style-fire): separate licks
// of flame and small embers poured down the cone from the spout, marched
// voxel by voxel through the world's grid of 16 texels to the block. Each lick
// is a tongue on its own lane that travels out from the spout, swells, climbs,
// then shrinks and fades; embers are single bright texels that drift out
// slower and climb faster. Licks crowd near the spout and thin toward the
// reach, so the fire never reads as one solid body. A block the look meets
// cuts the fire off at its face: nothing burns behind it, and nothing turns
// off it. Each voxel is colored in hard steps from a white-yellow core through
// orange to red. TRANSLUCENT blend; the nearest voxel drawn writes the depth.

// Must match FireField.pack: eight std140 rows.
layout(std140) uniform FireField {
    vec4 ApexTan;     // the spout, and the tangent of half the cone's angle
    vec4 AxisReach;   // the way the fire pours, and blocks to the reach or the face the look meets
    vec4 SideTail;    // the unit direction across the cone, and blocks from the spout the burning starts
    vec4 NormalFront; // the met face's normal, and blocks from the spout the burning has reached
    vec4 HitWall;     // the met point, and blocks from the spout to it, negative with no wall
    vec4 BoxLowDepth; // the box's low corner, and whether depth runs 0 to 1
    vec4 BoxHigh;
    vec4 Grid;        // the camera's world position modulo 16, so the voxels hold to the world
};

in vec3 rayPoint;

out vec4 fragColor;

const float TICKS_PER_DAY = 24000.0;
const float TEXELS_PER_BLOCK = 16.0;
const int MAX_VOXELS = 480;
const float TWO_PI = 6.2831853;

// Licks: blocks a tick they travel out, blocks between one wave of licks and
// the next, and the lanes each wave runs on.
const float LICK_POUR_PER_TICK = 0.55;
const float LICK_SPACING = 0.42;
const int LICK_LANES = 4;
// The share of the lanes a wave fills at the spout and at the reach.
const float NEAR_PRESENCE = 0.95;
const float FAR_PRESENCE = 0.35;
// How far out across the cone a lick's lane may sit, as a share of the cone's radius there.
const float LANE_SPREAD = 0.85;
// A lick's radius at the spout and once swollen, the share of its life it swells over, how much it shrinks by
// its end, and how many radii long it stretches down its lane.
const float LICK_OPENING_RADIUS = 0.04;
const float LICK_RADIUS = 0.15;
const float LICK_SWELL_SHARE = 0.2;
const float LICK_SHRINK = 0.7;
const float LICK_STRETCH = 2.2;
// Tongues burn taller above their middle than below.
const float STRETCH_ABOVE = 0.6;
const float SQUASH_BELOW = 1.5;
// Blocks a lick climbs per block it travels, and how far it sways across its lane.
const float LICK_CLIMB_PER_BLOCK = 0.12;
const float LICK_SWAY = 0.05;
const float SWAY_PER_TICK = 0.8;

// Embers: blocks a tick they drift out, blocks between waves, lanes per wave, the share of lanes lit, how far
// out across the cone they wander, how much faster they climb than the licks, and their radius, enough that
// each lights at least one voxel.
const float EMBER_POUR_PER_TICK = 0.3;
const float EMBER_SPACING = 0.5;
const int EMBER_LANES = 2;
const float EMBER_PRESENCE = 0.55;
const float EMBER_SPREAD = 1.15;
const float EMBER_CLIMB_PER_BLOCK = 0.32;
const float EMBER_RADIUS = 0.06;

// The noise that breaks each lick's edge into pixels as it climbs.
const float BREAKUP_CELLS = 7.0;
const float BREAKUP_RISE_PER_TICK = 0.45;
const float BREAKUP_STRENGTH = 0.4;

// The hard color steps, hottest first, the heat each one starts at, and how opaque each voxel draws.
const vec3 CORE = vec3(1.00, 0.95, 0.70);
const vec3 YELLOW = vec3(1.00, 0.78, 0.22);
const vec3 ORANGE = vec3(0.97, 0.48, 0.09);
const vec3 RED = vec3(0.78, 0.20, 0.05);
const vec3 EMBER = vec3(1.00, 0.70, 0.25);
const float CORE_HEAT = 0.85;
const float YELLOW_HEAT = 0.6;
const float ORANGE_HEAT = 0.38;
const float RED_HEAT = 0.18;
const float FLAME_OPACITY = 0.5;
const float EMBER_OPACITY = 0.9;
// The operator's ruling: the fire near the spout is see-through so the far end reads.
const float NEAR_OPACITY_SHARE = 0.3;
const float NEAR_CLEAR_UNTIL = 0.5;
const float FAR_SOLID_FROM = 2.5;

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x),
                   mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
                   mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

// Whether a lick or ember standing the given blocks out burns now: inside the burning stretch and the reach.
bool burningAt(float travelled) {
    return travelled >= SideTail.w && travelled <= NormalFront.w && travelled <= AxisReach.w;
}

// A point on a lane across the cone: the given share of the cone's radius out, turned the given share of a circle.
vec2 laneAt(float spread, float turn, float travelled) {
    float angle = turn * TWO_PI;
    return vec2(cos(angle), sin(angle)) * spread * travelled * ApexTan.w;
}

// The hottest lick at a point, from the waves nearest it, by how deep inside a lick the point stands.
float lickHeat(vec2 across, float along, float ticks) {
    float poured = ticks * LICK_POUR_PER_TICK;
    float wave = floor((along - poured) / LICK_SPACING);
    float reach = max(AxisReach.w, 1e-3);
    float hottest = 0.0;
    for (int near = -1; near <= 1; near++) {
        float index = wave + float(near);
        for (int lane = 0; lane < LICK_LANES; lane++) {
            vec3 seed = vec3(index, float(lane), 17.0);
            float travelled = (index + hash(seed)) * LICK_SPACING + poured;
            float life = travelled / reach;
            if (!burningAt(travelled) || hash(seed + 3.1) > mix(NEAR_PRESENCE, FAR_PRESENCE, life)) {
                continue;
            }
            vec2 center = laneAt(LANE_SPREAD * sqrt(hash(seed + 7.3)), hash(seed + 11.9), travelled);
            center.x += sin(ticks * SWAY_PER_TICK + hash(seed + 5.7) * TWO_PI) * LICK_SWAY * travelled;
            center.y += travelled * LICK_CLIMB_PER_BLOCK;
            float swell = smoothstep(0.0, LICK_SWELL_SHARE, life);
            float radius = mix(LICK_OPENING_RADIUS, LICK_RADIUS, swell) * (1.0 - LICK_SHRINK * life * life);
            vec2 off = across - center;
            float shaped = off.y > 0.0 ? off.y * STRETCH_ABOVE : off.y * SQUASH_BELOW;
            float depth = 1.0 - length(vec3(off.x, shaped, (along - travelled) / LICK_STRETCH)) / max(radius, 1e-3);
            hottest = max(hottest, depth * (1.0 - 0.45 * life));
        }
    }
    return hottest;
}

// Whether an ember lights the point, and how bright, from the waves nearest it.
float emberGlow(vec2 across, float along, float ticks) {
    float poured = ticks * EMBER_POUR_PER_TICK;
    float wave = floor((along - poured) / EMBER_SPACING);
    float reach = max(AxisReach.w, 1e-3);
    float brightest = 0.0;
    for (int near = -1; near <= 1; near++) {
        float index = wave + float(near);
        for (int lane = 0; lane < EMBER_LANES; lane++) {
            vec3 seed = vec3(index, float(lane), 41.0);
            float travelled = (index + hash(seed)) * EMBER_SPACING + poured;
            if (!burningAt(travelled) || hash(seed + 2.3) > EMBER_PRESENCE) {
                continue;
            }
            vec2 center = laneAt(EMBER_SPREAD * sqrt(hash(seed + 6.1)), hash(seed + 9.7), travelled);
            center.y += travelled * EMBER_CLIMB_PER_BLOCK;
            vec3 off = vec3(across - center, along - travelled);
            if (length(off) < EMBER_RADIUS) {
                float flicker = step(0.25, hash(seed + floor(ticks * 2.0)));
                brightest = max(brightest, (1.0 - travelled / reach) * flicker);
            }
        }
    }
    return brightest;
}

// The voxel's color and opacity, its alpha zero where it burns nothing.
vec4 voxelAt(vec3 p, float ticks) {
    if (HitWall.w >= 0.0 && dot(p - HitWall.xyz, NormalFront.xyz) < 0.0) {
        return vec4(0.0);
    }
    vec3 fromSpout = p - ApexTan.xyz;
    float along = dot(fromSpout, AxisReach.xyz);
    if (along < 0.0 || along > AxisReach.w + LICK_RADIUS * LICK_STRETCH) {
        return vec4(0.0);
    }
    vec3 off = fromSpout - AxisReach.xyz * along;
    vec3 up = cross(SideTail.xyz, AxisReach.xyz);
    vec2 across = vec2(dot(off, SideTail.xyz), dot(off, up));
    float nearOpacity = mix(NEAR_OPACITY_SHARE, 1.0, smoothstep(NEAR_CLEAR_UNTIL, FAR_SOLID_FROM, along));
    float ember = emberGlow(across, along, ticks);
    if (ember > 0.0) {
        return vec4(EMBER, EMBER_OPACITY * ember * nearOpacity);
    }
    float heat = lickHeat(across, along, ticks);
    if (heat <= 0.0) {
        return vec4(0.0);
    }
    vec3 cell = (p + Grid.xyz) * BREAKUP_CELLS + vec3(0.0, -ticks * BREAKUP_RISE_PER_TICK, 0.0);
    heat += (noise(cell) - 0.5) * BREAKUP_STRENGTH;
    vec3 color;
    if (heat > CORE_HEAT) {
        color = CORE;
    } else if (heat > YELLOW_HEAT) {
        color = YELLOW;
    } else if (heat > ORANGE_HEAT) {
        color = ORANGE;
    } else if (heat > RED_HEAT) {
        color = RED;
    } else {
        return vec4(0.0);
    }
    return vec4(color, FLAME_OPACITY * nearOpacity);
}

void main() {
    float ticks = GameTime * TICKS_PER_DAY;
    vec3 ray = normalize(rayPoint);
    float far = length(rayPoint);
    // Where the ray enters the box; a camera inside it starts at the eye.
    vec3 inverse = 1.0 / ray;
    vec3 toLow = (BoxLowDepth.xyz) * inverse;
    vec3 toHigh = (BoxHigh.xyz) * inverse;
    vec3 nearest = min(toLow, toHigh);
    float enter = max(max(max(nearest.x, nearest.y), nearest.z), 0.05);
    if (enter >= far) {
        discard;
    }
    // Walk the world's voxel grid along the ray, each voxel visited once.
    vec3 grid = ray * enter + Grid.xyz;
    vec3 cell = floor(grid * TEXELS_PER_BLOCK);
    vec3 step = sign(ray);
    vec3 crossing = abs(inverse) / TEXELS_PER_BLOCK;
    vec3 nextEdge = (cell + max(step, 0.0)) / TEXELS_PER_BLOCK;
    vec3 untilEdge = (nextEdge - grid) * inverse;
    untilEdge = max(untilEdge, 0.0) + enter;
    vec3 color = vec3(0.0);
    float opacity = 0.0;
    float firstHit = -1.0;
    float travel = enter;
    for (int index = 0; index < MAX_VOXELS; index++) {
        vec3 center = (cell + 0.5) / TEXELS_PER_BLOCK - Grid.xyz;
        vec4 voxel = voxelAt(center, ticks);
        if (voxel.a > 0.0) {
            if (firstHit < 0.0) {
                firstHit = travel;
            }
            color += (1.0 - opacity) * voxel.a * voxel.rgb;
            opacity += (1.0 - opacity) * voxel.a;
            if (opacity > 0.97) {
                break;
            }
        }
        if (untilEdge.x < untilEdge.y && untilEdge.x < untilEdge.z) {
            travel = untilEdge.x;
            untilEdge.x += crossing.x;
            cell.x += step.x;
        } else if (untilEdge.y < untilEdge.z) {
            travel = untilEdge.y;
            untilEdge.y += crossing.y;
            cell.y += step.y;
        } else {
            travel = untilEdge.z;
            untilEdge.z += crossing.z;
            cell.z += step.z;
        }
        if (travel > far) {
            break;
        }
    }
    if (opacity < 0.003) {
        discard;
    }
    fragColor = vec4(color / opacity, opacity);
    vec4 clip = ProjMat * ModelViewMat * vec4(ray * firstHit, 1.0);
    float ndc = clip.z / clip.w;
    gl_FragDepth = BoxLowDepth.w > 0.5 ? ndc : ndc * 0.5 + 0.5;
}
