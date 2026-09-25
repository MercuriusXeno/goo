package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.resources.ResourceKey;
import java.util.List;

/**
 * One fuel goo's burn in the crucible: the heat ticks one mB buys and the mB
 * the crucible melts per tick on that heat (decision fuel-goo-heats-per-mb).
 *
 * @param fuel       the fuel goo type
 * @param ticksPerMb the heat ticks one mB of the fuel buys
 * @param meltRate   the mB melted per tick on this fuel's heat
 */
public record FuelGrade(ResourceKey<GooTypeDefinition> fuel, int ticksPerMb, int meltRate) {

    /**
     * Returns the configured fuel grades in burn order: the first grade the reservoir holds burns first,
     * so unstable burns before blaze (decision unstable-goo-is-super-fuel).
     *
     * @return the grades, read from GooConfig
     */
    public static List<FuelGrade> configured() {
        return List.of(configuredUnstable(), configuredBlaze());
    }

    /**
     * Returns unstable's configured grade.
     *
     * @return the unstable grade
     */
    public static FuelGrade configuredUnstable() {
        return new FuelGrade(GooTypes.UNSTABLE, GooConfig.UNSTABLE_TICKS_PER_MB.get(), GooConfig.UNSTABLE_MELT_RATE.get());
    }

    /**
     * Returns blaze's configured grade.
     *
     * @return the blaze grade
     */
    public static FuelGrade configuredBlaze() {
        return new FuelGrade(GooTypes.BLAZE, GooConfig.BLAZE_TICKS_PER_MB.get(), GooConfig.BLAZE_MELT_RATE.get());
    }
}
