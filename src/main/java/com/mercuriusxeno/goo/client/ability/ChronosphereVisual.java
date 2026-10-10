package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.HostVariables;
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
     * marker's synced slow_time step at the size its cast was dragged to, and
     * the ticks since this client first saw it stand.
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
        // chronosphere-hastes-players-slows-mobs: the veil grows to the radius its cast was dragged to
        double fullRadius = veil.get().radius().evaluate(HostVariables.sized(be.programState().castSize()));
        state.chronosphereRadius = (float) veil.get().radiusAt(fullRadius, now - stood + state.partialTick);
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
        emitVeil(quads, radius, 1f);
    }

    /**
     * Emits the veil for its shader at a strength, which the shader draws
     * every part of it at a share of.
     *
     * @param quads    the quad context
     * @param radius   the veil's radius
     * @param strength how strongly the veil draws, 0 to 1
     */
    static void emitVeil(FlatQuadContext quads, float radius, float strength) {
        int alpha = Math.round(Math.clamp(strength, 0f, 1f) * OPAQUE);
        for (int band = 0; band < BANDS; band++) {
            double lowPolar = Math.PI * band / BANDS;
            double highPolar = Math.PI * (band + 1) / BANDS;
            for (int segment = 0; segment < SEGMENTS; segment++) {
                double from = Math.TAU * segment / SEGMENTS;
                double to = Math.TAU * (segment + 1) / SEGMENTS;
                veilVertex(quads, radius, lowPolar, from, alpha);
                veilVertex(quads, radius, highPolar, from, alpha);
                veilVertex(quads, radius, highPolar, to, alpha);
                veilVertex(quads, radius, lowPolar, to, alpha);
            }
        }
    }

    private static void veilVertex(FlatQuadContext quads, float radius, double polar, double azimuth, int alpha) {
        float x = (float) (Math.sin(polar) * Math.cos(azimuth));
        float y = (float) Math.cos(polar);
        float z = (float) (Math.sin(polar) * Math.sin(azimuth));
        int color = ARGB.color(alpha, packed(x), packed(y), packed(z));
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
}
