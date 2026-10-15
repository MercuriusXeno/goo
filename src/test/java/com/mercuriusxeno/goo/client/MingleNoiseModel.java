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
    /** MINGLE_WORLD_TEXELS: Minecraft's texel, a sixteenth of a block. */
    public static final float WORLD_TEXELS = 16f;

    private MingleNoiseModel() {
    }

    /**
     * mingleTexelSnap on one axis: the middle of the texel holding the coordinate
     * (decision mingle-noise-snaps-to-texels).
     *
     * @param coordinate    the position on one axis
     * @param texelsPerUnit texels per unit of the coordinate
     * @return the coordinate of its texel's middle
     */
    public static float texelSnap(float coordinate, float texelsPerUnit) {
        return ((float) Math.floor(coordinate * texelsPerUnit) + 0.5f) / texelsPerUnit;
    }

    /** MINGLE_SKIN_TEXELS: a mob skin's texels across its [0, 1] coordinates. */
    public static final float SKIN_TEXELS = 64f;

    /**
     * mingleField at a point, as the shader reads it.
     *
     * @param px x in field cells
     * @param py y in field cells
     * @param pz z in field cells
     * @return the field value in [0, 1]
     */
    public static float fieldAt(float px, float py, float pz) {
        return field(px, py, pz);
    }

    /**
     * mingleCornerWeight on one axis: the fraction blended from a straight slant
     * at ease 0 to a smooth curve at ease 1 (decision cut-edge-blends-texel-corners).
     *
     * @param fraction the point's fraction inside its texel
     * @param ease     MINGLE_CORNER_EASE
     * @return the weight of the far corner
     */
    public static float cornerWeight(float fraction, float ease) {
        return mix(fraction, fraction * fraction * (3f - 2f * fraction), ease);
    }

    /**
     * mingleCornerField: the field read at the four corners of the texel holding a
     * point on a 2D grid, blended at the point's fraction inside it.
     *
     * @param px            the point's x in its own units
     * @param py            the point's y in its own units
     * @param texelsPerUnit the grid's texels per unit
     * @param cellsPerUnit  field cells per unit
     * @param offset        the field offset, {x, y, z}
     * @param ease          MINGLE_CORNER_EASE
     * @return the blended field value
     */
    public static float cornerField(float px, float py, float texelsPerUnit, float cellsPerUnit, float[] offset,
                                    float ease) {
        float gx = px * texelsPerUnit;
        float gy = py * texelsPerUnit;
        float bx = (float) Math.floor(gx);
        float by = (float) Math.floor(gy);
        float wx = cornerWeight(gx - bx, ease);
        float wy = cornerWeight(gy - by, ease);
        float c00 = gridCorner(bx, by, texelsPerUnit, cellsPerUnit, offset);
        float c10 = gridCorner(bx + 1f, by, texelsPerUnit, cellsPerUnit, offset);
        float c01 = gridCorner(bx, by + 1f, texelsPerUnit, cellsPerUnit, offset);
        float c11 = gridCorner(bx + 1f, by + 1f, texelsPerUnit, cellsPerUnit, offset);
        return mix(mix(c00, c10, wx), mix(c01, c11, wx), wy);
    }

    private static float gridCorner(float cx, float cy, float texelsPerUnit, float cellsPerUnit, float[] offset) {
        return field(cx / texelsPerUnit * cellsPerUnit + offset[0], cy / texelsPerUnit * cellsPerUnit + offset[1],
            offset[2]);
    }

    /**
     * mingleWorldCornerField, past the face step the shader takes from screen
     * derivatives: the field read at the eight corners of the world texel holding
     * a point, blended at its fraction inside it.
     *
     * @param x             world x
     * @param y             world y
     * @param z             world z
     * @param cellsPerBlock field cells per block
     * @param offset        the field offset, {x, y, z}
     * @param ease          MINGLE_CORNER_EASE
     * @return the blended field value
     */
    public static float worldCornerField(float x, float y, float z, float cellsPerBlock, float[] offset, float ease) {
        float[] grid = {x * WORLD_TEXELS, y * WORLD_TEXELS, z * WORLD_TEXELS};
        float[] base = new float[3];
        float[] weight = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            base[axis] = (float) Math.floor(grid[axis]);
            weight[axis] = cornerWeight(grid[axis] - base[axis], ease);
        }
        float[] alongX = new float[4];
        for (int corner = 0; corner < 4; corner++) {
            float cy = base[1] + (corner & 1);
            float cz = base[2] + (corner >> 1);
            alongX[corner] = mix(worldCorner(base[0], cy, cz, cellsPerBlock, offset),
                worldCorner(base[0] + 1f, cy, cz, cellsPerBlock, offset), weight[0]);
        }
        return mix(mix(alongX[0], alongX[1], weight[1]), mix(alongX[2], alongX[3], weight[1]), weight[2]);
    }

    private static float worldCorner(float cx, float cy, float cz, float cellsPerBlock, float[] offset) {
        return field(cx / WORLD_TEXELS * cellsPerBlock + offset[0], cy / WORLD_TEXELS * cellsPerBlock + offset[1],
            cz / WORLD_TEXELS * cellsPerBlock + offset[2]);
    }

    /**
     * The field crucible_dissolve.fsh's glow type pick reads: mingleField at the item sprite's
     * pixel holding the atlas coordinate, scaled to the given cells per pixel.
     *
     * @param u             atlas u
     * @param v             atlas v
     * @param atlasWidth    the atlas's width in pixels
     * @param atlasHeight   the atlas's height in pixels
     * @param cellsPerPixel noise cells per sprite pixel
     * @return the field value in [0, 1]
     */
    public static float itemPixelField(float u, float v, float atlasWidth, float atlasHeight, float cellsPerPixel) {
        return field(texelSnap(u, atlasWidth) * atlasWidth * cellsPerPixel,
            texelSnap(v, atlasHeight) * atlasHeight * cellsPerPixel, 0f);
    }

    /**
     * The field a world-space mingle shader thresholds: mingleField at the world
     * position snapped to its texel and scaled to the given cells per block.
     *
     * @param x             world x
     * @param y             world y
     * @param z             world z
     * @param cellsPerBlock noise cells per block
     * @return the field value in [0, 1]
     */
    public static float texelField(float x, float y, float z, float cellsPerBlock) {
        return field(texelSnap(x, WORLD_TEXELS) * cellsPerBlock, texelSnap(y, WORLD_TEXELS) * cellsPerBlock,
            texelSnap(z, WORLD_TEXELS) * cellsPerBlock);
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
     * mingleOpacity at the world texel holding a position, as goo_fluid_surface.fsh
     * samples it (decision mingle-noise-snaps-to-texels): the opacity a layer draws at.
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
        float px = texelSnap(x, WORLD_TEXELS) * CELLS_PER_BLOCK
            + DRIFT_CELLS * ((float) Math.cos(sway) + (float) Math.sin(roll)) + seed * SEED_OFFSET[0];
        float py = texelSnap(y, WORLD_TEXELS) * CELLS_PER_BLOCK + DRIFT_CELLS * (float) Math.sin(heave)
            + seed * SEED_OFFSET[1];
        float pz = texelSnap(z, WORLD_TEXELS) * CELLS_PER_BLOCK
            + DRIFT_CELLS * ((float) Math.sin(sway) + (float) Math.cos(roll)) + seed * SEED_OFFSET[2];
        float t = threshold(share);
        return smoothstep(t - SEAM, t + SEAM, field(px, py, pz));
    }

    /**
     * The one-pass blend goo_fluid_surface.fsh draws at a position (decision
     * one-pass-blends-every-type): each layer over the ones before at its
     * opacity, every sprite opaque, so a layer shows its opacity times one
     * minus the opacity of every later layer.
     *
     * @param x        world x
     * @param y        world y
     * @param z        world z
     * @param gameTime the fraction of the day, as the GameTime uniform
     * @param shares   each layer's conditional share, the first drawn first
     * @param seeds    each layer's noise seed
     * @return how much each layer shows, summing to the composite's alpha
     */
    public static float[] blend(float x, float y, float z, float gameTime, float[] shares, float[] seeds) {
        float[] shown = new float[shares.length];
        for (int layer = 0; layer < shares.length; layer++) {
            float over = opacity(x, y, z, gameTime, shares[layer], seeds[layer]);
            for (int below = 0; below < layer; below++) {
                shown[below] *= 1f - over;
            }
            shown[layer] = over;
        }
        return shown;
    }
}
