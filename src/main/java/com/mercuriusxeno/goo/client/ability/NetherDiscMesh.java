package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;

/**
 * The nether black hole's flat accretion disc, one mesh every hole style
 * submits through {@code NETHER_DISK_TYPE} (decision one-disc-mesh-config-lens).
 * The disc is an annulus of quads in the world XZ plane; each vertex packs
 * R = radialT (0 inner, 1 outer), G = angularT (0..1 around the ring) and
 * B = the animation phase, the contract {@code nether_disk.vsh} reads.
 */
public final class NetherDiscMesh {

    /** Offset to get block center from integer position. */
    private static final float BLOCK_CENTER = 0.5f;
    /** Disc outer edge at full expansion as a multiple of the full blast radius. */
    private static final float DISK_OUTER_FULL_MULT = 2.8f;
    /** Minimum ring width past the inner edge, as a multiple of the hole's visible radius. */
    private static final float DISK_MIN_RING_WIDTH = 0.25f;
    /** Angular segments around the annulus. */
    private static final int DISK_ANGULAR_SEGMENTS = 64;
    /** Floats per entry in {@link #DISK_ANGULAR_SAMPLES}: cos, sin, angularT. */
    private static final int DISK_SAMPLE_STRIDE = 3;
    private static final int DISK_SAMPLE_COS_OFFSET = 0;
    private static final int DISK_SAMPLE_SIN_OFFSET = 1;
    private static final int DISK_SAMPLE_ANG_OFFSET = 2;
    /** Radial T packed into Color.r for inner-edge vertices. */
    private static final float RADIAL_T_INNER = 0f;
    /** Radial T packed into Color.r for outer-edge vertices. */
    private static final float RADIAL_T_OUTER = 1f;
    /** Opaque alpha for vertex color packing. */
    private static final int OPAQUE_ALPHA = 0xFF;
    /** Maximum byte value for a 0..1 to byte mapping. */
    private static final int PROGRESS_BYTE_MAX = 255;
    /** Cycle length in ticks for the swirl animation phase. */
    private static final int ANIMATION_CYCLE_TICKS = 64;
    /** Full circle in radians. */
    private static final double TWO_PI = 2.0 * Math.PI;

    /**
     * Angular samples as stride-3 triples {@code (cos, sin, angularT)}. The
     * trailing sample wraps back to angle 0 with angularT = 1, so the seam
     * quad carries a continuous angular UV.
     */
    private static final float[] DISK_ANGULAR_SAMPLES = buildDiskAngularSamples();

    private NetherDiscMesh() {
    }

    /**
     * Answers the disc's outer edge: it sweeps out with the expansion scale
     * but never closes to less than a minimum ring width past the inner edge.
     *
     * @param innerRadius    the disc's inner edge in world blocks
     * @param visibleRadius  the hole's current visible radius in world blocks
     * @param fullRadius     the hole's full blast radius in world blocks
     * @param expansionScale the disc's expansion in [0, 1]
     * @return the outer edge radius in world blocks
     */
    public static float outerRadius(float innerRadius, float visibleRadius,
                                    float fullRadius, float expansionScale) {
        return Math.max(
                innerRadius + visibleRadius * DISK_MIN_RING_WIDTH,
                fullRadius * DISK_OUTER_FULL_MULT * expansionScale);
    }

    /**
     * Emits the disc annulus as a ring of quads, winding inner0, inner1,
     * outer1, outer0 so the top face points +Y.
     *
     * @param pose      the current pose entry
     * @param c         the vertex consumer
     * @param innerR    disc inner edge radius in world blocks
     * @param outerR    disc outer edge radius in world blocks
     * @param animPhase global animation phase in [0, 1]
     */
    public static void emitDisc(PoseStack.Pose pose, VertexConsumer c,
                                float innerR, float outerR, float animPhase) {
        int animByte = toByte(animPhase);
        for (int i = 0; i < DISK_ANGULAR_SEGMENTS; i++) {
            int i0 = i * DISK_SAMPLE_STRIDE;
            int i1 = (i + 1) * DISK_SAMPLE_STRIDE;
            emitDiscVertex(pose, c, i0, innerR, RADIAL_T_INNER, animByte);
            emitDiscVertex(pose, c, i1, innerR, RADIAL_T_INNER, animByte);
            emitDiscVertex(pose, c, i1, outerR, RADIAL_T_OUTER, animByte);
            emitDiscVertex(pose, c, i0, outerR, RADIAL_T_OUTER, animByte);
        }
    }

