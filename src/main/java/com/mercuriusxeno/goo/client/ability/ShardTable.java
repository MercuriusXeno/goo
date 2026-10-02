package com.mercuriusxeno.goo.client.ability;

import org.joml.Vector3f;
import java.util.Random;

/**
 * One crystal cloud's shard layout, drawn from a seed: where each shard sits
 * in the unit sphere, how it lies, its shape and size, and how it spins
 * (decision cloud-seed-per-client-ephemeral).
 */
final class ShardTable {

    /**
     * Total slivers at full charge.
     */
    static final int MAX_SLIVERS = 256;
    /**
     * Half-length of each sliver along its long axis before size variation,
     * so every sliver reaches 0.08 to 0.24 blocks from its center.
     * Decision razor-shards-are-bigger.
     */
    private static final float SLIVER_HALF_LENGTH = 0.16f;

    /**
     * Probability that a sliver has zero spin.
     */
    private static final float NO_SPIN_CHANCE = 0.3f;
    /**
     * Maximum spin speed in radians per tick for spinning shards.
     */
    private static final float MAX_SPIN_SPEED = 0.06f;
    /**
     * Minimum spin speed when spinning.
     */
    private static final float MIN_SPIN_SPEED = 0.008f;

    /**
     * Per-sliver data stride. Layout:
     * [cx, cy, cz, axisX, axisY, axisZ, perpX, perpY, perpZ, halfLen,
     * spinAxisX, spinAxisY, spinAxisZ, spinSpeed, shape, widthRatio, depthRatio]
     */
    private static final int SLIVER_STRIDE = 17;
    private static final int OFF_CY = 1;
    private static final int OFF_CZ = 2;
    private static final int OFF_AX = 3;
    private static final int OFF_AY = 4;
    private static final int OFF_AZ = 5;
    private static final int OFF_PX = 6;
    private static final int OFF_PY = 7;
    private static final int OFF_PZ = 8;
    private static final int OFF_HALF_LEN = 9;
    private static final int OFF_SPIN_AX = 10;
    private static final int OFF_SPIN_AY = 11;
    private static final int OFF_SPIN_AZ = 12;
    private static final int OFF_SPIN_SPEED = 13;
    private static final int OFF_SHAPE = 14;
    private static final int OFF_WIDTH_RATIO = 15;
    private static final int OFF_DEPTH = 16;

    /**
     * Shape codes: 0 = single spike (triangle), 1 = diamond, 2 = asymmetric diamond.
     */
    static final float SHAPE_SINGLE_SPIKE = 0f;
    static final float SHAPE_DIAMOND = 1f;
    static final float SHAPE_ASYMMETRIC = 2f;
    /**
     * Chance of single spike vs diamond shapes.
     */
    private static final float SINGLE_SPIKE_CHANCE = 0.2f;
    /**
     * Chance of symmetric diamond (of the non-spike remainder).
     */
    private static final float SYMMETRIC_CHANCE = 0.15f;
    private static final float MIN_WIDTH_RATIO = 0.02f;
    private static final float MAX_WIDTH_RATIO = 0.10f;
    private static final float MIN_DEPTH_RATIO = 0.08f;
    private static final float MAX_DEPTH_RATIO = 0.25f;
    /**
     * Maps [0,1) random floats into [-1, 1).
     */
    private static final float RNG_RANGE = 2f;
    private static final float MIN_LEN_SCALE = 0.5f;
    private static final float LEN_SCALE_RANGE = 1.0f;
    /**
     * Threshold for near-parallel detection in perpendicular vector construction.
     */
    private static final float PARALLEL_THRESHOLD = 0.9f;
    /**
     * Minimum vector length to avoid normalizing near-zero vectors.
     */
    private static final float NORMALIZE_EPSILON = 0.001f;

    private final float[] data;

    private ShardTable(float[] data) {
        this.data = data;
    }

    /**
     * Draws a cloud's shard layout from a seed; one seed always draws one layout.
     *
     * @param seed the seed the layout is drawn from
     * @return the layout
     */
    static ShardTable fromSeed(long seed) {
        Random rng = new Random(seed);
        float[] data = new float[MAX_SLIVERS * SLIVER_STRIDE];
        for (int i = 0; i < MAX_SLIVERS; i++) {
            buildOneSliver(rng, data, i * SLIVER_STRIDE);
        }
        return new ShardTable(data);
    }

    /**
     * The shard's center within the unit sphere, before the cloud radius scales it.
     *
     * @param shard the shard index
     * @return the center
     */
    Vector3f center(int shard) {
        return vectorAt(shard * SLIVER_STRIDE);
    }

    /**
     * The shard's unit long axis at rest.
     *
     * @param shard the shard index
     * @return the long axis
     */
    Vector3f axis(int shard) {
        return vectorAt(shard * SLIVER_STRIDE + OFF_AX);
    }

    /**
     * The shard's unit perpendicular arm at rest.
     *
     * @param shard the shard index
     * @return the perpendicular arm
     */
    Vector3f perp(int shard) {
        return vectorAt(shard * SLIVER_STRIDE + OFF_PX);
    }

    /**
     * The unit axis the shard spins about; unset on a still shard.
     *
     * @param shard the shard index
     * @return the spin axis
     */
    Vector3f spinAxis(int shard) {
        return vectorAt(shard * SLIVER_STRIDE + OFF_SPIN_AX);
    }

