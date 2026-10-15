#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

// Flames' fire field (decision flames-pixelated-fire-stream): one body of
// pixelated flame, marched voxel by voxel through the world's grid of 16
// texels to the block. The body is the jet's cone from the glove, and where a
// block cuts the look off, the damped bounce off its face, the two merged by
// a smooth union, the operator's ruling: the jet and its bounce blend as one
// flame. Nothing burns behind the face the fire hits, and the fire thins as
// it nears it. Each voxel is colored in hard steps from a white-yellow core
// through orange and red to dark soot; the flames rise and flicker, never
// spin, and move quick. TRANSLUCENT blend; the nearest voxel drawn writes the
// depth.

// Must match FireField.pack: ten std140 rows.
layout(std140) uniform FireField {
    vec4 ApexTan;      // the glove, and the tangent of half the cone's angle
    vec4 AxisEnd;      // the look, and blocks down it the jet burns to
    vec4 SideTail;     // the look's side, and blocks down it the burning starts
    vec4 BounceLength; // the bounce's way, and blocks it runs, negative with no bounce
    vec4 HitWall;      // the hit point, and blocks from the glove to it, negative with no wall
    vec4 NormalFrays;  // the hit face's normal, and whether the jet's front is open and frays
    vec4 ClimbDepth;   // the way the bounce climbs, and whether depth runs 0 to 1
    vec4 BoxLow;
    vec4 BoxHigh;
    vec4 Grid;         // the camera's world position modulo 16, so the voxels hold to the world
};

in vec3 rayPoint;

out vec4 fragColor;

const float TICKS_PER_DAY = 24000.0;
const float TEXELS_PER_BLOCK = 16.0;
const int MAX_VOXELS = 480;
// The thinnest the jet runs, at the glove, in blocks.
const float NOZZLE_BLOCKS = 0.06;
// How far the fire's middle lifts per block it travels, and how much taller it
// burns above its middle than below, as flames climb.
const float LIFT_PER_BLOCK = 0.10;
const float STRETCH_ABOVE = 0.65;
const float SQUASH_BELOW = 1.35;
// Must match FireField: the bounce splays at this many times the jet's angle,
// climbs this many blocks per block squared, and runs this share of the front's push past the wall.
const float BOUNCE_SPLAY = 2.0;
const float BOUNCE_RISE = 0.25;
const float BOUNCE_KEEP = 0.35;
// Under a ceiling, how much of the look runs along the face before the roll heads one way: below the first
// share it spreads evenly around the hit, above the second it rolls the way the look runs.
const float CEILING_EVEN_BELOW = 0.15;
const float CEILING_AIMED_ABOVE = 0.5;
// How wide the smooth union blends the jet into its bounce, in body units.
const float BLEND = 0.45;
// The fire thins over this many blocks in front of the face it hits, by this much body.
const float FACE_THINNING_BLOCKS = 0.3;
const float FACE_THINNING = 0.5;
// How far past an open end the body runs before it stops outright, in blocks.
const float FRAY_REACH = 0.4;
// Below this body no voxel can burn, however the noise falls.
const float EMPTY_BODY = -0.4;
// Noise cells per block across the fire and up it; the up runs coarser, so the
// flame breaks into upright tongues.
const float CELLS_ACROSS = 3.5;
const float CELLS_UP = 2.0;
const float CELLS_ALONG = 2.5;
// Blocks a tick the fire climbs and pours outward, and flicker cells a tick.
const float RISE_PER_TICK = 0.16;
const float POUR_PER_TICK = 0.55;
const float FLICKER_PER_TICK = 0.7;
const float FLICKER_STRENGTH = 0.35;
// The hard color steps, hottest first, the heat each one starts at, and how opaque each voxel draws.
const vec3 CORE = vec3(1.00, 0.95, 0.70);
const vec3 YELLOW = vec3(1.00, 0.78, 0.22);
const vec3 ORANGE = vec3(0.97, 0.48, 0.09);
const vec3 RED = vec3(0.78, 0.20, 0.05);
const vec3 SOOT = vec3(0.25, 0.09, 0.06);
const float CORE_HEAT = 0.95;
const float YELLOW_HEAT = 0.72;
const float ORANGE_HEAT = 0.50;
const float RED_HEAT = 0.32;
const float SOOT_HEAT = 0.22;
const float FLAME_OPACITY = 0.30;
const float SOOT_OPACITY = 0.14;
// The operator's ruling: the near jet is see-through so the far end reads.
const float NEAR_OPACITY_SHARE = 0.25;
const float NEAR_CLEAR_UNTIL = 0.75;
const float FAR_SOLID_FROM = 3.0;
const float NO_BODY = -10.0;

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

