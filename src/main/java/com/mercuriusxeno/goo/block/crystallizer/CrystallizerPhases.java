package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * The crystallizer's phase arithmetic over plain values, so a unit test reaches
 * it without a registry (decision crystallizer-emits-chrysm). Each phase takes
 * the held chrysm to the next tier: it needs the goo between the two tiers'
 * volumes of the forming type, the phase's crystal goo, and the phase's time,
 * and the dial names the tier the crystallizer stops at. Crystal goo is the
 * catalyst; the crystallizer holds one other type, the type it crystallizes,
 * or crystallizes crystal itself when crystal is all it holds.
 */
public final class CrystallizerPhases {

    /** n, operator ruling: one chrysm forms in 200 ticks. */
    public static final int CHRYSM_TICKS = 200;

    /** The catalyst each phase spends. */
    public static final ResourceKey<GooTypeDefinition> CATALYST = GooTypes.CRYSTAL;

    private static final double TIER_LOG_BASE = Math.log(1_000);

    private CrystallizerPhases() {
    }

    /**
     * What the crystallizer holds, in plain values.
     *
     * @param held       the goo in its holding
     * @param formed     the tier standing formed inside, or null
     * @param formedType the type of the chrysm standing formed inside, or null
     * @param dial       the tier the dial stops at
     */
    public record Chamber(GooContents held, @Nullable ChrysmTier formed,
                          @Nullable ResourceKey<GooTypeDefinition> formedType, ChrysmTier dial) {
    }

    /**
     * m, operator ruling: a phase spends the cube root of its tier's volume in crystal goo.
     *
     * @param tier the tier the phase forms
     * @return the crystal goo spent, in mB
     */
    public static int crystalCost(ChrysmTier tier) {
        return (int) Math.round(Math.cbrt(tier.volume()));
    }

    /**
     * Operator ruling: a phase takes n x log1000 of its tier's volume, so 200,
     * 400 and 600 ticks.
     *
     * @param tier the tier the phase forms
     * @return the phase's time, in ticks
     */
    public static int phaseTicks(ChrysmTier tier) {
        return (int) Math.round(CHRYSM_TICKS * Math.log(tier.volume()) / TIER_LOG_BASE);
    }

    /**
     * Operator ruling: a right click on the dial steps its size and wraps from 3 to 1.
     *
     * @param dial the dial's size, 1 to 3
     * @return the next size
     */
    public static int nextDial(int dial) {
        return dial % ChrysmTier.values().length + 1;
    }

    /**
     * The tier the chamber forms next: a chrysm from nothing, else the tier
     * above the formed one while the dial stands past it.
     *
     * @param chamber the crystallizer's state
     * @return the tier in progress, or null when the chamber holds at the dial
     */
    public static @Nullable ChrysmTier goal(Chamber chamber) {
        ChrysmTier formed = chamber.formed();
        if (formed == null) {
            return ChrysmTier.CHRYSM;
        }
        return formed.ordinal() < chamber.dial().ordinal() ? ChrysmTier.values()[formed.ordinal() + 1] : null;
    }

    /**
     * The type the chamber crystallizes: the formed chrysm's type, else the one
     * type besides crystal in the holding, else crystal when it is all the holding.
     *
     * @param chamber the crystallizer's state
     * @return the forming type, or null when the chamber holds nothing
     */
    public static @Nullable ResourceKey<GooTypeDefinition> formingType(Chamber chamber) {
        return chamber.formedType() != null ? chamber.formedType() : formingType(chamber.held());
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
     * The most of one type the holding takes: the goo the phase in progress still
     * needs of the forming type, the phase's crystal beside it, and nothing of a
     * third type or while the chamber holds at the dial.
     *
     * @param chamber the crystallizer's state
     * @param offered the type offered
     * @return the most of that type the holding takes, in mB
     */
    public static int capacityFor(Chamber chamber, ResourceKey<GooTypeDefinition> offered) {
        ChrysmTier goal = goal(chamber);
        if (goal == null) {
            return 0;
        }
        return CATALYST.equals(offered) ? crystalCapacity(chamber, goal) : typeCapacity(chamber, goal, offered);
    }

    private static int crystalCapacity(Chamber chamber, ChrysmTier goal) {
        int cost = crystalCost(goal);
        return crystalForms(formingType(chamber)) ? gooNeeded(chamber, goal) + cost : cost;
    }

    private static int typeCapacity(Chamber chamber, ChrysmTier goal, ResourceKey<GooTypeDefinition> offered) {
        ResourceKey<GooTypeDefinition> forming = formingType(chamber);
        boolean unclaimed = chamber.formedType() == null && crystalForms(forming);
        return unclaimed || offered.equals(forming) ? gooNeeded(chamber, goal) : 0;
    }

    private static boolean crystalForms(@Nullable ResourceKey<GooTypeDefinition> forming) {
        return forming == null || CATALYST.equals(forming);
    }

    /**
     * The goo one phase spends from the holding.
     *
     * @param chamber the crystallizer's state
     * @return the goo spent, or null when the phase in progress lacks goo or crystal
     */
    public static @Nullable GooContents spentToForm(Chamber chamber) {
        ChrysmTier goal = goal(chamber);
        ResourceKey<GooTypeDefinition> forming = formingType(chamber);
        if (goal == null || forming == null) {
            return null;
        }
        GooContents spent = GooContents.EMPTY.withAdded(forming, gooNeeded(chamber, goal))
                .withAdded(CATALYST, crystalCost(goal));
        for (var entry : spent.getAll().entrySet()) {
            if (chamber.held().getVolume(entry.getKey()) < entry.getValue()) {
                return null;
            }
        }
        return spent;
    }

    private static int gooNeeded(Chamber chamber, ChrysmTier goal) {
        long formedVolume = chamber.formed() == null ? 0 : chamber.formed().volume();
        return Math.toIntExact(goal.volume() - formedVolume);
    }
}
