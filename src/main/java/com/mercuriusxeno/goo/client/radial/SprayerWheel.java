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
 * The sprayer wheel's model: the canister's goo types that have a residual, most held first, which the glove's wheel
 * shows as type petals, and inside the hub a three by three grid of the nozzles over a divider and the flow steps
 * under it.
 * Picking a petal or an icon sets that one setting and keeps the other two.
 * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
 * decision spray-item-sprays-more-or-less-per-tick
 * decision spray-item-takes-different-nozzles-doing-different-things
 * operator ruling 2026-11-19: a three by three grid in the hub; the type petals open onto their abilities
 *
 * @param types   the sprayable goo types, most held first
 * @param flows   the flow steps, low to high
 * @param nozzles the nozzles
 */
public record SprayerWheel(List<ResourceKey<GooTypeDefinition>> types, List<SprayerFlow> flows,
                           List<SprayerNozzle> nozzles) {

    /**
     * The hub's grid in steps from its center: three columns, two nozzle rows, the divider tight under them and the
     * flow row tight under the divider.
     */
    private static final int COLUMNS = 3;
    private static final double CENTER_COLUMN = 1;
    private static final double FIRST_ROW = -1.25;
    private static final double DIVIDER_ROW = 0.42;
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
     * Where an icon sits inside the hub, as offsets from the hub's center in grid steps, across and down: three
     * columns aligned, the nozzles in two rows of three in their order, Mist, Wide and Line on top, and under a
     * divider the flow steps in a row, low to high, one to a column.
     * operator ruling 2026-11-19: put the icons in alignment and arrange them three by three; tighten the divider
     *
     * @param icon a nozzle or flow petal
     * @return the icon's offset, across and down in grid steps
     */
    public double[] hubOffsetOf(RingPetal icon) {
        if (icon.group() == Group.NOZZLE) {
            return new double[]{icon.index() % COLUMNS - CENTER_COLUMN, FIRST_ROW + rowOf(icon.index())};
        }
        return new double[]{icon.index() - CENTER_COLUMN, FLOW_ROW};
    }

    /**
     * @param nozzle the nozzle's place in the list
     * @return the nozzle row it stands in, counting from the top: three to a row
     */
    private static int rowOf(int nozzle) {
        return nozzle / COLUMNS;
    }

    /**
     * @return the row the divider between the nozzles and the flow steps runs along, in grid steps
     */
    public double dividerRow() {
        return DIVIDER_ROW;
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
