#version 330

#moj_import <minecraft:globals.glsl>
#moj_import "portal_texels.glsl"

// A gate's swirl band (decision tornadic-halo-spirals-out-from-the-rim): one dense, bright band
// straddling the rim, arching from just inside it, over the portal's view, to just outside it on the
// wall, peaking a little off the wall right over the rim. It is packed with fine streaks that run
// along the ring at a slight slant and turn slowly the screw way; its edges feather softly and its
// core over the rim burns toward white. It draws smooth and blended, reading the face coordinate
// unsnapped, off the texel grid the rim and surface glow keep. frameCoord runs zero to one around the
// rim, then zero to one across the arch from inside the rim to outside; texels carries how many
// texels each way runs, around the arch's peak and across it.
// A hole's mouth wears the same band (decision hole-mouths-wear-the-tornadic-halo): its vertex alpha
// marks the torn edge, its arch straddles the middle of the tear, and every part of it inside the
// tear holeTornEdge draws is cut, so the band starts at the tear.

in vec2 frameCoord;
flat in vec2 texels;
in vec4 frameColor;

out vec4 fragColor;

// GameTime runs from zero to one over a day of 24000 ticks; this many seconds make one day. Each
// period below divides it whole, so the band carries on unbroken as the day wraps.
const float SECONDS_PER_DAY = 1200.0;
const float TAU = 6.28318530718;
// How many seconds the streaks take to turn once around, tuned by eye.
const float BAND_TURN_SECONDS = 8.0;
// How many noise cells the streaks run in around the ring: fewer runs them longer.
const float BAND_STREAKS_AROUND = 18.0;
// How many noise cells lie across the band: more packs the streaks finer.
const float BAND_STREAK_ROWS = 7.0;
// How many rows a streak climbs across the band per cell around: the slant. Times the cells around it
// stays whole, so the streaks close on themselves all round.
const float BAND_SLANT = 1.0 / 3.0;
// How strongly the band glows under its streaks, and how much more along a streak.
const float BAND_GLOW = 0.35;
const float BAND_STREAK_GLOW = 0.65;
// How far the brightest streaks at the core burn toward white.
const float BAND_HOT = 0.6;
// How far in from each edge of the band its glow feathers up to full, as a share of its width.
const float BAND_FEATHER = 0.35;
// How wide the hot core over the rim runs, as a share of the band's width.
const float BAND_CORE = 0.2;

// A value nothing to one fixed for each cell, the same at every frame.
float hash(vec2 cell) {
    return fract(sin(dot(cell, vec2(127.1, 311.7))) * 43758.5453);
}

// A cell's value, the cell taken back into the first lap around the ring: one lap is period cells
// around and, by the slant, period times BAND_SLANT rows across, so the streaks close on themselves.
float lapped(vec2 cell, float period) {
    float laps = floor(cell.x / period);
    return hash(cell - laps * vec2(period, period * BAND_SLANT));
}

// Smooth noise over cells, wrapping every lap around the ring.
float streakNoise(vec2 at, float period) {
    vec2 cell = floor(at);
    vec2 within = fract(at);
    within = within * within * (3.0 - 2.0 * within);
    float low = mix(lapped(cell, period), lapped(cell + vec2(1.0, 0.0), period), within.x);
    float high = mix(lapped(cell + vec2(0.0, 1.0), period), lapped(cell + vec2(1.0, 1.0), period), within.x);
    return mix(low, high, within.y);
}

void main() {
    float around = frameCoord.x;
    float across = frameCoord.y;
    // How far out this fragment stands as a share of the oval's radius: the arch's peak, plus its
    // offset across the arch, the arch's texels across over the peak's radius in texels.
    float peak = mix(1.0, 1.0 - 0.5 * HOLE_TEAR_DEPTH, frameColor.a);
    float spread = peak + (across - 0.5) * texels.y * TAU / texels.x;
    if (frameColor.a > 0.5 && spread < holeTornEdge(around * TAU, GameTime)) {
        discard;
    }
    float seconds = GameTime * SECONDS_PER_DAY;
    float turned = (around - seconds / BAND_TURN_SECONDS) * BAND_STREAKS_AROUND;
    vec2 at = vec2(turned, across * BAND_STREAK_ROWS + turned * BAND_SLANT);
    float streaks = 0.65 * streakNoise(at, BAND_STREAKS_AROUND)
            + 0.35 * streakNoise(at * 2.0, BAND_STREAKS_AROUND * 2.0);
    streaks = streaks * streaks;
    float feather = smoothstep(0.0, BAND_FEATHER, across) * (1.0 - smoothstep(1.0 - BAND_FEATHER, 1.0, across));
    float core = exp(-pow((across - 0.5) / BAND_CORE, 2.0));
    float glow = feather * (BAND_GLOW + BAND_STREAK_GLOW * streaks);
    vec3 color = mix(frameColor.rgb, vec3(1.0), BAND_HOT * core * streaks);
    fragColor = vec4(color, glow);
}
