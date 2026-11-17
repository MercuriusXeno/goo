package com.mercuriusxeno.goo.lunar;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The {@code goo:crater_rays} surface rule condition: it passes a column on an ejecta ray of a large crater or a
 * giant one, read from the same seeded crater noise lunar's terrain sinks its craters from.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 *
 * @param noise the crater noise the fields roll their cells from
 */
public record CraterRaysCondition(ResourceKey<NormalNoise.NoiseParameters> noise)
        implements SurfaceRules.ConditionSource {

    /** The data codec of the condition, naming its noise. */
    public static final MapCodec<CraterRaysCondition> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceKey.codec(Registries.NOISE).fieldOf("noise").forGetter(CraterRaysCondition::noise)
    ).apply(instance, CraterRaysCondition::new));
    private static final KeyDispatchDataCodec<CraterRaysCondition> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
        return CODEC;
    }

    @Override
    public SurfaceRules.Condition apply(SurfaceRules.Context context) {
        NormalNoise craterNoise = context.randomState.getOrCreateNoise(noise);
        CraterField craters = CraterField.seeded(craterNoise);
        GiantCraterField giants = GiantCraterField.seeded(craterNoise);
        return new ColumnTest(context, (x, z) -> craters.isOnRayAt(x, z) || giants.isOnRayAt(x, z));
    }
}
