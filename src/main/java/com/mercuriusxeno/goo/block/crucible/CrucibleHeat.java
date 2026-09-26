package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The crucible's stored heat: ticks bought from fuel goo in the reservoir, and
 * the grade that bought them, which sets the melt rate while they last
 * (decision fuel-goo-heats-per-mb).
 */
public final class CrucibleHeat {

    /**
     * The fuel goo a crucible buys heat from, its reservoir in play.
     */
    public interface FuelStock {

        /**
         * Returns the mB of one goo type the stock holds.
         *
         * @param type the goo type
         * @return the volume in mB
         */
        int volume(ResourceKey<GooTypeDefinition> type);

        /**
         * Removes up to the given mB of one goo type.
         *
         * @param type   the goo type
         * @param amount the mB to remove
         * @return the mB removed
         */
        int extract(ResourceKey<GooTypeDefinition> type, int amount);
    }

    /** The fewest grades a combo burns together. */
    private static final int COMBO_GRADES = 2;

    private int heatTicks;
    private @Nullable FuelGrade grade;

    /**
     * Returns the heat ticks left.
     *
     * @return the heat ticks
     */
    public int heatTicks() {
        return heatTicks;
    }

    /**
     * Returns the grade that bought the heat left, or null when cold.
     *
     * @return the grade, or null
     */
    public @Nullable FuelGrade grade() {
        return grade;
    }

    /**
     * Sets the heat to the given ticks at the given grade, as a load or a grant does.
     *
     * @param ticks      the heat ticks
     * @param boughtWith the grade the ticks burn at, or null when ticks is 0
     */
    public void set(int ticks, @Nullable FuelGrade boughtWith) {
        heatTicks = Math.max(0, ticks);
        grade = heatTicks > 0 ? boughtWith : null;
    }

    /**
     * Returns true when the crucible holds heat or any fuel goo to buy it with.
     *
     * @param grades the fuel grades in burn order
     * @param stock  the reservoir
     * @return true if the crucible can melt
     */
    public boolean canHeat(List<FuelGrade> grades, FuelStock stock) {
        return heatTicks > 0 || firstStockedGrade(grades, stock) != null;
    }

    /**
     * Burns one melt tick. When every grade's fuel stands the combo burns first and bought
     * heat waits; otherwise one tick of bought heat burns, buying 1 mB of fuel first when cold.
     * A tick with nothing to melt spends nothing, so heat is spent only while melting.
     *
     * @param meltsAnItem whether the crucible holds an item to melt this tick
     * @param grades      the fuel grades in burn order
     * @param comboDrain  the mB of each fuel a combo tick burns
     * @param stock       the reservoir
     * @return the mB to melt this tick, 0 when nothing melts or no heat could be bought
     */
    public int burnMeltTick(boolean meltsAnItem, List<FuelGrade> grades, int comboDrain, FuelStock stock) {
        if (!meltsAnItem) {
            return 0;
        }
        if (comboStands(grades, stock)) {
            return burnComboTick(grades, comboDrain, stock);
        }
        return burnBoughtHeat(grades, stock);
    }

    /**
     * Burns one tick of bought heat, buying 1 mB of the first stocked fuel first when cold.
     *
     * @param grades the fuel grades in burn order
     * @param stock  the reservoir
     * @return the melt rate of the heat burned, 0 when no heat could be bought
     */
    private int burnBoughtHeat(List<FuelGrade> grades, FuelStock stock) {
        if (heatTicks <= 0 && !buyHeat(grades, stock)) {
            return 0;
        }
        FuelGrade burning = grade;
        heatTicks--;
        if (heatTicks == 0) {
            grade = null;
        }
        return burning == null ? 0 : burning.meltRate();
    }

    /**
     * Returns true when there are two or more grades and the stock holds every one's fuel.
     *
     * @param grades the fuel grades
     * @param stock  the reservoir
     * @return true if the combo burns
     */
    static boolean comboStands(List<FuelGrade> grades, FuelStock stock) {
        if (grades.size() < COMBO_GRADES) {
            return false;
        }
        for (FuelGrade candidate : grades) {
            if (stock.volume(candidate.fuel()) <= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Burns one combo tick: extracts up to the drain from each fuel and melts the product
     * of the grades' melt rates, each scaled by the share of the drain its fuel supplied,
     * so a short last tick melts in proportion rather than at full (decision blaze-unstable-combo-burn).
     *
     * @param grades     the fuel grades, every one stocked
     * @param comboDrain the mB of each fuel a full combo tick burns
     * @param stock      the reservoir
     * @return the mB to melt this tick
     */
    private static int burnComboTick(List<FuelGrade> grades, int comboDrain, FuelStock stock) {
        double melt = 1;
        for (FuelGrade candidate : grades) {
            int taken = stock.extract(candidate.fuel(), comboDrain);
            melt *= (double) candidate.meltRate() * taken / comboDrain;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(melt));
    }

    /**
     * Extracts 1 mB of the first stocked fuel and adds the heat it buys.
     *
     * @param grades the fuel grades in burn order
     * @param stock  the reservoir
     * @return true if heat was bought
     */
    private boolean buyHeat(List<FuelGrade> grades, FuelStock stock) {
        FuelGrade stocked = firstStockedGrade(grades, stock);
        if (stocked == null || stock.extract(stocked.fuel(), 1) < 1) {
            return false;
        }
        set(stocked.ticksPerMb(), stocked);
        return true;
    }

    /**
     * Returns the first grade whose fuel the stock holds, or null.
     *
     * @param grades the fuel grades in burn order
     * @param stock  the reservoir
     * @return the grade, or null
     */
    private static @Nullable FuelGrade firstStockedGrade(List<FuelGrade> grades, FuelStock stock) {
        for (FuelGrade candidate : grades) {
            if (stock.volume(candidate.fuel()) > 0) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The melt ticks one fuel grade holds: its bought heat plus its stock at its ticks per mB.
     *
     * @param grade the fuel grade
     * @param ticks the melt ticks it lasts
     */
    public record FuelBurn(FuelGrade grade, long ticks) {
    }

    /**
     * Returns the melt ticks each grade holds, in burn order, leaving out a grade holding none
     * (decision heat-row-reads-seconds).
     *
     * @param grades   the fuel grades in burn order
     * @param volumeOf the mB of a fuel type the reservoir holds
     * @return one burn per grade holding heat or stock
     */
    public List<FuelBurn> forecast(List<FuelGrade> grades, ToIntFunction<ResourceKey<GooTypeDefinition>> volumeOf) {
        List<FuelBurn> burns = new ArrayList<>();
        for (FuelGrade candidate : grades) {
            long ticks = (long) volumeOf.applyAsInt(candidate.fuel()) * candidate.ticksPerMb()
                    + boughtTicksOf(candidate);
            if (ticks > 0) {
                burns.add(new FuelBurn(candidate, ticks));
            }
        }
        return burns;
    }

    /**
     * Returns the bought heat ticks when the given grade's fuel bought them, else 0.
     *
     * @param candidate the fuel grade
     * @return the bought heat ticks of that fuel
     */
    private int boughtTicksOf(FuelGrade candidate) {
        return grade != null && grade.fuel().equals(candidate.fuel()) ? heatTicks : 0;
    }
}