float turbulence(vec3 p) {
    float sum = 0.0;
    float amplitude = 0.55;
    for (int octave = 0; octave < 3; octave++) {
        sum += amplitude * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 4.1);
        amplitude *= 0.5;
    }
    return sum;
}

// One cone of fire at a point: how far inside its body the point stands, the
// blocks the fire has traveled there, how near a frayed end it stands, and
// the point across the cone and along it, for the noise.
struct Part {
    float body;
    float traveled;
    float edge;
    vec3 local;
};

// A cone's body at a point offset from its centerline, in its own frame, and
// standing the given blocks behind the cone's start along its line.
float coneBody(vec2 across, float radius, float traveled, float behind, float lift) {
    float upFromMiddle = across.y - traveled * lift;
    float shaped = upFromMiddle > 0.0 ? upFromMiddle * STRETCH_ABOVE : upFromMiddle * SQUASH_BELOW;
    return 1.0 - length(vec3(across.x, shaped, behind)) / max(radius, NOZZLE_BLOCKS);
}

Part jetPart(vec3 p) {
    vec3 fromGlove = p - ApexTan.xyz;
    float along = dot(fromGlove, AxisEnd.xyz);
    vec3 off = fromGlove - AxisEnd.xyz * along;
    vec3 up = cross(SideTail.xyz, AxisEnd.xyz);
    vec2 across = vec2(dot(off, SideTail.xyz), dot(off, up));
    float tail = SideTail.w;
    bool frays = NormalFrays.w > 0.5;
    float fromTail = tail > 0.0 ? along - tail : 1.0;
    float fromEnd = frays ? AxisEnd.w - along : 1.0;
    Part part;
    part.body = coneBody(across, along * ApexTan.w, along, 0.0, LIFT_PER_BLOCK);
    if (along < 0.0 || fromTail < -FRAY_REACH || fromEnd < -FRAY_REACH) {
        part.body = NO_BODY;
    }
    part.traveled = along;
    part.edge = clamp(min(fromTail, fromEnd), 0.0, 1.0);
    part.local = vec3(across, along);
    return part;
}

Part bouncePart(vec3 p) {
    Part part;
    part.body = NO_BODY;
    part.traveled = 0.0;
    part.edge = 1.0;
    part.local = vec3(0.0);
    float runs = BounceLength.w;
    float wall = HitWall.w;
    if (runs < 0.0 || wall < 0.0) {
        return part;
    }
    vec3 bounce = BounceLength.xyz;
    vec3 fromHit = p - HitWall.xyz;
    float tan = ApexTan.w;
    // The operator's ruling for the underside of a block: the fire rolls along it away from the glove, its whole
    // body hanging just under the face, and aimed nearly straight up it spreads evenly around the hit.
    bool ceiling = NormalFrays.y < -0.5;
    float lift = LIFT_PER_BLOCK;
    if (ceiling) {
        vec3 normal = NormalFrays.xyz;
        vec3 inPlane = fromHit - normal * dot(fromHit, normal);
        float outwardLength = length(inPlane);
        vec3 outward = outwardLength > 1e-4 ? inPlane / outwardLength : bounce;
        vec3 skim = AxisEnd.xyz - normal * dot(AxisEnd.xyz, normal);
        float evenness = 1.0 - smoothstep(CEILING_EVEN_BELOW, CEILING_AIMED_ABOVE, length(skim));
        bounce = normalize(mix(bounce, outward, evenness));
        lift = 0.0;
    }
    float alongLine = dot(fromHit, bounce);
    float along = clamp(alongLine, 0.0, runs + FRAY_REACH);
    // A point on the near side of the hit stands behind the bounce's start: its distance there counts, or every
    // point back along the bounce's line toward the glove reads as inside the bounce.
    float behind = min(alongLine, 0.0);
    float radius = wall * tan + along * tan * BOUNCE_SPLAY;
    vec3 center = HitWall.xyz + bounce * along + ClimbDepth.xyz * (BOUNCE_RISE * along * along);
    if (ceiling) {
        // The body's top reaches its radius over STRETCH_ABOVE above its middle; hang the middle that far under.
        center += NormalFrays.xyz * (radius / STRETCH_ABOVE);
    }
    vec3 side = vec3(-bounce.z, 0.0, bounce.x);
    side = dot(side, side) > 1e-6 ? normalize(side) : SideTail.xyz;
    vec3 up = cross(side, bounce);
    if (up.y < 0.0) {
        side = -side;
        up = -up;
    }
    vec3 off = p - center;
    vec2 across = vec2(dot(off, side), dot(off, up));
    float traveled = wall + along;
    float tailAlong = (SideTail.w - wall) * BOUNCE_KEEP;
    float fromTail = SideTail.w > wall ? along - tailAlong : 1.0;
    float fromEnd = runs - alongLine;
    part.body = coneBody(across, radius, traveled, behind, lift);
    if (fromTail < -FRAY_REACH || fromEnd < -FRAY_REACH) {
        part.body = NO_BODY;
    }
    part.traveled = traveled;
    part.edge = clamp(min(fromTail, fromEnd), 0.0, 1.0);
    part.local = vec3(across, traveled);
    return part;
}

