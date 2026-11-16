package com.mercuriusxeno.goo.lunar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The {@code goo:crater_band} surface rule condition: it passes a column some crater reaches within a band of its
 * radii, read from the same seeded crater noise lunar's terrain sinks its craters from, so a rule it gates lays its
 * block on the craters the world drew. A band from zero to half a radius is a crater's floor.
 * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
 *
 * @param noise    the crater noise the field rolls its cells from
 * @param minReach the band's inner edge, in radii, inclusive
 * @param maxReach the band's outer edge, in radii, exclusive
 */
public record CraterBandCondition(ResourceKey<NormalNoise.NoiseParameters> noise, double minReach, double maxReach)
        implements SurfaceRules.ConditionSource {

    /** The data codec of the condition, naming its noise and its band. */
    public static final MapCodec<CraterBandCondition> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceKey.codec(Registries.NOISE).fieldOf("noise").forGetter(CraterBandCondition::noise),
            Codec.DOUBLE.fieldOf("min_reach").forGetter(CraterBandCondition::minReach),
            Codec.DOUBLE.fieldOf("max_reach").forGetter(CraterBandCondition::maxReach)
    ).apply(instance, CraterBandCondition::new));
    private static final KeyDispatchDataCodec<CraterBandCondition> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
        return CODEC;
    }

    @Override
    public SurfaceRules.Condition apply(SurfaceRules.Context context) {
        return new ColumnTest(context, CraterField.seeded(context.randomState.getOrCreateNoise(noise)));
    }

    /**
     * Tests the column the surface rules stand on, reading the field once per column as the rules walk down it.
     */
    private final class ColumnTest implements SurfaceRules.Condition {
        private final SurfaceRules.Context context;
        private final CraterField craters;
        private int testedX = Integer.MIN_VALUE;
        private int testedZ = Integer.MIN_VALUE;
        private boolean inBand;

        private ColumnTest(SurfaceRules.Context context, CraterField craters) {
            this.context = context;
            this.craters = craters;
        }

        @Override
        public boolean test() {
            if (context.blockX != testedX || context.blockZ != testedZ) {
                testedX = context.blockX;
                testedZ = context.blockZ;
                inBand = craters.reachesBandAt(testedX, testedZ, minReach, maxReach);
            }
            return inBand;
        }
    }
}
