package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.List;

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
     * Burns one tick's heat for a melt tick, buying 1 mB of fuel first when cold.
     * A tick with nothing to melt spends nothing, so heat is spent only while melting.
     *
     * @param meltsAnItem whether the crucible holds an item to melt this tick
     * @param grades      the fuel grades in burn order
     * @param stock       the reservoir
     * @return the mB to melt this tick, 0 when nothing melts or no heat could be bought
     */
    public int burnMeltTick(boolean meltsAnItem, List<FuelGrade> grades, FuelStock stock) {
        if (!meltsAnItem) {
            return 0;
        }
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
     * Returns the mB of fuel goo the stock holds across every grade.
     *
     * @param grades the fuel grades
     * @param stock  the reservoir
     * @return the fuel goo volume in mB
     */
    public static long fuelVolume(List<FuelGrade> grades, FuelStock stock) {
        long total = 0;
        for (FuelGrade candidate : grades) {
            total += stock.volume(candidate.fuel());
        }
        return total;
    }
}
