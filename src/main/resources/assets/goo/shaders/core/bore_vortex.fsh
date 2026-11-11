#version 330

#moj_import <minecraft:globals.glsl>

// Bore's vortex (decisions bore-vortex-with-a-worldspace-shake,
// diagnose-and-correct-bores-small-distant-vortex): a long drilling vortex
// down the tunnel from the glove. Its tube's walls are rifled: dust arms wind
// about the axis as a screw, packed with fine streaks running along each arm,
// and the screw turns so its arms run forward down the tunnel into the rock.
// Thin as it leaves the glove and at the reach. TRANSLUCENT blend, both faces
// drawn, so the far wall shows through the near.

in float along;
in vec2 discPos;

out vec4 fragColor;

const float TAU = 6.2831853;
// GameTime runs from zero to one over a day of 24000 ticks; this many seconds make one day. The spin
// below turns a whole number of times a day, so the screw carries on unbroken as the day wraps.
const float SECONDS_PER_DAY = 1200.0;
const vec3 DUST_COLOR = vec3(0.561, 0.478, 0.357);
const vec3 HIGHLIGHT_COLOR = vec3(0.918, 0.816, 0.565);
// Dust arms wound about the axis, and how many turns each makes from the glove to the reach.
const float ARMS = 3.0;
const float TWIST_TURNS = 5.0;
// How many turns a second the screw spins, its arms running forward down the tunnel. Tuned by eye.
const float SPIN_TURNS_PER_SECOND = 1.5;
// Streak cells across one arm's width, and down the whole tunnel along the arms: few along runs them long.
const float STREAKS_ACROSS = 6.0;
const float STREAKS_ALONG = 9.0;
// How strongly the walls glow between the arms, on the arms, and along a streak on an arm.
const float BETWEEN_ARMS = 0.12;
const float ON_ARMS = 0.35;
const float ON_STREAKS = 0.5;
// How far along the tube it feathers up from the glove, and from where it feathers out toward the reach.
const float GLOVE_FEATHER = 0.08;
const float REACH_FEATHER = 0.8;

// A value nothing to one fixed for each cell, the same at every frame.
float hash(vec2 cell) {
    return fract(sin(dot(cell, vec2(127.1, 311.7))) * 43758.5453);
}

// A cell's value, its place across the arms taken back into one lap around the tube, so the
// streaks close on themselves all round.
float lapped(vec2 cell, float period) {
    return hash(vec2(mod(cell.x, period), cell.y));
}

// Smooth noise over cells, wrapping every lap around the tube.
float streakNoise(vec2 at, float period) {
    vec2 cell = floor(at);
    vec2 within = fract(at);
    within = within * within * (3.0 - 2.0 * within);
    float low = mix(lapped(cell, period), lapped(cell + vec2(1.0, 0.0), period), within.x);
    float high = mix(lapped(cell + vec2(0.0, 1.0), period), lapped(cell + vec2(1.0, 1.0), period), within.x);
    return mix(low, high, within.y);
}

void main() {
    float around = atan(discPos.y, discPos.x) / TAU;
    float seconds = GameTime * SECONDS_PER_DAY;
    // Which arm a fragment sits on and how far across it: constant along each winding arm.
    float helix = around * ARMS + along * TWIST_TURNS * ARMS - seconds * SPIN_TURNS_PER_SECOND * ARMS;
    float arm = smoothstep(0.3, 1.0, 0.5 + 0.5 * cos(TAU * helix));
    vec2 at = vec2(helix * STREAKS_ACROSS, along * STREAKS_ALONG);
    float period = ARMS * STREAKS_ACROSS;
    float streaks = 0.65 * streakNoise(at, period) + 0.35 * streakNoise(at * vec2(2.0, 1.0), period * 2.0);
    streaks = streaks * streaks;

    float feather = smoothstep(0.0, GLOVE_FEATHER, along) * (1.0 - smoothstep(REACH_FEATHER, 1.0, along));
    float alpha = feather * (BETWEEN_ARMS + arm * (ON_ARMS + ON_STREAKS * streaks));
    vec3 color = mix(DUST_COLOR, HIGHLIGHT_COLOR, arm * streaks);

    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
