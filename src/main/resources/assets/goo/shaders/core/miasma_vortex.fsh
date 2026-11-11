#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

// Drain's miasma vortex (decision drain-casts-a-filled-tornadic-miasma-vortex-along-the-look): the cone
// Drain strikes, from the glove hand along the look, filled with a turning cloud of miasma. Fine streaks
// run through the cloud's whole volume on helices around the look, the rifling of the portal's swirl band
// (decision tornadic-halo-spirals-out-from-the-rim) run down a cone: they turn the screw way and pour back
// toward the hand, tighter toward the narrow end, so the vortex reads as a tornado being drawn in. Each
// fragment marches its ray front to back through the cone, stopping at whatever the world drew there, so
// the miasma lies over floor, wall and sky alike and stands in front of a mob inside it rather than
// covering it from behind. TRANSLUCENT blend, capped so a mob inside stays visible.

// Must match MiasmaVortexPass.pack: five std140 rows.
layout(std140) uniform MiasmaVortex {
    vec4 ApexTan;     // the glove hand, camera relative, and the tangent of half the cone's angle
    vec4 AxisLength;  // the unit look, and blocks the cone reaches along it
    vec4 SideDepth;   // a unit direction square to the look, and whether depth runs 0 to 1
    vec4 BodyColor;   // the goo's wheel color, the cloud's
    vec4 StreakColor; // the goo's bright color, the streaks'
};

uniform sampler2D MiasmaDepthSampler;

in vec3 rayPoint;

out vec4 fragColor;

// GameTime runs from zero to one over a day of 24000 ticks; this many seconds make one day.
const float SECONDS_PER_DAY = 1200.0;
const float TAU = 6.28318530718;
// Samples along each ray through the cone.
const int STEPS = 40;

// Seconds the vortex takes to turn once around the look; divides the day whole, so the turn never jumps.
const float TURN_SECONDS = 2.5;
// Turns the helices make per block along the look, at the narrow end and at the wide end: tighter at the
// hand, as a tornado winds tighter toward its foot.
const float SCREW_NEAR = 0.55;
const float SCREW_FAR = 0.2;
// Blocks a second the miasma pours back along the look toward the hand.
const float POUR_PER_SECOND = 1.6;

// The cloud: noise cells around a cross-section, along the look per block, and how much it swells and thins.
const float CLOUD_ACROSS = 2.2;
const float CLOUD_ALONG = 1.3;
const float CLOUD_LEAST = 0.35;
const float CLOUD_OFFSET = 64.0;
const float CLOUD_NEVER_WRAPS = 4096.0;
// The streaks: cells around the look (whole, so the streaks close on themselves), cells along per block
// (few, so each streak runs long down its helix), radial layers through the volume, and how sharply a
// streak stands out of the cloud, higher thinner.
const float STREAKS_AROUND = 26.0;
const float STREAK_ALONG = 0.9;
const float STREAK_LAYERS = 3.0;
const float STREAK_SHARPNESS = 3.0;

// How much miasma a block of cloud holds, how much more a streak holds, and how much the wall of the cone
// thickens it, so its shape reads from inside it.
const float CLOUD_DENSITY = 0.42;
const float STREAK_DENSITY = 1.6;
const float WALL_DENSITY = 0.6;
// Where the wall thickening starts, as a share of the radius, and the share at the rim it feathers over.
const float WALL_FROM = 0.6;
const float RIM_FEATHER = 0.15;
// Blocks from the hand the miasma thickens over, so the glove itself stays clear, and the share of the
// length the far end feathers over.
const float HAND_CLEAR = 0.7;
const float FAR_FEATHER = 0.15;
// The most the miasma covers what lies behind it, so a mob inside still shows.
const float MOST_COVER = 0.72;
// How far the brightest streaks burn toward white, and how dark the cloud's core runs.
const float STREAK_HOT = 0.35;
const float CORE_SHADE = 0.55;

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

