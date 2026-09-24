// Mingle noise of decision noise-mingled-type-textures: a field over world
// XZ and GameTime whose value spreads close to evenly over [0, 1), so the
// fragments falling in a band [lo, hi) cover close to hi - lo of a surface.
// A shader under shaders/core imports it by its quoted relative name.

const float MINGLE_TAU = 6.2831853;
// Noise cells per block: the size of one mingled swirl.
const float MINGLE_CELLS_PER_BLOCK = 2.0;
// Phase units the noise spans; fract of a phase spread over several units
// is close to uniform, which keeps patch area in step with the band width.
const float MINGLE_PHASE_SPAN = 4.0;
// GameTime is the fraction of a 24000-tick day, so whole cycles per day
// keep the motion seamless when the day wraps.
const float MINGLE_DRIFT_CYCLES_PER_DAY = 60.0;
const float MINGLE_COUNTER_DRIFT_CYCLES_PER_DAY = 90.0;
const float MINGLE_SWELL_CYCLES_PER_DAY = 150.0;
// Blocks the noise drifts around its circle, and phase units the swell sways.
const float MINGLE_DRIFT_RADIUS = 0.5;
const float MINGLE_SWELL_PHASE = 0.35;
// Keeps the last band's exclusive upper edge reachable.
const float MINGLE_BELOW_ONE = 0.99999;

float mingleHash(ivec2 cell) {
    uint h = uint(cell.x) * 0x8da6b343u ^ uint(cell.y) * 0xd8163841u;
    h = (h ^ (h >> 15u)) * 0x2c1b3c6du;
    h ^= h >> 12u;
    return float(h & 0xFFFFu) / 65536.0;
}

float mingleValueNoise(vec2 p) {
    ivec2 cell = ivec2(floor(p));
    vec2 f = fract(p);
    vec2 s = f * f * (3.0 - 2.0 * f);
    float a = mingleHash(cell);
    float b = mingleHash(cell + ivec2(1, 0));
    float c = mingleHash(cell + ivec2(0, 1));
    float d = mingleHash(cell + ivec2(1, 1));
    return mix(mix(a, b, s.x), mix(c, d, s.x), s.y);
}

vec2 mingleCircle(float cyclesPerDay, float gameTime) {
    float angle = gameTime * MINGLE_TAU * cyclesPerDay;
    return MINGLE_DRIFT_RADIUS * vec2(cos(angle), sin(angle));
}

// The mingle value at a world XZ position and GameTime, in [0, 1).
float mingleNoise(vec2 worldXZ, float gameTime) {
    vec2 p = worldXZ * MINGLE_CELLS_PER_BLOCK;
    float field = mingleValueNoise(p + mingleCircle(MINGLE_DRIFT_CYCLES_PER_DAY, gameTime))
        + 0.5 * mingleValueNoise(p * 2.03 - mingleCircle(MINGLE_COUNTER_DRIFT_CYCLES_PER_DAY, gameTime) + 17.0);
    float phase = field * MINGLE_PHASE_SPAN
        + MINGLE_SWELL_PHASE * sin(gameTime * MINGLE_TAU * MINGLE_SWELL_CYCLES_PER_DAY);
    float triangle = 1.0 - abs(1.0 - 2.0 * fract(phase));
    return min(triangle, MINGLE_BELOW_ONE);
}
