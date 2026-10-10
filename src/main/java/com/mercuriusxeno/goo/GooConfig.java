package com.mercuriusxeno.goo;

import net.neoforged.neoforge.common.ModConfigSpec;

public class GooConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue BASE_VALUES_OVERRIDE_RECIPES;
    public static final int DEFAULT_SPARK_HEAT_TICKS = 20;
    public static final ModConfigSpec.IntValue SPARK_HEAT_TICKS;
    public static final int DEFAULT_BLAZE_TICKS_PER_MB = 4;
    public static final ModConfigSpec.IntValue BLAZE_TICKS_PER_MB;
    public static final double DEFAULT_BLAZE_MELT_EXPONENT = 0.75;
    public static final ModConfigSpec.DoubleValue BLAZE_MELT_EXPONENT;
    public static final int DEFAULT_UNSTABLE_TICKS_PER_MB = 1;
    public static final ModConfigSpec.IntValue UNSTABLE_TICKS_PER_MB;
    public static final double DEFAULT_UNSTABLE_MELT_EXPONENT = 0.5;
    public static final ModConfigSpec.DoubleValue UNSTABLE_MELT_EXPONENT;
    public static final int DEFAULT_COMBO_DRAIN_PER_TICK = 2;
    public static final ModConfigSpec.IntValue COMBO_DRAIN_PER_TICK;
    /** The light level under which Radiant leaves a wisp (decision radiant-wisps-where-light-is-low). */
    public static final int DEFAULT_RADIANT_LIGHT_THRESHOLD = 8;
    public static final ModConfigSpec.IntValue RADIANT_LIGHT_THRESHOLD;
    private static final int MAX_LIGHT = 15;
    /** The largest melt exponent the config accepts. */
    private static final double MAX_MELT_EXPONENT = 2.0;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Goo Value Derivation Settings");
        builder.push("derivation");

        BASE_VALUES_OVERRIDE_RECIPES = builder
            .comment(
                "When true, hand-keyed base values always win over recipe-derived values.",
                "When false (default), the lowest total goo count wins (LCD rule).",
                "Set to true if you are a modpack author who wants full control over base values.")
            .define("baseValuesOverrideRecipes", false);

        builder.pop();

        builder.comment("Crucible Settings");
        builder.push("crucible");

        SPARK_HEAT_TICKS = builder
            .comment("Ticks of heat a flint-and-steel spark gives a cold crucible, burning at blaze's grade.")
            .defineInRange("sparkHeatTicks", DEFAULT_SPARK_HEAT_TICKS, 1, Integer.MAX_VALUE);

        BLAZE_TICKS_PER_MB = builder
            .comment("Ticks of heat one unit of blaze goo buys; heat is spent only on ticks that melt an item.")
            .defineInRange("blazeTicksPerMb", DEFAULT_BLAZE_TICKS_PER_MB, 1, Integer.MAX_VALUE);

        BLAZE_MELT_EXPONENT = builder
            .comment("An item alone in the crucible melts in ceil(amount ^ this) ticks while the crucible burns blaze goo.")
            .defineInRange("blazeMeltExponent", DEFAULT_BLAZE_MELT_EXPONENT, 0.0, MAX_MELT_EXPONENT);

        UNSTABLE_TICKS_PER_MB = builder
            .comment("Ticks of heat one unit of unstable goo buys.")
            .defineInRange("unstableTicksPerMb", DEFAULT_UNSTABLE_TICKS_PER_MB, 1, Integer.MAX_VALUE);

        UNSTABLE_MELT_EXPONENT = builder
            .comment("An item alone in the crucible melts in ceil(amount ^ this) ticks while the crucible burns unstable goo.")
            .defineInRange("unstableMeltExponent", DEFAULT_UNSTABLE_MELT_EXPONENT, 0.0, MAX_MELT_EXPONENT);

        COMBO_DRAIN_PER_TICK = builder
            .comment("Amount of each fuel goo the crucible burns per melt tick while blaze and unstable both stand,",
                "advancing every item each tick on unstableMeltExponent's clock.")
            .defineInRange("comboDrainPerTick", DEFAULT_COMBO_DRAIN_PER_TICK, 1, Integer.MAX_VALUE);

        builder.pop();

        builder.comment("Ability Settings");
        builder.push("abilities");

        RADIANT_LIGHT_THRESHOLD = builder
            .comment("Radiant leaves a wisp of light only in air whose light level is under this.")
            .defineInRange("radiantLightThreshold", DEFAULT_RADIANT_LIGHT_THRESHOLD, 0, MAX_LIGHT + 1);

        builder.pop();

        SPEC = builder.build();
    }

    /**
     * The light level under which Radiant leaves a wisp: the configured one,
     * or the default before the config loads.
     *
     * @return the threshold
     */
    public static int radiantLightThreshold() {
        return SPEC.isLoaded() ? RADIANT_LIGHT_THRESHOLD.get() : DEFAULT_RADIANT_LIGHT_THRESHOLD;
    }
}