// Smooth value noise, wrapping every period cells along x so it closes around the look.
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
    vec3 offset = eye - ApexTan.xyz;
    float b = dot(offset, dir);
    float c = dot(offset, offset) - radius * radius;
    float h = b * b - c;
    if (h < 0.0) {
        return vec2(1.0, 0.0);
    }
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

// The miasma at a point: how dense it is and how much of it is streak, or nothing outside the cone.
vec2 miasmaAt(vec3 point, float seconds) {
    vec3 offset = point - ApexTan.xyz;
    float along = dot(offset, AxisLength.xyz);
    float reachLength = AxisLength.w;
    if (along <= 0.0 || along > reachLength) {
        return vec2(0.0);
    }
    vec3 radial = offset - along * AxisLength.xyz;
    float share = length(radial) / (along * ApexTan.w);
    if (share > 1.0) {
        return vec2(0.0);
    }
    vec3 lift = cross(AxisLength.xyz, SideDepth.xyz);
    float theta = atan(dot(radial, lift), dot(radial, SideDepth.xyz));
    float reach = along / reachLength;

    // The screw: the helix's turn so far along the look, and the turn of the whole vortex in time.
    float screwed = along * mix(SCREW_NEAR, SCREW_FAR, reach);
    float turned = theta / TAU + screwed - seconds / TURN_SECONDS;
    float poured = along + seconds * POUR_PER_SECOND;

    float angle = turned * TAU;
    vec3 cloudAt = vec3(cos(angle) * share * CLOUD_ACROSS, sin(angle) * share * CLOUD_ACROSS, poured * CLOUD_ALONG);
    // the cloud never wraps: its cells sit well clear of zero, under a period nothing reaches
    float cloud = mix(CLOUD_LEAST, 1.0, noise(cloudAt + CLOUD_OFFSET, CLOUD_NEVER_WRAPS) * 0.65
            + noise(cloudAt * 2.1 + CLOUD_OFFSET, CLOUD_NEVER_WRAPS) * 0.35);

    vec3 streakAt = vec3(turned * STREAKS_AROUND, poured * STREAK_ALONG, share * STREAK_LAYERS);
    float streak = noise(streakAt, STREAKS_AROUND) * 0.7
            + noise(streakAt * vec3(2.0, 2.0, 1.0), STREAKS_AROUND * 2.0) * 0.3;
    streak = pow(streak, STREAK_SHARPNESS);

    float wall = 1.0 + WALL_DENSITY * smoothstep(WALL_FROM, 1.0, share);
    float feather = (1.0 - smoothstep(1.0 - RIM_FEATHER, 1.0, share))
            * smoothstep(0.0, HAND_CLEAR, along)
            * (1.0 - smoothstep(1.0 - FAR_FEATHER, 1.0, reach));
    float density = feather * wall * (CLOUD_DENSITY * cloud + STREAK_DENSITY * streak);
    return vec2(density, streak * 3.0 / (cloud + streak * 3.0));
}

void main() {
    mat4 viewToWorld = inverse(ModelViewMat);
    vec3 eye = (viewToWorld * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    vec3 dir = normalize(rayPoint - eye);
    float ballRadius = AxisLength.w * sqrt(1.0 + ApexTan.w * ApexTan.w);
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
    vec4 gathered = vec4(0.0);
    for (int index = 0; index < STEPS && gathered.a < MOST_COVER; index++) {
        float travelled = near + (float(index) + jitter) * stride;
        vec2 miasma = miasmaAt(eye + dir * travelled, seconds);
        if (miasma.x <= 0.0) {
            continue;
        }
        float taken = 1.0 - exp(-miasma.x * stride);
        vec3 streakColor = mix(StreakColor.rgb, vec3(1.0), STREAK_HOT * miasma.y);
        vec3 color = mix(body * CORE_SHADE, streakColor, miasma.y);
        gathered.rgb += (1.0 - gathered.a) * taken * color;
        gathered.a += (1.0 - gathered.a) * taken;
    }
    if (gathered.a <= 0.0) {
        discard;
    }
    fragColor = vec4(gathered.rgb / gathered.a, min(gathered.a, MOST_COVER));
}
