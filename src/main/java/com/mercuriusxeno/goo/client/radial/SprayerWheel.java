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
 * The sprayer wheel's model: a contracted petal for each goo type of the fitted canister that has a residual, most
 * held first, and inside the hub the nozzles in two staggered rows over a divider and the flow steps under it.
 * Picking a petal or an icon sets that one setting and keeps the other two.
 * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
 * decision spray-item-sprays-more-or-less-per-tick
 * decision spray-item-takes-different-nozzles-doing-different-things
 * operator ruling 2026-11-18: icons inside the hub; the goo petals contract
 *
 * @param types   the sprayable goo types, most held first
 * @param flows   the flow steps, low to high
 * @param nozzles the nozzles
 */
public record SprayerWheel(List<ResourceKey<GooTypeDefinition>> types, List<SprayerFlow> flows,
                           List<SprayerNozzle> nozzles) {

    /** No petal: the cursor rests in the hub or past the ring. */
    public static final int NONE = -1;
    private static final double FULL_TURN = Math.PI * 2;
    private static final double HALF = 2;
    /**
     * The widest a goo petal spans and the gap between petals, radians.
     * operator ruling 2026-11-18: the goo type petals still need to contract
     */
    private static final double MOST_GOO_ARC = Math.toRadians(72);
    private static final double GOO_GAP = Math.toRadians(6);
    /** The rows inside the hub, centered on it: the upper and lower nozzle rows, the divider, the flow row. */
    private static final double UPPER_NOZZLE_ROW = -1.4;
    /** The nozzles alternate between two rows. */
    private static final int ROWS_OF_NOZZLES = 2;
    private static final double LOWER_NOZZLE_ROW = -0.5;
    private static final double DIVIDER_ROW = 0.3;
    private static final double FLOW_ROW = 1.1;

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
     * @return each goo petal's span, radians: an even share of the turn, never wider than a contracted petal
     */
    public double gooArc() {
        return types.isEmpty() ? 0 : Math.min(MOST_GOO_ARC, FULL_TURN / types.size() - GOO_GAP);
    }

    /**
     * Where a goo petal starts, clockwise from the top: the petals spread evenly around the turn, the first centered
     * at the top.
     *
     * @param type the type's place in the list
     * @return its start angle, radians in [0, 2 pi)
     */
    public double gooStartOf(int type) {
        return wrap(type * FULL_TURN / types.size() - gooArc() / HALF);
    }

    /**
     * The goo petal an angle falls on.
     *
     * @param angle the angle clockwise from the top, radians
     * @return the type's place in the list, or {@link #NONE} between petals
     */
    public int gooAt(double angle) {
        for (int type = 0; type < types.size(); type++) {
            if (wrap(angle - gooStartOf(type)) < gooArc()) {
                return type;
            }
        }
        return NONE;
    }

    /**
     * Where an icon sits inside the hub, as offsets from the hub's center: across in half steps, a step being the
     * gap between two icons of one row, and down in rows. The nozzles stand in two staggered rows, 1, 3 and 5 on
     * the lower and 2, 4 and 6 on the upper, each a half step right of the one before; under a divider the flow
     * steps stand in a row, each under 2, 4 and 6.
     * operator ruling 2026-11-18: arrange the icons as 2 4 6 over 1 3 5, a divider, then 1 2 3; drop the wheel
     *
     * @param icon a nozzle or flow petal
     * @return the icon's offset, across in half steps and down in rows
     */
    public double[] hubOffsetOf(RingPetal icon) {
        double middle = (nozzles.size() - 1) / HALF;
        if (icon.group() == Group.NOZZLE) {
            boolean lower = icon.index() % ROWS_OF_NOZZLES == 0;
            return new double[]{icon.index() - middle, lower ? LOWER_NOZZLE_ROW : UPPER_NOZZLE_ROW};
        }
        // each flow step stands under the upper row's nozzle of its place: 2, 4 and 6
        return new double[]{icon.index() * ROWS_OF_NOZZLES + 1 - middle, FLOW_ROW};
    }

    /**
     * @return the row the divider between the nozzles and the flow steps runs along
     */
    public double dividerRow() {
        return DIVIDER_ROW;
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
