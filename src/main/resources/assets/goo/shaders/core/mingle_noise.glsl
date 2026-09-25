// Mingle noise of decision noise-mingled-type-textures: each goo type past
// the largest draws over the layers below at an opacity from its own noise
// field of world position and GameTime, so the types form blobs that blend
// at short seams and never average into one sludge.
// A shader under shaders/core imports it by its quoted relative name.

const float MINGLE_TAU = 6.2831853;
// Noise cells per block: the size of one blob.
const float MINGLE_CELLS_PER_BLOCK = 2.5;
// Half the width of a seam, in field units: the crossfade between two types.
const float MINGLE_SEAM = 0.12;
// The warp field runs at this fraction of the blob frequency and pushes the
// blobs this many cells, so the patches swirl into each other.
const float MINGLE_WARP_SCALE = 0.5;
const float MINGLE_WARP_CELLS = 2.4;
// GameTime is the fraction of a 24000-tick day, so whole cycles per day keep
// the drift seamless when the day wraps: 40 = 600 ticks, 55 ~ 436, 70 ~ 343.
const float MINGLE_SWAY_CYCLES_PER_DAY = 40.0;
const float MINGLE_ROLL_CYCLES_PER_DAY = 55.0;
const float MINGLE_HEAVE_CYCLES_PER_DAY = 70.0;
// Cells each drift loop reaches from its centre.
const float MINGLE_DRIFT_CELLS = 0.6;
// Offsets each layer's field so every type blobs on its own.
const vec3 MINGLE_LAYER_SEED = vec3(37.1, 11.7, 53.3);
// Mean opacity at thresholds -MINGLE_SEAM + i/32 (1 + 2 MINGLE_SEAM),
// measured over three million uniform samples of mingleField, so the
// threshold whose mean opacity is a layer's share is read off by inversion.
const float MINGLE_COVERAGE[33] = float[33](
    1.0000, 1.0000, 1.0000, 1.0000, 1.0000, 0.9999, 0.9993, 0.9974, 0.9924,
    0.9814, 0.9605, 0.9257, 0.8735, 0.8024, 0.7134, 0.6104, 0.4997, 0.3890,
    0.2860, 0.1971, 0.1261, 0.0740, 0.0393, 0.0186, 0.0076, 0.0026, 0.0007,
    0.0001, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000);
const int MINGLE_COVERAGE_STEPS = 32;

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

// Three octaves over a warped domain, in [0, 1]; the coverage table is
// measured on exactly this.
float mingleField(vec3 p) {
    vec3 q = p * MINGLE_WARP_SCALE;
    vec3 warp = vec3(mingleValueNoise(q), mingleValueNoise(q + vec3(19.1, 0.0, 0.0)),
        mingleValueNoise(q + vec3(0.0, 0.0, 31.7))) - 0.5;
    vec3 w = p + MINGLE_WARP_CELLS * warp;
    return (mingleValueNoise(w) + 0.5 * mingleValueNoise(w * 2.03 + 17.0)
        + 0.25 * mingleValueNoise(w * 4.07 + 41.0)) / 1.75;
}

float mingleCoverageThreshold(int i) {
    return -MINGLE_SEAM + float(i) / float(MINGLE_COVERAGE_STEPS) * (1.0 + 2.0 * MINGLE_SEAM);
}

// The threshold whose mean opacity over the surface is share.
float mingleThreshold(float share) {
    for (int i = 0; i < MINGLE_COVERAGE_STEPS; i++) {
        if (MINGLE_COVERAGE[i + 1] <= share) {
            float span = MINGLE_COVERAGE[i] - MINGLE_COVERAGE[i + 1];
            float along = span > 0.0 ? (MINGLE_COVERAGE[i] - share) / span : 0.0;
            return mix(mingleCoverageThreshold(i), mingleCoverageThreshold(i + 1), along);
        }
    }
    return mingleCoverageThreshold(MINGLE_COVERAGE_STEPS);
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
// field around the threshold whose mean opacity over the surface is share.
float mingleOpacity(vec3 worldPos, float gameTime, float share, float layer) {
    if (layer < 0.5) {
        return 1.0;
    }
    if (share <= 0.0) {
        return 0.0;
    }
    vec3 p = worldPos * MINGLE_CELLS_PER_BLOCK + mingleDrift(gameTime, layer) + layer * MINGLE_LAYER_SEED;
    float threshold = mingleThreshold(share);
    return smoothstep(threshold - MINGLE_SEAM, threshold + MINGLE_SEAM, mingleField(p));
}
