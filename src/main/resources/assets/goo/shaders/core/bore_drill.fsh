#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

// Bore's drill (decision bore-shows-a-big-drill-from-the-glove): a solid cone bit, widest at the glove
// and pointed at the tip, as the Bore icon draws it. Two grooves are cut into it, winding round it from
// the glove to the tip: steel lands between brown grooves, a dark line where a groove's wall meets a land.
// The grooves are real cuts, so the light falls into them and their walls catch it. The bit spins while
// Bore runs, so its grooves screw forward into the rock. Each fragment marches its ray to the bit's
// surface, stopping at whatever the world drew there, so the blocks it sinks into hide its buried length.
// Opaque where the ray strikes the bit; every other fragment is discarded.

// Must match BoreDrillPass.pack: three std140 rows.
layout(std140) uniform BoreDrill {
    vec4 ApexRadius;  // the glove, camera relative, and the bit's radius there
    vec4 AxisLength;  // the unit axis from the glove to the tip, and the bit's length along it
    vec4 SideDepth;   // a unit direction square to the axis, and whether depth runs 0 to 1
};

uniform sampler2D BoreDrillDepthSampler;

in vec3 rayPoint;

out vec4 fragColor;

const float TAU = 6.28318530718;
// GameTime runs from zero to one over a day of 24000 ticks; this many seconds make one day. The spin
// below turns a whole number of times a day, so the bit spins on unbroken as the day wraps.
const float SECONDS_PER_DAY = 1200.0;
// Marching steps along each ray, and how close to the surface counts as striking it.
const int STEPS = 96;
const float SURFACE = 0.0015;
// The grooves wind round the bit: their speed is not the cone's, so each step goes only this share of
// the distance the cone alone would allow.
const float STEP_SHARE = 0.55;
// Grooves round the bit, the turns each makes from the glove to the tip, and the bit's turns a second.
const float GROOVES = 2.0;
const float TURNS = 2.5;
const float SPIN_TURNS_PER_SECOND = 3.0;
// A groove's half width, as a share of its period round the bit, and how deep it cuts, as a share of
// the radius there.
const float GROOVE_HALF = 0.2;
const float GROOVE_DEPTH = 0.3;
// The Bore icon's tones (rock_bore.png): steel, light to dark; the grooves, light to dark; the outline.
const vec3 STEEL_LIGHT = vec3(0.882, 0.914, 0.949);
const vec3 STEEL_DARK = vec3(0.380, 0.349, 0.349);
const vec3 GROOVE_LIGHT = vec3(0.757, 0.584, 0.427);
const vec3 GROOVE_DARK = vec3(0.345, 0.220, 0.141);
const vec3 EDGE_COLOR = vec3(0.149, 0.141, 0.133);
// The light, in view space: from the upper left, toward the viewer, as the icon is lit.
const vec3 VIEW_LIGHT = vec3(-0.55, 0.6, 0.58);
const float AMBIENT = 0.3;
const float SHINE = 24.0;
const float SHINE_STRENGTH = 0.45;

