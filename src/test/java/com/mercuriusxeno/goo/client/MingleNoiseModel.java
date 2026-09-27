package com.mercuriusxeno.goo.client;

/**
 * mingle_noise.glsl on the CPU, line for line, so a test reads the opacity
 * each layer of a mingled surface draws at (decision noise-mingled-type-textures).
 */
public final class MingleNoiseModel {

    private static final float TAU = 6.2831853f;
    private static final float CELLS_PER_BLOCK = 2.5f;
    private static final float SEAM = 0.12f;
    private static final float WARP_SCALE = 0.5f;
    private static final float WARP_CELLS = 2.4f;
    private static final float SWAY_CYCLES_PER_DAY = 40f;
    private static final float ROLL_CYCLES_PER_DAY = 55f;
    private static final float HEAVE_CYCLES_PER_DAY = 70f;
    private static final float DRIFT_CELLS = 0.6f;
    private static final float[] SEED_OFFSET = {37.1f, 11.7f, 53.3f};
    private static final float[] COVERAGE = {
        1.0000f, 1.0000f, 1.0000f, 1.0000f, 1.0000f, 0.9999f, 0.9993f, 0.9974f, 0.9924f,
        0.9814f, 0.9605f, 0.9257f, 0.8735f, 0.8024f, 0.7134f, 0.6104f, 0.4997f, 0.3890f,
        0.2860f, 0.1971f, 0.1261f, 0.0740f, 0.0393f, 0.0186f, 0.0076f, 0.0026f, 0.0007f,
        0.0001f, 0.0000f, 0.0000f, 0.0000f, 0.0000f, 0.0000f};
    private static final int COVERAGE_STEPS = 32;

    private MingleNoiseModel() {
    }

    private static float hash(int x, int y, int z) {
        int h = x * 0x8da6b343 ^ y * 0xd8163841 ^ z * 0xcb1ab31f;
        h = (h ^ (h >>> 15)) * 0x2c1b3c6d;
        h ^= h >>> 12;
        return (h & 0xFFFF) / 65536f;
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float valueNoise(float px, float py, float pz) {
        int cx = (int) Math.floor(px);
        int cy = (int) Math.floor(py);
        int cz = (int) Math.floor(pz);
        float fx = px - cx;
        float fy = py - cy;
        float fz = pz - cz;
        float sx = fx * fx * (3f - 2f * fx);
        float sy = fy * fy * (3f - 2f * fy);
        float sz = fz * fz * (3f - 2f * fz);
        float x00 = mix(hash(cx, cy, cz), hash(cx + 1, cy, cz), sx);
        float x10 = mix(hash(cx, cy + 1, cz), hash(cx + 1, cy + 1, cz), sx);
        float x01 = mix(hash(cx, cy, cz + 1), hash(cx + 1, cy, cz + 1), sx);
        float x11 = mix(hash(cx, cy + 1, cz + 1), hash(cx + 1, cy + 1, cz + 1), sx);
        return mix(mix(x00, x10, sy), mix(x01, x11, sy), sz);
    }

    private static float field(float px, float py, float pz) {
        float qx = px * WARP_SCALE;
        float qy = py * WARP_SCALE;
        float qz = pz * WARP_SCALE;
        float wx = px + WARP_CELLS * (valueNoise(qx, qy, qz) - 0.5f);
        float wy = py + WARP_CELLS * (valueNoise(qx + 19.1f, qy, qz) - 0.5f);
        float wz = pz + WARP_CELLS * (valueNoise(qx, qy, qz + 31.7f) - 0.5f);
        return (valueNoise(wx, wy, wz)
            + 0.5f * valueNoise(wx * 2.03f + 17f, wy * 2.03f + 17f, wz * 2.03f + 17f)
            + 0.25f * valueNoise(wx * 4.07f + 41f, wy * 4.07f + 41f, wz * 4.07f + 41f)) / 1.75f;
    }

    private static float coverageThreshold(int i) {
        return -SEAM + (float) i / COVERAGE_STEPS * (1f + 2f * SEAM);
    }

    private static float threshold(float share) {
        for (int i = 0; i < COVERAGE_STEPS; i++) {
            if (COVERAGE[i + 1] <= share) {
                float span = COVERAGE[i] - COVERAGE[i + 1];
                float along = span > 0f ? (COVERAGE[i] - share) / span : 0f;
                return mix(coverageThreshold(i), coverageThreshold(i + 1), along);
            }
        }
        return coverageThreshold(COVERAGE_STEPS);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * mingleOpacity: the opacity a layer draws at one world position.
     *
     * @param x        world x
     * @param y        world y
     * @param z        world z
     * @param gameTime the fraction of the day, as the GameTime uniform
     * @param share    the layer's conditional share, as the shader unpacks it
     * @param seed     the type's noise seed, as the shader unpacks it
     * @return the opacity in [0, 1]
     */
    public static float opacity(float x, float y, float z, float gameTime, float share, float seed) {
        if (share <= 0f) {
            return 0f;
        }
        float sway = gameTime * TAU * SWAY_CYCLES_PER_DAY + seed;
        float roll = gameTime * TAU * ROLL_CYCLES_PER_DAY + seed * 1.7f;
        float heave = gameTime * TAU * HEAVE_CYCLES_PER_DAY + seed * 2.3f;
        float px = x * CELLS_PER_BLOCK + DRIFT_CELLS * ((float) Math.cos(sway) + (float) Math.sin(roll))
            + seed * SEED_OFFSET[0];
        float py = y * CELLS_PER_BLOCK + DRIFT_CELLS * (float) Math.sin(heave) + seed * SEED_OFFSET[1];
        float pz = z * CELLS_PER_BLOCK + DRIFT_CELLS * ((float) Math.sin(sway) + (float) Math.cos(roll))
            + seed * SEED_OFFSET[2];
        float t = threshold(share);
        return smoothstep(t - SEAM, t + SEAM, field(px, py, pz));
    }
}
