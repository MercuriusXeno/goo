// Mingle noise of decision noise-mingled-type-textures: each goo type past
// the largest draws over the layers below at an opacity from its own noise
// field of world position and GameTime, so the types form blobs that blend
// at short seams and never average into one sludge.
// A shader under shaders/core imports it by its quoted relative name.

const float MINGLE_TAU = 6.2831853;
// Noise cells per block: the size of one blob.
const float MINGLE_CELLS_PER_BLOCK = 1.5;
// Half the width of a seam, in field units: the crossfade between two types.
const float MINGLE_SEAM = 0.08;
// GameTime is the fraction of a 24000-tick day, so whole cycles per day keep
// the drift seamless when the day wraps: 40 = 600 ticks, 55 ~ 436, 70 ~ 343.
const float MINGLE_SWAY_CYCLES_PER_DAY = 40.0;
const float MINGLE_ROLL_CYCLES_PER_DAY = 55.0;
const float MINGLE_HEAVE_CYCLES_PER_DAY = 70.0;
// Cells each drift loop reaches from its centre.
const float MINGLE_DRIFT_CELLS = 0.6;
// Offsets each layer's field so every type blobs on its own.
const vec3 MINGLE_LAYER_SEED = vec3(37.1, 11.7, 53.3);
// The field's quantiles at 0, 1/16 ... 1, measured over four million
// uniform samples of mingleField, so a threshold at the quantile of 1 - share
// leaves share of the surface above it.
const float MINGLE_QUANTILES[17] = float[17](
    0.0150, 0.2865, 0.3365, 0.3727, 0.4026, 0.4290, 0.4536, 0.4771, 0.5000,
    0.5229, 0.5463, 0.5709, 0.5974, 0.6274, 0.6634, 0.7134, 0.9767);
const float MINGLE_QUANTILE_STEPS = 16.0;

float mingleHash(ivec3 cell) {
    uint h = uint(cell.x) * 0x8da6b343u ^ uint(cell.y) * 0xd8163841u ^ uint(cell.z) * 0xcb1ab31fu;
    h = (h ^ (h >> 15u)) * 0x2c1b3c6du;
    h ^= h >> 12u;
    return float(h & 0xFFFFu) / 65536.0;
}

float mingleValueNoise(vec3 p) {
    ivec3 cell = ivec3(floor(p));
    vec3 f = fract(p);
    vec3 s = f * f * (3.0 - 2.0 * f);
    float x00 = mix(mingleHash(cell), mingleHash(cell + ivec3(1, 0, 0)), s.x);
    float x10 = mix(mingleHash(cell + ivec3(0, 1, 0)), mingleHash(cell + ivec3(1, 1, 0)), s.x);
    float x01 = mix(mingleHash(cell + ivec3(0, 0, 1)), mingleHash(cell + ivec3(1, 0, 1)), s.x);
    float x11 = mix(mingleHash(cell + ivec3(0, 1, 1)), mingleHash(cell + ivec3(1, 1, 1)), s.x);
    return mix(mix(x00, x10, s.y), mix(x01, x11, s.y), s.z);
}

// Two octaves in [0, 1]; the quantile table is measured on exactly this.
float mingleField(vec3 p) {
    return (mingleValueNoise(p) + 0.5 * mingleValueNoise(p * 2.03 + 17.0)) / 1.5;
}

float mingleQuantile(float q) {
    float x = clamp(q, 0.0, 1.0) * MINGLE_QUANTILE_STEPS;
    int i = min(int(x), 15);
    return mix(MINGLE_QUANTILES[i], MINGLE_QUANTILES[i + 1], x - float(i));
}

// Closed loops in all three axes, phase-shifted per layer, so the blobs sway
// across the top and heave up and down the sides.
vec3 mingleDrift(float gameTime, float layer) {
    float sway = gameTime * MINGLE_TAU * MINGLE_SWAY_CYCLES_PER_DAY + layer;
    float roll = gameTime * MINGLE_TAU * MINGLE_ROLL_CYCLES_PER_DAY + layer * 1.7;
    float heave = gameTime * MINGLE_TAU * MINGLE_HEAVE_CYCLES_PER_DAY + layer * 2.3;
    return MINGLE_DRIFT_CELLS * vec3(cos(sway) + sin(roll), sin(heave), sin(sway) + cos(roll));
}

// The opacity a layer draws at: 1 for layer 0, else a smoothstep of its own
// field around the quantile of 1 - share, whose mean over the surface is share.
float mingleOpacity(vec3 worldPos, float gameTime, float share, float layer) {
    if (layer < 0.5) {
        return 1.0;
    }
    vec3 p = worldPos * MINGLE_CELLS_PER_BLOCK + mingleDrift(gameTime, layer) + layer * MINGLE_LAYER_SEED;
    float threshold = mingleQuantile(1.0 - share);
    return smoothstep(threshold - MINGLE_SEAM, threshold + MINGLE_SEAM, mingleField(p));
}
