package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SlowTimeStep;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ber.AbilityBlockRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Aeon's chronosphere: a translucent golden veil, a sphere centered on the
 * marker the blob stood where it landed, growing from the impact point to
 * the veil's radius as the slow_time step does. The veil draws through
 * {@code goo_chronosphere.fsh}: a glowing rim that reads from inside as well
 * as out, falling bands of light and clock-hour meridians.
 * chronosphere-hastes-players-slows-mobs
 */
public final class ChronosphereVisual {

    /** The veil's color: the stasis gold, faint enough to see the slowed mobs through. */
    static final int VEIL_COLOR = ARGB.color(46, 0xFF, 0xD4, 0x47);
    /** Bands from pole to pole, and segments around each band. */
    static final int BANDS = 24;
    static final int SEGMENTS = 48;
    private static final float CENTER = 0.5f;
    private static final int OPAQUE = 255;
    private static final float HALF_CHANNEL_SPAN = 0.5f;

    /** The game time each veil this client draws first stood, by marker. */
    private static final Map<BlockPos, Long> STOOD_AT = new HashMap<>();

    private ChronosphereVisual() {
    }

    /**
     * Copies the veil's radius this frame into the render state, from the
     * marker's synced slow_time step and the ticks since this client first
     * saw it stand.
     *
     * @param be    the ability block entity
     * @param state the render state
     */
    public static void extract(AbilityBlockEntity be, AbilityBlockRenderState state) {
        Optional<SlowTimeStep> veil = be.getBehavior() == null ? Optional.empty()
                : SyncedSteps.first(be, SlowTimeStep.class);
        if (veil.isEmpty() || be.getLevel() == null) {
            STOOD_AT.remove(be.getBlockPos());
            state.chronosphereRadius = 0f;
            return;
        }
        long now = be.getLevel().getGameTime();
        long stood = STOOD_AT.computeIfAbsent(be.getBlockPos().immutable(), pos -> now);
        state.chronosphereRadius = (float) veil.get().radiusAt(now - stood + state.partialTick);
    }

    /**
     * Draws the veil around the marker's center.
     *
     * @param state         the render state
     * @param poseStack     the pose at the marker's cell corner
     * @param nodeCollector the submit collector
     */
    public static void submit(AbilityBlockRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        float radius = state.chronosphereRadius;
        if (radius <= 0f) {
            return;
        }
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.CHRONOSPHERE_TYPE,
                (pose, consumer) -> emitVeil(new FlatQuadContext(pose, consumer), radius));
    }

    /**
     * Emits the veil for its shader: each vertex carries its normal, and its
     * direction from the center packed into its color.
     *
     * @param quads  the quad context
     * @param radius the veil's radius
     */
    static void emitVeil(FlatQuadContext quads, float radius) {
        for (int band = 0; band < BANDS; band++) {
            double lowPolar = Math.PI * band / BANDS;
            double highPolar = Math.PI * (band + 1) / BANDS;
            for (int segment = 0; segment < SEGMENTS; segment++) {
                double from = Math.TAU * segment / SEGMENTS;
                double to = Math.TAU * (segment + 1) / SEGMENTS;
                veilVertex(quads, radius, lowPolar, from);
                veilVertex(quads, radius, highPolar, from);
                veilVertex(quads, radius, highPolar, to);
                veilVertex(quads, radius, lowPolar, to);
            }
        }
    }

    private static void veilVertex(FlatQuadContext quads, float radius, double polar, double azimuth) {
        float x = (float) (Math.sin(polar) * Math.cos(azimuth));
        float y = (float) Math.cos(polar);
        float z = (float) (Math.sin(polar) * Math.sin(azimuth));
        int color = ARGB.color(OPAQUE, packed(x), packed(y), packed(z));
        quads.vertex(CENTER + radius * x, CENTER + radius * y, CENTER + radius * z, color, x, y, z);
    }

    /**
     * A direction component of [-1, 1] as a color channel of [0, 255].
     *
     * @param component the component
     * @return the channel
     */
    static int packed(float component) {
        return Math.round((component * HALF_CHANNEL_SPAN + HALF_CHANNEL_SPAN) * OPAQUE);
    }

    /**
     * Emits the veil as bands of quads from pole to pole, about the cell's center.
     *
     * @param quads  the quad context
     * @param radius the veil's radius
     * @param color  the veil's color, ARGB
     */
    static void emitSphere(FlatQuadContext quads, float radius, int color) {
        for (int band = 0; band < BANDS; band++) {
            double lowPolar = Math.PI * band / BANDS;
            double highPolar = Math.PI * (band + 1) / BANDS;
            for (int segment = 0; segment < SEGMENTS; segment++) {
                double from = Math.TAU * segment / SEGMENTS;
                double to = Math.TAU * (segment + 1) / SEGMENTS;
                vertex(quads, radius, lowPolar, from, color);
                vertex(quads, radius, highPolar, from, color);
                vertex(quads, radius, highPolar, to, color);
                vertex(quads, radius, lowPolar, to, color);
            }
        }
    }

    private static void vertex(FlatQuadContext quads, float radius, double polar, double azimuth, int color) {
        float x = (float) (radius * Math.sin(polar) * Math.cos(azimuth));
        float y = (float) (radius * Math.cos(polar));
        float z = (float) (radius * Math.sin(polar) * Math.sin(azimuth));
        quads.vertex(CENTER + x, CENTER + y, CENTER + z, color);
    }
}