// The distance from the eye to whatever the world drew at this fragment, camera relative.
float sceneDistance(vec3 eye, mat4 viewToWorld) {
    float depth = texelFetch(BoreDrillDepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    float ndcDepth = SideDepth.w > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 view = inverse(ProjMat) * vec4(gl_FragCoord.xy / ScreenSize * 2.0 - 1.0, ndcDepth, 1.0);
    vec3 world = (viewToWorld * vec4(view.xyz / view.w, 1.0)).xyz;
    return length(world - eye);
}

// Where the ray enters and leaves the ball holding the whole bit, as distances along it.
vec2 throughBall(vec3 eye, vec3 dir) {
    vec3 middle = ApexRadius.xyz + AxisLength.xyz * (0.5 * AxisLength.w);
    float radius = length(vec2(0.5 * AxisLength.w, ApexRadius.w)) + 0.01;
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

// How much of a groove a point of the bit stands in: 1 at a groove's middle, 0 on a land.
float grooveAt(vec3 point, float seconds) {
    vec3 offset = point - ApexRadius.xyz;
    float along = clamp(dot(offset, AxisLength.xyz) / AxisLength.w, 0.0, 1.0);
    vec3 radial = offset - dot(offset, AxisLength.xyz) * AxisLength.xyz;
    vec3 lift = cross(AxisLength.xyz, SideDepth.xyz);
    float around = atan(dot(radial, lift), dot(radial, SideDepth.xyz)) / TAU;
    float phase = fract((around - along * TURNS + seconds * SPIN_TURNS_PER_SECOND) * GROOVES);
    return smoothstep(GROOVE_HALF, GROOVE_HALF * 0.35, abs(phase - 0.5));
}

// The distance from a point to the bit's grooved surface, under the true distance.
float bitDistance(vec3 point, float seconds) {
    vec3 offset = point - ApexRadius.xyz;
    float along = dot(offset, AxisLength.xyz);
    float bitLength = AxisLength.w;
    float radiusHere = ApexRadius.w * (1.0 - clamp(along / bitLength, 0.0, 1.0));
    float cut = radiusHere * (1.0 - GROOVE_DEPTH * grooveAt(point, seconds));
    float offAxis = length(offset - along * AxisLength.xyz);
    float slope = bitLength / length(vec2(bitLength, ApexRadius.w));
    float side = (offAxis - cut) * slope;
    return max(side, max(-along, along - bitLength));
}

vec3 normalAt(vec3 point, float seconds) {
    const vec2 e = vec2(0.002, 0.0);
    return normalize(vec3(
            bitDistance(point + e.xyy, seconds) - bitDistance(point - e.xyy, seconds),
            bitDistance(point + e.yxy, seconds) - bitDistance(point - e.yxy, seconds),
            bitDistance(point + e.yyx, seconds) - bitDistance(point - e.yyx, seconds)));
}

vec3 shade(vec3 point, vec3 dir, float seconds, mat4 viewToWorld) {
    vec3 normal = normalAt(point, seconds);
    vec3 light = normalize(mat3(viewToWorld) * VIEW_LIGHT);
    float lit = AMBIENT + (1.0 - AMBIENT) * max(dot(normal, light), 0.0);
    float groove = grooveAt(point, seconds);
    vec3 steel = mix(STEEL_DARK, STEEL_LIGHT, lit);
    steel += SHINE_STRENGTH * pow(max(dot(normal, normalize(light - dir)), 0.0), SHINE);
    vec3 brown = mix(GROOVE_DARK, GROOVE_LIGHT, lit);
    vec3 color = mix(steel, brown, smoothstep(0.35, 0.65, groove));
    // the groove's wall, where the land drops into it, drawn as the icon's dark outline
    float wall = smoothstep(0.08, 0.3, groove) * (1.0 - smoothstep(0.3, 0.55, groove));
    return mix(color, EDGE_COLOR, 0.7 * wall);
}

void main() {
    mat4 viewToWorld = inverse(ModelViewMat);
    vec3 eye = (viewToWorld * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    vec3 dir = normalize(rayPoint - eye);
    vec2 span = throughBall(eye, dir);
    float near = max(span.x, 0.0);
    float far = min(span.y, sceneDistance(eye, viewToWorld));
    if (far <= near) {
        discard;
    }
    float seconds = GameTime * SECONDS_PER_DAY;
    float travelled = near;
    for (int index = 0; index < STEPS; index++) {
        vec3 point = eye + dir * travelled;
        float gap = bitDistance(point, seconds);
        if (gap < SURFACE) {
            fragColor = vec4(shade(point, dir, seconds, viewToWorld), 1.0);
            return;
        }
        travelled += gap * STEP_SHARE;
        if (travelled > far) {
            break;
        }
    }
    discard;
}