float flameAt(vec3 local, float ticks) {
    return turbulence(vec3(local.x * CELLS_ACROSS,
                           (local.y - ticks * RISE_PER_TICK) * CELLS_UP,
                           (local.z - ticks * POUR_PER_TICK) * CELLS_ALONG));
}

// The voxel's color and opacity, its alpha zero where it burns nothing.
vec4 voxelAt(vec3 p, float ticks) {
    float faceDistance = 1e9;
    if (HitWall.w >= 0.0) {
        faceDistance = dot(p - HitWall.xyz, NormalFrays.xyz);
        if (faceDistance < 0.0) {
            return vec4(0.0);
        }
    }
    Part jet = jetPart(p);
    Part bounce = bouncePart(p);
    // A smooth union: where the two cones meet, the body swells to fill the turn rather than showing two shapes.
    float weight = clamp(0.5 + 0.5 * (jet.body - bounce.body) / BLEND, 0.0, 1.0);
    float body = mix(bounce.body, jet.body, weight) + BLEND * weight * (1.0 - weight) * 0.5;
    body -= (1.0 - smoothstep(0.0, FACE_THINNING_BLOCKS, faceDistance)) * FACE_THINNING;
    if (body < EMPTY_BODY) {
        return vec4(0.0);
    }
    float traveled = mix(bounce.traveled, jet.traveled, weight);
    float edge = mix(bounce.edge, jet.edge, weight);
    float flame;
    if (weight > 0.99) {
        flame = flameAt(jet.local, ticks);
    } else if (weight < 0.01) {
        flame = flameAt(bounce.local, ticks);
    } else {
        flame = mix(flameAt(bounce.local, ticks), flameAt(jet.local, ticks), weight);
    }
    float flicker = noise(vec3(traveled * 1.2, 0.0, ticks * FLICKER_PER_TICK)) - 0.5;
    float heat = body * 1.5 + (flame - 0.5) * 1.3 + flicker * FLICKER_STRENGTH - traveled / 6.0 * 0.3;
    heat -= (1.0 - edge) * (0.6 + flame * 0.8);

    vec3 color = SOOT;
    float alpha = FLAME_OPACITY;
    if (heat > CORE_HEAT) {
        color = CORE;
    } else if (heat > YELLOW_HEAT) {
        color = YELLOW;
    } else if (heat > ORANGE_HEAT) {
        color = ORANGE;
    } else if (heat > RED_HEAT) {
        color = RED;
    } else if (heat > SOOT_HEAT) {
        alpha = SOOT_OPACITY;
    } else {
        return vec4(0.0);
    }
    alpha *= mix(NEAR_OPACITY_SHARE, 1.0, smoothstep(NEAR_CLEAR_UNTIL, FAR_SOLID_FROM, traveled));
    return vec4(color, alpha);
}

void main() {
    float ticks = GameTime * TICKS_PER_DAY;
    vec3 ray = normalize(rayPoint);
    float far = length(rayPoint);
    // Where the ray enters the box; a camera inside it starts at the eye.
    vec3 inverse = 1.0 / ray;
    vec3 toLow = (BoxLow.xyz) * inverse;
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
    gl_FragDepth = ClimbDepth.w > 0.5 ? ndc : ndc * 0.5 + 0.5;
}
