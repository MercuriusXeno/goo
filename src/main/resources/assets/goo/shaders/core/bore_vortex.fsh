#version 330

#moj_import <minecraft:globals.glsl>

// Bore's vortex (decisions bore-vortex-with-a-worldspace-shake,
// diagnose-and-correct-bores-small-distant-vortex): a long drilling vortex
// down the tunnel from the glove, drawn the way the portal's swirl band is
// (decision tornadic-halo-spirals-out-from-the-rim). Its tube is packed with
// fine, broken streaks of dust running around it at a slight slant, whirling
// fast the screw way; it glows softly under them, feathers away where the view
// grazes the tube so it shows no outline, and thins as it leaves the glove and
// at the reach. TRANSLUCENT blend, both faces drawn.

in float along;
in vec2 discPos;
in float facing;

out vec4 fragColor;

// GameTime runs from zero to one over a day of 24000 ticks; this many seconds make one day. The whirl
// below turns a whole number of times a day, so the vortex carries on unbroken as the day wraps.
const float SECONDS_PER_DAY = 1200.0;
const float TAU = 6.28318530718;
const vec3 DUST_COLOR = vec3(0.561, 0.478, 0.357);
const vec3 HIGHLIGHT_COLOR = vec3(0.918, 0.816, 0.565);
// How many turns a second the streaks whirl about the tunnel. Tuned by eye.
const float WHIRL_TURNS_PER_SECOND = 2.0;
// How many noise cells the streaks run in around the tube: fewer runs them longer.
const float STREAKS_AROUND = 18.0;
// How many noise cells lie down the tube: more packs the streaks finer.
const float STREAK_ROWS = 28.0;
// How many rows a streak climbs down the tube per cell around: the slant. Times the cells around it
// stays whole, so the streaks close on themselves all round.
const float SLANT = 1.0 / 3.0;
// How strongly the vortex glows under its streaks, and how much more along a streak.
const float GLOW = 0.08;
const float STREAK_GLOW = 0.7;
// How far the brightest streaks burn toward the highlight.
const float HOT = 0.8;
// How squarely the view must meet the wall before it shows in full.
const float GRAZE_FEATHER = 0.55;
// How far along the tube it feathers up from the glove, and from where it feathers out toward the reach.
const float GLOVE_FEATHER = 0.12;
const float REACH_FEATHER = 0.75;

// A value nothing to one fixed for each cell, the same at every frame.
float hash(vec2 cell) {
    return fract(sin(dot(cell, vec2(127.1, 311.7))) * 43758.5453);
}

// A cell's value, the cell taken back into the first lap around the tube: one lap is period cells
// around and, by the slant, period times SLANT rows down, so the streaks close on themselves.
float lapped(vec2 cell, float period) {
    float laps = floor(cell.x / period);
    return hash(cell - laps * vec2(period, period * SLANT));
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
    float around = atan(discPos.y, discPos.x) / TAU + 0.5;
    float seconds = GameTime * SECONDS_PER_DAY;
    float turned = (around - seconds * WHIRL_TURNS_PER_SECOND) * STREAKS_AROUND;
    vec2 at = vec2(turned, along * STREAK_ROWS + turned * SLANT);
    float streaks = 0.65 * streakNoise(at, STREAKS_AROUND) + 0.35 * streakNoise(at * 2.0, STREAKS_AROUND * 2.0);
    streaks = streaks * streaks;

    float feather = smoothstep(0.0, GRAZE_FEATHER, facing)
            * smoothstep(0.0, GLOVE_FEATHER, along) * (1.0 - smoothstep(REACH_FEATHER, 1.0, along));
    float alpha = feather * (GLOW + STREAK_GLOW * streaks);
    vec3 color = mix(DUST_COLOR, HIGHLIGHT_COLOR, HOT * streaks);

    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
