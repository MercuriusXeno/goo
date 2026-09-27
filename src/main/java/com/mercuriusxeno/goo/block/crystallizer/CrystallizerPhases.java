package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * The crystallizer's arithmetic over plain values, so a unit test reaches it
 * without a registry (decision crystallizer-emits-chrysm). Operator ruling:
 * goo crystallizes as it arrives, spending crystal goo at 10% of the goo and
 * pausing when crystal runs out, up to the tier the knob names; the item inside
 * is the highest tier the crystallized volume reached. Crystal goo is the
 * catalyst; the crystallizer holds one other type, the type it crystallizes,
 * or crystallizes crystal itself when crystal is all it holds.
 */
public final class CrystallizerPhases {

    /** The catalyst crystallizing spends. */
    public static final ResourceKey<GooTypeDefinition> CATALYST = GooTypes.CRYSTAL;

    /** Goo crystallizes in steps of this many mB, each spending one mB of crystal (10%). */
    public static final int GOO_PER_CRYSTAL = 10;

    private CrystallizerPhases() {
    }

    /**
     * What the crystallizer holds, in plain values.
     *
     * @param held         the raw goo and crystal in its holding
     * @param crystallized the goo crystallized so far, in mB
     * @param formingType  the type crystallized so far, or null when none is
     * @param knob         the tier the knob caps crystallizing at
     */
    public record Chamber(GooContents held, long crystallized,
                          @Nullable ResourceKey<GooTypeDefinition> formingType, ChrysmTier knob) {
    }

    /**
     * One tick's crystallizing: the goo turned to crystal and the crystal spent.
     *
     * @param type    the type crystallized
     * @param goo     the goo crystallized, in mB
     * @param crystal the crystal goo spent beside it, in mB
     */
    public record Step(ResourceKey<GooTypeDefinition> type, int goo, int crystal) {
    }

    /**
     * Operator ruling: a right click on the knob steps its size and wraps from 3 to 1.
     *
     * @param knob the knob's size, 1 to 3
     * @return the next size
     */
    public static int nextKnob(int knob) {
        return knob % ChrysmTier.values().length + 1;
    }

    /**
     * The highest tier a crystallized volume reached.
     *
     * @param crystallized the crystallized volume, in mB
     * @return the tier, or null below a chrysm
     */
    public static @Nullable ChrysmTier reachedTier(long crystallized) {
        ChrysmTier reached = null;
        for (ChrysmTier tier : ChrysmTier.values()) {
            if (crystallized >= tier.volume()) {
                reached = tier;
            }
        }
        return reached;
    }

    /**
     * The type the chamber crystallizes: the type crystallized so far, else the
     * one type besides crystal in the holding, else crystal when it is all the holding.
     *
     * @param chamber the crystallizer's state
     * @return the forming type, or null when the chamber holds nothing
     */
    public static @Nullable ResourceKey<GooTypeDefinition> formingType(Chamber chamber) {
        return chamber.formingType() != null ? chamber.formingType() : formingType(chamber.held());
    }

    /**
     * The one type besides crystal in a holding, or crystal when it is all the holding.
     *
     * @param held the goo in the holding
     * @return the forming type, or null for an empty holding
     */
    public static @Nullable ResourceKey<GooTypeDefinition> formingType(GooContents held) {
        ResourceKey<GooTypeDefinition> crystal = null;
        for (var entry : held.getAll().entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            if (!CATALYST.equals(entry.getKey())) {
                return entry.getKey();
            }
            crystal = entry.getKey();
        }
        return crystal;
    }

    /**
     * The most of one type the holding takes: the goo still to crystallize up to
     * the knob's tier, the crystal that pays for it, and nothing of a third type.
     *
     * @param chamber the crystallizer's state
     * @param offered the type offered
     * @return the most of that type the holding takes, in mB
     */
    public static int capacityFor(Chamber chamber, ResourceKey<GooTypeDefinition> offered) {
        long room = roomToKnob(chamber);
        ResourceKey<GooTypeDefinition> forming = formingType(chamber);
        if (CATALYST.equals(offered)) {
            return Math.toIntExact(crystalForms(forming) ? room + crystalFor(room) : crystalFor(room));
        }
        boolean unclaimed = chamber.formingType() == null && crystalForms(forming);
        return unclaimed || offered.equals(forming) ? Math.toIntExact(room) : 0;
    }

    /**
     * One tick's crystallizing: as much held goo of the forming type as the held
     * crystal pays for, in whole steps, up to the knob's tier.
     *
     * @param chamber the crystallizer's state
     * @return the step, or null when nothing crystallizes this tick
     */
    public static @Nullable Step step(Chamber chamber) {
        ResourceKey<GooTypeDefinition> forming = formingType(chamber);
        if (forming == null) {
            return null;
        }
        long steps = Math.min(affordableSteps(chamber.held(), forming), roomToKnob(chamber) / GOO_PER_CRYSTAL);
        if (steps <= 0) {
            return null;
        }
        return new Step(forming, Math.toIntExact(steps * GOO_PER_CRYSTAL), Math.toIntExact(steps));
    }

    /**
     * Whole steps the holding pays for: goo in 10 mB lots and one mB of crystal
     * each, or 11 mB of crystal each when crystal crystallizes itself.
     *
     * @param held    the goo in the holding
     * @param forming the type crystallizing
     * @return the steps paid for
     */
    private static long affordableSteps(GooContents held, ResourceKey<GooTypeDefinition> forming) {
        int crystal = held.getVolume(CATALYST);
        if (CATALYST.equals(forming)) {
            return crystal / (GOO_PER_CRYSTAL + 1);
        }
        return Math.min(held.getVolume(forming) / GOO_PER_CRYSTAL, crystal);
    }

    /**
     * The goo left to crystallize before the knob tier; a tank capacity counts what it holds.
     *
     * @param chamber the crystallizer state
     * @return the room, in mB
     */
    private static long roomToKnob(Chamber chamber) {
        return Math.max(0, chamber.knob().volume() - chamber.crystallized());
    }

    private static long crystalFor(long goo) {
        return (goo + GOO_PER_CRYSTAL - 1) / GOO_PER_CRYSTAL;
    }

    private static boolean crystalForms(@Nullable ResourceKey<GooTypeDefinition> forming) {
        return forming == null || CATALYST.equals(forming);
    }
}
