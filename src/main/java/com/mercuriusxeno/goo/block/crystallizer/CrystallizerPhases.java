package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.ChrysmTier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * The crystallizer's arithmetic over plain values, so a unit test reaches it
 * without a registry (decision crystallizer-emits-chrysm). Operator rulings: the
 * crystallizer holds two canisters, and it doesn't matter which slot has the
 * crystal goo, the other slot governs what goo grows; goo crystallizes as it
 * goes, spending crystal at 10% of the goo and pausing when crystal runs out, up
 * to the tier the knob names, at an even pace per tier; the item inside is the
 * highest tier reached.
 */
public final class CrystallizerPhases {

    /** The catalyst crystallizing spends. */
    public static final ResourceKey<GooTypeDefinition> CATALYST = GooTypes.CRYSTAL;

    /** Goo crystallizes in steps of this many mB, each spending one mB of crystal (10%). */
    public static final int GOO_PER_CRYSTAL = 10;

    /** Operator ruling: each tier takes about 10 s more, 200 ticks. */
    public static final int TICKS_PER_TIER = 200;
    /** Below a chrysm the pace is flat: a chrysm's volume over one tier's ticks. */
    private static final double FLAT_PACE = (double) ChrysmTier.CHRYSM.volume() / TICKS_PER_TIER;
    /** Past a chrysm the volume grows 1,000-fold, one tier, every tier's ticks. */
    private static final double GROWTH_PER_TICK = Math.log(1_000) / TICKS_PER_TIER;

    private CrystallizerPhases() {
    }

    /**
     * What one canister holds, in plain values.
     *
     * @param type   the goo type it holds, or null when empty or holding no goo
     * @param volume the volume it holds, in mB
     */
    public record Held(@Nullable ResourceKey<GooTypeDefinition> type, int volume) {

        /** A canister slot holding nothing. */
        public static final Held NOTHING = new Held(null, 0);

        boolean holds(ResourceKey<GooTypeDefinition> wanted) {
            return volume > 0 && wanted.equals(type);
        }

        boolean holdsGoo() {
            return volume > 0 && type != null;
        }
    }

    /**
     * Which canister slot is the catalyst and which the ingredient.
     *
     * @param catalyst   the slot whose crystal is spent
     * @param ingredient the slot whose goo grows
     */
    public record Roles(int catalyst, int ingredient) {
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
     * Operator ruling: an even pace per tier, a chrysm at about 10 s, a kilochrysm by
     * 20 s and a megachrysm by 30 s. Flat below a chrysm, then growing with what's
     * crystallized so every tier's 1,000-fold climb takes the same 200 ticks.
     *
     * @param crystallized the goo crystallized so far, in mB
     * @return the mB one tick may crystallize
     */
    public static double paceAllowance(long crystallized) {
        return crystallized < ChrysmTier.CHRYSM.volume() ? FLAT_PACE : crystallized * GROWTH_PER_TICK;
    }

    /**
     * Carries the pace across ticks: this tick's allowance joins the unspent budget,
     * held to at most one tick's allowance and one step so an idle stretch banks no burst.
     *
     * @param budget       the mB left unspent from earlier ticks
     * @param crystallized the goo crystallized so far, in mB
     * @return the budget this tick may spend
     */
    public static double nextBudget(double budget, long crystallized) {
        double allowance = paceAllowance(crystallized);
        return Math.min(budget + allowance, allowance + GOO_PER_CRYSTAL);
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
     * Operator ruling: whichever canister holds crystal is the catalyst and the
     * other's goo is what grows; both holding crystal, the first slot is the catalyst.
     *
     * @param first  what the first slot holds
     * @param second what the second slot holds
     * @return the roles, or null while no slot holds crystal or the other holds nothing
     */
    public static @Nullable Roles roles(Held first, Held second) {
        if (first.holds(CATALYST) && second.holdsGoo()) {
            return new Roles(0, 1);
        }
        if (second.holds(CATALYST) && first.holdsGoo()) {
            return new Roles(1, 0);
        }
        return null;
    }

    /**
     * One tick's crystallizing: as much of the ingredient as the catalyst pays for and
     * the pace allows, in whole steps, up to the knob's tier, and only of the type
     * already crystallized.
     *
     * @param ingredient   what the ingredient canister holds
     * @param catalyst     what the catalyst canister holds
     * @param crystallized the goo crystallized so far, in mB
     * @param formingType  the type crystallized so far, or null when none is
     * @param knob         the tier the knob caps crystallizing at
     * @param budget       the mB the pace lets this tick crystallize
     * @return the step, or null when nothing crystallizes this tick
     */
    public static @Nullable Step step(Held ingredient, Held catalyst, long crystallized,
                                      @Nullable ResourceKey<GooTypeDefinition> formingType, ChrysmTier knob,
                                      double budget) {
        ResourceKey<GooTypeDefinition> type = ingredient.type();
        if (type == null || formingType != null && !formingType.equals(type)) {
            return null;
        }
        long room = Math.max(0, knob.volume() - crystallized);
        long steps = Math.min(Math.min(ingredient.volume() / GOO_PER_CRYSTAL, catalyst.volume()),
                Math.min(room / GOO_PER_CRYSTAL, (long) (budget / GOO_PER_CRYSTAL)));
        if (steps <= 0) {
            return null;
        }
        return new Step(type, Math.toIntExact(steps * GOO_PER_CRYSTAL), Math.toIntExact(steps));
    }
}
