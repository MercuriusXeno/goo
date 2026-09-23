package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.program.PhasedState;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;

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
