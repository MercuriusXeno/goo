package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import java.util.List;

/**
 * One fuel goo's burn in the crucible: the heat ticks one mB buys (decision fuel-goo-heats-per-mb)
 * and the exponent of an item's melt clock on that heat (decision melt-time-is-mb-to-a-power).
 *
 * @param fuel         the fuel goo type
 * @param ticksPerMb   the heat ticks one mB of the fuel buys
 * @param meltExponent an item alone melts in ceil(mB ^ this) ticks on this fuel's heat
 */
public record FuelGrade(ResourceKey<GooTypeDefinition> fuel, int ticksPerMb, double meltExponent) {

    /**
     * Returns the configured fuel grades: both burn together as the combo when both stand,
     * and a lone grade burns on its own clock (decision blaze-unstable-combo-burn).
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
        return new FuelGrade(GooTypes.UNSTABLE, GooConfig.UNSTABLE_TICKS_PER_MB.get(), GooConfig.UNSTABLE_MELT_EXPONENT.get());
    }

    /**
     * Returns blaze's configured grade.
     *
     * @return the blaze grade
     */
    public static FuelGrade configuredBlaze() {
        return new FuelGrade(GooTypes.BLAZE, GooConfig.BLAZE_TICKS_PER_MB.get(), GooConfig.BLAZE_MELT_EXPONENT.get());
    }
}
