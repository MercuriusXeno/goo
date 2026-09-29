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
 * to the tier the knob names, at an even pace within each tier, each tier taking
 * twice the time of the one before (decision crystal-pace-doubles-per-tier); the
 * item inside is the highest tier reached.
 */
public final class CrystallizerPhases {

    /** The catalyst crystallizing spends. */
    public static final ResourceKey<GooTypeDefinition> CATALYST = GooTypes.CRYSTAL;

    /** Goo crystallizes in steps of this many mB, each spending one mB of crystal (10%). */
    public static final int GOO_PER_CRYSTAL = 10;

    /** A chrysm crystallizes from empty in 25 s, and each tier after takes twice the one before. */
    public static final int CHRYSM_TICKS = 500;
    /** The knob's five positions: 0 is off, and 1 to 4 cap crystallizing at the tier of their number. */
    public static final int KNOB_POSITIONS = 5;
    /** The knob's highest position, materia. */
    public static final int KNOB_MAX = KNOB_POSITIONS - 1;

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
     * Whether a canister takes an arriving goo beside what the other canister holds:
     * crystal always, any other goo only while the other canister holds crystal or
     * nothing, so at most one canister holds the goo that grows.
     *
     * @param other    what the other canister holds
     * @param incoming the goo arriving, or null for a fluid that is not goo
     * @return true when the canister takes it
     */
    public static boolean admits(Held other, @Nullable ResourceKey<GooTypeDefinition> incoming) {
        return CATALYST.equals(incoming) || !other.holdsGoo() || other.holds(CATALYST);
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
     * A right click on the knob steps it up and wraps from materia to off (decision
     * dial-five-positions-off-to-materia).
     *
     * @param knob the knob's position, 0 to 4
     * @return the next position
     */
    public static int nextKnob(int knob) {
        return (knob + 1) % KNOB_POSITIONS;
    }

    /**
     * The tier a knob position caps crystallizing at (decision dial-five-positions-off-to-materia).
     *
     * @param knob the knob's position, 0 to 4
     * @return chrysm, budding chrysm, flowering chrysm or materia for 1 to 4, null for off
     */
    public static @Nullable ChrysmTier tierForKnob(int knob) {
        return knob == 0 ? null : ChrysmTier.values()[knob - 1];
    }

    /**
     * Operator ruling: the crystal is clickable once it reaches maturity, the knob's
     * tier. With the knob off nothing grows, so any tier a crystal already reached is
     * mature.
     *
     * @param crystallized the crystallized volume, in mB
     * @param cap          the tier the knob caps at, or null when off
     * @return true when the crystal is mature
     */
    public static boolean isMature(long crystallized, @Nullable ChrysmTier cap) {
        return cap == null ? reachedTier(crystallized) != null : crystallized > 0 && crystallized >= cap.volume();
    }

    /**
     * The chrysm a click on the crystal hands (operator ruling): the dial's tier once
     * the crystal reaches it, the rest handed as excess; with the dial off, the highest
     * tier reached.
     *
     * @param crystallized the crystallized volume, in mB
     * @param cap          the tier the knob caps at, or null when off
     * @return the tier handed, or null while the crystal is not mature
     */
    public static @Nullable ChrysmTier harvestTier(long crystallized, @Nullable ChrysmTier cap) {
        if (!isMature(crystallized, cap)) {
            return null;
        }
        return cap == null ? reachedTier(crystallized) : cap;
    }

    /**
     * The ticks a tier's span takes to crystallize: 25 s for a chrysm, doubling each
     * tier (decision crystal-pace-doubles-per-tier).
     *
     * @param tier the tier
     * @return the ticks from the tier below to this one
     */
    public static int ticksFor(ChrysmTier tier) {
        return CHRYSM_TICKS << tier.ordinal();
    }

    /**
     * @param tier the tier
     * @return the volume of the tier below, where this tier's span starts; zero for a chrysm
     */
    public static long spanStart(ChrysmTier tier) {
        return tier.ordinal() == 0 ? 0 : ChrysmTier.values()[tier.ordinal() - 1].volume();
    }

    /**
     * The tier a crystallized volume is growing toward: the first it has not reached,
     * or materia once every tier is reached.
     *
     * @param crystallized the goo crystallized so far, in mB
     * @return the tier growing
     */
    public static ChrysmTier growingTier(long crystallized) {
        for (ChrysmTier tier : ChrysmTier.values()) {
            if (crystallized < tier.volume()) {
                return tier;
            }
        }
        return ChrysmTier.MATERIA;
    }

    /**
     * An even pace per tier, the tier's span over its ticks, so the goo drawn a tick
     * rises about 16x per tier as the cost rises 32x and the time doubles (decision
     * crystal-pace-doubles-per-tier).
     *
     * @param crystallized the goo crystallized so far, in mB
     * @return the mB one tick may crystallize
     */
    public static double paceAllowance(long crystallized) {
        ChrysmTier tier = growingTier(crystallized);
        return (double) (tier.volume() - spanStart(tier)) / ticksFor(tier);
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
     * @param knob         the tier the knob caps crystallizing at, or null when it is off
     * @param budget       the mB the pace lets this tick crystallize
     * @return the step, or null when nothing crystallizes this tick
     */
    public static @Nullable Step step(Held ingredient, Held catalyst, long crystallized,
                                      @Nullable ResourceKey<GooTypeDefinition> formingType,
                                      @Nullable ChrysmTier knob, double budget) {
        ResourceKey<GooTypeDefinition> type = ingredient.type();
        if (knob == null || type == null || formingType != null && !formingType.equals(type)) {
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
