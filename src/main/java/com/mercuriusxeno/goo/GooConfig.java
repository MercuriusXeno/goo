package com.mercuriusxeno.goo;

import net.neoforged.neoforge.common.ModConfigSpec;

public class GooConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue BASE_VALUES_OVERRIDE_RECIPES;
    public static final int DEFAULT_SPARK_HEAT_TICKS = 20;
    public static final ModConfigSpec.IntValue SPARK_HEAT_TICKS;
    public static final int DEFAULT_BLAZE_TICKS_PER_MB = 4;
    public static final ModConfigSpec.IntValue BLAZE_TICKS_PER_MB;
    public static final int DEFAULT_BLAZE_MELT_RATE = 20;
    public static final ModConfigSpec.IntValue BLAZE_MELT_RATE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Goo Value Derivation Settings");
        builder.push("derivation");

        BASE_VALUES_OVERRIDE_RECIPES = builder
            .comment(
                "When true, hand-keyed base values always win over recipe-derived values.",
                "When false (default), the lowest total blob count wins (LCD rule).",
                "Set to true if you are a modpack author who wants full control over base values.")
            .define("baseValuesOverrideRecipes", false);

        builder.pop();

        builder.comment("Crucible Settings");
        builder.push("crucible");

        SPARK_HEAT_TICKS = builder
            .comment("Ticks of heat a flint-and-steel spark gives a cold crucible, burning at blaze's grade.")
            .defineInRange("sparkHeatTicks", DEFAULT_SPARK_HEAT_TICKS, 1, Integer.MAX_VALUE);

        BLAZE_TICKS_PER_MB = builder
            .comment("Ticks of heat one mB of blaze goo buys; heat is spent only on ticks that melt an item.")
            .defineInRange("blazeTicksPerMb", DEFAULT_BLAZE_TICKS_PER_MB, 1, Integer.MAX_VALUE);

        BLAZE_MELT_RATE = builder
            .comment("mB drained from the melting item per tick while the crucible burns blaze goo.")
            .defineInRange("blazeMeltRate", DEFAULT_BLAZE_MELT_RATE, 1, Integer.MAX_VALUE);

        builder.pop();

        SPEC = builder.build();
    }
}