    /**
     * Writes one disc vertex at the angular sample starting at {@code sample}.
     *
     * @param pose     current pose entry
     * @param c        vertex consumer
     * @param sample   index of the sample's first float in {@link #DISK_ANGULAR_SAMPLES}
     * @param radius   world-space radius for this vertex (inner or outer)
     * @param radialT  radial coordinate (0 inner, 1 outer)
     * @param animByte pre-computed animation phase byte
     */
    private static void emitDiscVertex(PoseStack.Pose pose, VertexConsumer c,
                                       int sample, float radius, float radialT, int animByte) {
        float cosT = DISK_ANGULAR_SAMPLES[sample + DISK_SAMPLE_COS_OFFSET];
        float sinT = DISK_ANGULAR_SAMPLES[sample + DISK_SAMPLE_SIN_OFFSET];
        float angularT = DISK_ANGULAR_SAMPLES[sample + DISK_SAMPLE_ANG_OFFSET];
        new FlatQuadContext(pose, c).vertex(
                BLOCK_CENTER + cosT * radius,
                BLOCK_CENTER,
                BLOCK_CENTER + sinT * radius,
                packDiskColor(toByte(radialT), toByte(angularT), animByte),
                0f, 1f, 0f);
    }

    /**
     * Packs one disc vertex color: R = radialT, G = angularT, B = animation phase.
     *
     * @param radialByte  radialT already encoded to a byte
     * @param angularByte angularT already encoded to a byte
     * @param animByte    animation phase already encoded to a byte
     * @return the packed ARGB color
     */
    public static int packDiskColor(int radialByte, int angularByte, int animByte) {
        return ARGB.color(OPAQUE_ALPHA, radialByte, angularByte, animByte);
    }

    /**
     * Derives a deterministic [0, 1) animation phase from the marker's level
     * game time, so every style's swirl runs in the same phase.
     *
     * @param be the chain marker block entity
     * @return the animation phase for the shaders
     */
    public static float animationTime(ChainMarkerBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) {
            return 0f;
        }
        long tick = level.getGameTime() % ANIMATION_CYCLE_TICKS;
        return (float) tick / ANIMATION_CYCLE_TICKS;
    }

    /**
     * Maps a 0..1 value, clamped, to a color byte.
     *
     * @param unit the value to encode
     * @return the byte in [0, 255]
     */
    public static int toByte(float unit) {
        return Math.round(Math.min(1f, Math.max(0f, unit)) * PROGRESS_BYTE_MAX);
    }

    /**
     * Builds the stride-3 angular sample table.
     *
     * @return the angular sample table
     */
    private static float[] buildDiskAngularSamples() {
        int sampleCount = DISK_ANGULAR_SEGMENTS + 1;
        float[] out = new float[sampleCount * DISK_SAMPLE_STRIDE];
        for (int i = 0; i < sampleCount; i++) {
            double theta = TWO_PI * i / DISK_ANGULAR_SEGMENTS;
            int base = i * DISK_SAMPLE_STRIDE;
            out[base + DISK_SAMPLE_COS_OFFSET] = (float) Math.cos(theta);
            out[base + DISK_SAMPLE_SIN_OFFSET] = (float) Math.sin(theta);
            out[base + DISK_SAMPLE_ANG_OFFSET] = (float) i / DISK_ANGULAR_SEGMENTS;
        }
        return out;
    }
}
