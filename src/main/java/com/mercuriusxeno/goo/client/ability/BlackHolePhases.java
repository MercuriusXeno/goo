package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.program.MarkerVariables;
import com.mercuriusxeno.goo.ability.program.PhasedState;
import com.mercuriusxeno.goo.ability.program.PhasedStep;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.ber.ChainMarkerRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * Reads the nether black hole's size off the phase cursor its program
 * keeps on the marker: the sphere eases out to full size through
 * {@code expand}, holds through {@code hold} and eases back to nothing
 * through {@code contract}, while the accretion disk sweeps out on a
 * curve of its own. The phase names are the ones
 * {@code nether_black_hole.json} declares.
 */
public final class BlackHolePhases {

    private static final String EXPAND = "expand";
    private static final String HOLD = "hold";
    private static final String CONTRACT = "contract";
    /**
     * The disk's expansion at the expand to hold transition.
     */
    private static final float DISK_EXPAND_PEAK = 0.25f;
    /**
     * Minimum visible radius so the hole never collapses to a single pixel.
     */
    private static final float HOLE_MIN_RADIUS = 0.25f;
    /**
     * World-space margin added to the implosion radius so the hole's body
     * covers the blast zone.
     */
    private static final float OCCLUSION_MARGIN = 0.75f;

    private BlackHolePhases() {
    }

    /**
     * Answers whether the marker is a nether marker running a phased program.
     *
     * @param be the chain marker block entity
     * @return true while the black hole should draw
     */
    public static boolean isRunning(ChainMarkerBlockEntity be) {
        return GooTypes.NETHER.equals(be.getGooType()) && be.getPhased().isRunning();
    }

    /**
     * Returns the visible scale of the sphere.
     *
     * @param phase the phase cursor
     * @return the scale in [0, 1]
     */
    public static float visibleScale(PhasedState phase) {
        return switch (phase.name()) {
            case EXPAND -> easeOutCubic(phase.progress());
            case HOLD -> 1f;
            case CONTRACT -> easeOutCubic(1f - phase.progress());
            default -> 0f;
        };
    }

    /**
     * Returns the disk's expansion, which runs ahead of the sphere through
     * hold so the disk sweeps outward on its own.
     *
     * @param phase the phase cursor
     * @return the expansion in [0, 1]
     */
    public static float diskExpansionScale(PhasedState phase) {
        return switch (phase.name()) {
            case EXPAND -> DISK_EXPAND_PEAK * phase.progress();
            case HOLD -> DISK_EXPAND_PEAK + (1f - DISK_EXPAND_PEAK) * phase.progress();
            case CONTRACT -> 1f - phase.progress();
            default -> 0f;
        };
    }

    /**
     * Fills the render state's nether fields from the marker's phase cursor,
     * the one extraction every hole style shares (decision
     * one-disc-mesh-config-lens), or clears {@code netherActive} when no
     * black hole runs.
     *
     * @param be    the chain marker block entity
     * @param state the render state to populate
     * @return true when a black hole with a visible body should mark the lens
     */
    public static boolean populateRenderState(ChainMarkerBlockEntity be, ChainMarkerRenderState state) {
        if (!isRunning(be)) {
            state.netherActive = false;
            return false;
        }
        PhasedState phase = be.getPhased();
        state.netherActive = true;
        state.visibleScale = visibleScale(phase);
        state.diskExpansionScale = diskExpansionScale(phase);
        state.implodeRadius = SyncedSteps.first(be, PhasedStep.class)
                .map(step -> step.radius().evaluateFloat(new MarkerVariables(be))).orElse(0f);
        state.animationTime = NetherDiscMesh.animationTime(be);
        return state.visibleScale > 0f;
    }

    /**
     * Answers the hole's full radius: the implosion radius plus the margin
     * that makes the body cover the whole blast zone.
     *
     * @param state the populated render state
     * @return the full radius in world blocks
     */
    public static float fullRadius(ChainMarkerRenderState state) {
        return state.implodeRadius + OCCLUSION_MARGIN;
    }

    /**
     * Answers the hole body's current radius (a sphere's radius, a cube's
     * half-extent), never collapsing below a minimum.
     *
     * @param state the populated render state
     * @return the visible radius in world blocks
     */
    public static float visibleRadius(ChainMarkerRenderState state) {
        return Math.max(HOLE_MIN_RADIUS, fullRadius(state) * state.visibleScale);
    }

    /**
     * Answers the world-space center of the marker's block.
     *
     * @param be the chain marker block entity
     * @return the block center
     */
    public static Vec3 holeCenter(ChainMarkerBlockEntity be) {
        return Vec3.atCenterOf(be.getBlockPos());
    }

    /**
     * Cubic ease-out: {@code 1 - (1 - t)^3}.
     *
     * @param t the linear progress
     * @return the eased progress
     */
    private static float easeOutCubic(float t) {
        float inverse = 1f - t;
        return 1f - inverse * inverse * inverse;
    }
}
