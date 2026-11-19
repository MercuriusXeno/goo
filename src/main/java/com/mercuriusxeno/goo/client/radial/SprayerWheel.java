package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.SprayerFlow;
import com.mercuriusxeno.goo.item.SprayerNozzle;
import com.mercuriusxeno.goo.item.SprayerSetting;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The sprayer wheel's model: one ring of three arcs shown together, a petal for each goo type of the fitted
 * canister that has a residual, most held first, then the flow steps from low to high, then the nozzles. The goo
 * arc is centered at the top and each arc is set off from the next by a gap. Letting go over a petal sets that one
 * setting and keeps the other two.
 * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
 * decision spray-item-sprays-more-or-less-per-tick
 * decision spray-item-takes-different-nozzles-doing-different-things
 * operator ruling 2026-11-18: layout A, one ring of three arcs
 *
 * @param types   the sprayable goo types, most held first
 * @param flows   the flow steps, low to high
 * @param nozzles the nozzles
 */
public record SprayerWheel(List<ResourceKey<GooTypeDefinition>> types, List<SprayerFlow> flows,
                           List<SprayerNozzle> nozzles) {

    /** No petal: the cursor rests in the hub or past the ring. */
    public static final int NONE = -1;
    /** The gap setting each arc off from the next, radians. */
    private static final double GROUP_GAP = Math.toRadians(8);
    private static final double FULL_TURN = Math.PI * 2;
    private static final int GROUPS = 3;
    /** The gaps before the nozzle arc: after the goo arc and after the flow arc. */
    private static final int NOZZLE_GAPS_BEFORE = 2;
    private static final double HALF = 2;

    /** Which setting a petal sets. */
    public enum Group { GOO, FLOW, NOZZLE }

    /**
     * One petal of the ring.
     *
     * @param group which setting it sets
     * @param index its place in that group's list
     */
    public record RingPetal(Group group, int index) {
    }

    /**
     * The wheel over a canister's content.
     *
     * @param content     the fitted canister's content
     * @param hasResidual whether a goo type has a residual
     * @return the wheel's model
     */
    public static SprayerWheel of(CanisterFluidContent content, Predicate<ResourceKey<GooTypeDefinition>> hasResidual) {
        return new SprayerWheel(content.gooVolumesDominantFirst().keySet().stream().filter(hasResidual).toList(),
                Arrays.stream(SprayerFlow.values()).sorted(Comparator.comparingDouble(SprayerFlow::rate)).toList(),
                List.of(SprayerNozzle.values()));
    }

    /**
     * @return the ring's petals clockwise from the goo arc: goo types, then flow steps, then nozzles
     */
    public List<RingPetal> ring() {
        List<RingPetal> ring = new ArrayList<>();
        for (int type = 0; type < types.size(); type++) {
            ring.add(new RingPetal(Group.GOO, type));
        }
        for (int flow = 0; flow < flows.size(); flow++) {
            ring.add(new RingPetal(Group.FLOW, flow));
        }
        for (int nozzle = 0; nozzle < nozzles.size(); nozzle++) {
            ring.add(new RingPetal(Group.NOZZLE, nozzle));
        }
        return ring;
    }

    /**
     * @return each petal's span, radians
     */
    public double petalArc() {
        return (FULL_TURN - GROUPS * GROUP_GAP) / (types.size() + flows.size() + nozzles.size());
    }

    /**
     * Where a petal starts, clockwise from the top.
     *
     * @param petal the petal's place in the ring
     * @return its start angle, radians in [0, 2 pi)
     */
    public double startOf(int petal) {
        int gapsBefore = petal < types.size() ? 0 : petal < types.size() + flows.size() ? 1 : NOZZLE_GAPS_BEFORE;
        double gooCentered = -types.size() * petalArc() / HALF;
        return wrap(gooCentered + petal * petalArc() + gapsBefore * GROUP_GAP);
    }

    /**
     * The petal an angle falls on.
     *
     * @param angle the angle clockwise from the top, radians
     * @return the petal's place in the ring, or {@link #NONE} in a gap between arcs
     */
    public int petalAt(double angle) {
        for (int petal = 0; petal < types.size() + flows.size() + nozzles.size(); petal++) {
            if (wrap(angle - startOf(petal)) < petalArc()) {
                return petal;
            }
        }
        return NONE;
    }

    private static double wrap(double angle) {
        double wrapped = angle % FULL_TURN;
        return wrapped < 0 ? wrapped + FULL_TURN : wrapped;
    }

    /**
     * What letting go over a petal sets: that petal's setting, the other two as they were.
     *
     * @param kept  what the sprayer is set to
     * @param petal the petal let go over
     * @return the new setting
     */
    public SprayerSetting picked(SprayerSetting kept, RingPetal petal) {
        return switch (petal.group()) {
            case GOO -> new SprayerSetting(Optional.of(types.get(petal.index())), kept.flow(), kept.nozzle());
            case FLOW -> new SprayerSetting(kept.pickedType(), flows.get(petal.index()), kept.nozzle());
            case NOZZLE -> new SprayerSetting(kept.pickedType(), kept.flow(), nozzles.get(petal.index()));
        };
    }
}