    /**
     * The shard's spin in radians per tick, zero on a still shard.
     *
     * @param shard the shard index
     * @return the spin speed
     */
    float spinSpeed(int shard) {
        return data[shard * SLIVER_STRIDE + OFF_SPIN_SPEED];
    }

    /**
     * The shard's reach along its long axis.
     *
     * @param shard the shard index
     * @return the half-length
     */
    float halfLength(int shard) {
        return data[shard * SLIVER_STRIDE + OFF_HALF_LEN];
    }

    /**
     * The shard's perpendicular reach as a fraction of its half-length.
     *
     * @param shard the shard index
     * @return the width ratio
     */
    float widthRatio(int shard) {
        return data[shard * SLIVER_STRIDE + OFF_WIDTH_RATIO];
    }

    /**
     * The shard's pyramid depth as a fraction of its half-length.
     *
     * @param shard the shard index
     * @return the depth ratio
     */
    float depthRatio(int shard) {
        return data[shard * SLIVER_STRIDE + OFF_DEPTH];
    }

    /**
     * The shard's shape code: single spike, diamond or asymmetric diamond.
     *
     * @param shard the shard index
     * @return the shape code
     */
    float shape(int shard) {
        return data[shard * SLIVER_STRIDE + OFF_SHAPE];
    }

    private Vector3f vectorAt(int off) {
        return new Vector3f(data[off], data[off + OFF_CY], data[off + OFF_CZ]);
    }

    private static void buildOneSliver(Random rng, float[] data, int off) {
        Vector3f pos = randomUnitSpherePoint(rng);
        data[off] = pos.x();
        data[off + OFF_CY] = pos.y();
        data[off + OFF_CZ] = pos.z();
        Vector3f axis = randomUnitVector(rng);
        data[off + OFF_AX] = axis.x();
        data[off + OFF_AY] = axis.y();
        data[off + OFF_AZ] = axis.z();
        Vector3f perp = buildPerp(axis);
        data[off + OFF_PX] = perp.x();
        data[off + OFF_PY] = perp.y();
        data[off + OFF_PZ] = perp.z();
        data[off + OFF_HALF_LEN] = SLIVER_HALF_LENGTH * (MIN_LEN_SCALE + rng.nextFloat() * LEN_SCALE_RANGE);
        buildShapeData(rng, data, off);
        buildSpinData(rng, data, off);
    }

    private static void buildShapeData(Random rng, float[] data, int off) {
        float roll = rng.nextFloat();
        if (roll < SINGLE_SPIKE_CHANCE) {
            data[off + OFF_SHAPE] = SHAPE_SINGLE_SPIKE;
        } else if (roll < SINGLE_SPIKE_CHANCE + SYMMETRIC_CHANCE) {
            data[off + OFF_SHAPE] = SHAPE_DIAMOND;
        } else {
            data[off + OFF_SHAPE] = SHAPE_ASYMMETRIC;
        }
        data[off + OFF_WIDTH_RATIO] = MIN_WIDTH_RATIO + rng.nextFloat() * (MAX_WIDTH_RATIO - MIN_WIDTH_RATIO);
        data[off + OFF_DEPTH] = MIN_DEPTH_RATIO + rng.nextFloat() * (MAX_DEPTH_RATIO - MIN_DEPTH_RATIO);
    }

    private static void buildSpinData(Random rng, float[] data, int off) {
        if (rng.nextFloat() < NO_SPIN_CHANCE) {
            data[off + OFF_SPIN_SPEED] = 0f;
            return;
        }
        Vector3f spinAxis = randomUnitVector(rng);
        data[off + OFF_SPIN_AX] = spinAxis.x();
        data[off + OFF_SPIN_AY] = spinAxis.y();
        data[off + OFF_SPIN_AZ] = spinAxis.z();
        data[off + OFF_SPIN_SPEED] = MIN_SPIN_SPEED + rng.nextFloat() * (MAX_SPIN_SPEED - MIN_SPIN_SPEED);
    }

    private static Vector3f randomUnitSpherePoint(Random rng) {
        float px;
        float py;
        float pz;
        do {
            px = rng.nextFloat() * RNG_RANGE - 1f;
            py = rng.nextFloat() * RNG_RANGE - 1f;
            pz = rng.nextFloat() * RNG_RANGE - 1f;
        } while (px * px + py * py + pz * pz > 1f);
        return new Vector3f(px, py, pz);
    }

    private static Vector3f randomUnitVector(Random rng) {
        float x = rng.nextFloat() * RNG_RANGE - 1f;
        float y = rng.nextFloat() * RNG_RANGE - 1f;
        float z = rng.nextFloat() * RNG_RANGE - 1f;
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len < NORMALIZE_EPSILON) {
            return new Vector3f(0f, 1f, 0f);
        }
        return new Vector3f(x / len, y / len, z / len);
    }

    private static Vector3f buildPerp(Vector3f axis) {
        float ax = axis.x();
        float ay = axis.y();
        float az = axis.z();
        float sx = Math.abs(ay) < PARALLEL_THRESHOLD ? 0f : 1f;
        float sy = Math.abs(ay) < PARALLEL_THRESHOLD ? 1f : 0f;
        float px = -az * sy;
        float py = az * sx;
        float pz = ax * sy - ay * sx;
        float len = (float) Math.sqrt(px * px + py * py + pz * pz);
        return new Vector3f(px / len, py / len, pz / len);
    }
}
