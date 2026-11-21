package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The eldritch warrens between lunar's caverns and its sea, as a density that opens their air: tight tunnels twisting
 * along the curves where two three dimensional noises both read near zero, as vanilla's noodle caves run, narrowing
 * shut as they near the zone's top and bottom. Lunar's surface rule lines their floors and roofs with sculk.
 * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
 *
 * @param first  the first noise whose zero surface the tunnels follow
 * @param second the second noise whose zero surface crosses the first's
 */
public record WarrenTunnels(Noise first, Noise second) implements DensitySampler {

    /** The data codec of the {@code goo:warrens} density function type, naming its two noises. */
    public static final MapCodec<Definition> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NormalNoise.CODEC.fieldOf("first").forGetter(Definition::first),
            NormalNoise.CODEC.fieldOf("second").forGetter(Definition::second)
    ).apply(instance, Definition::new));
    /** The first noise the tunnels follow. */
    public static final ResourceKey<NormalNoise> FIRST_NOISE = key("lunar_warrens_first");
    /** The second noise the tunnels follow. */
    public static final ResourceKey<NormalNoise> SECOND_NOISE = key("lunar_warrens_second");

    /** How far from zero both noises may read inside a tunnel, deep in the zone: about three blocks across. */
    static final double HALF_WIDTH = 0.07;
    /** The blocks inside each of the zone's edges over which the tunnels narrow shut. */
    static final double EDGE_MARGIN = 8.0;
    private static final double OPEN = -1.0;
    private static final double SOLID = 1.0;

    private static ResourceKey<NormalNoise> key(String path) {
        return ResourceKey.create(Registries.NOISE, Identifier.fromNamespaceAndPath(Goo.MODID, path));
    }

    /**
     * Whether a block lies in a tunnel's air, from its height and both noises there.
     *
     * @param y           the block's y
     * @param firstValue  the first noise at the block
     * @param secondValue the second noise at the block
     * @return true in a tunnel
     */
    static boolean isOpen(int y, double firstValue, double secondValue) {
        if (y < LunarDepths.SEA_ZONE_TOP_Y || y >= LunarDepths.WARREN_ZONE_TOP_Y) {
            return false;
        }
        double fromEdge = Math.min(y - LunarDepths.SEA_ZONE_TOP_Y, LunarDepths.WARREN_ZONE_TOP_Y - 1 - y);
        double width = HALF_WIDTH * Math.min(1.0, fromEdge / EDGE_MARGIN);
        return Math.abs(firstValue) < width && Math.abs(secondValue) < width;
    }

    /**
     * The warrens a world's random state draws.
     *
     * @param randomState the world's random state
     * @return the warrens its seeded noises draw
     */
    public static WarrenTunnels seeded(RandomState randomState) {
        return new WarrenTunnels(randomState.getOrCreateNoise(FIRST_NOISE),
                randomState.getOrCreateNoise(SECOND_NOISE));
    }

    /**
     * Whether a block lies in a tunnel's air.
     *
     * @param x the block's x
     * @param y the block's y
     * @param z the block's z
     * @return true in a tunnel
     */
    public boolean isOpenAt(int x, int y, int z) {
        if (y < LunarDepths.SEA_ZONE_TOP_Y || y >= LunarDepths.WARREN_ZONE_TOP_Y) {
            return false;
        }
        double firstValue = first.get(x, y, z);
        if (Math.abs(firstValue) >= HALF_WIDTH) {
            return false;
        }
        return isOpen(y, firstValue, second.get(x, y, z));
    }

    @Override
    public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
        return (float) densityAt(blockX, blockY, blockZ);
    }

    @Override
    public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
        DensitySampler.sampleVolumeNaive(context, outputBuffer, volume, this);
    }

    private double densityAt(int blockX, int blockY, int blockZ) {
        return isOpenAt(blockX, blockY, blockZ) ? OPEN : SOLID;
    }

    /**
     * The density function lunar's noise settings name, compiling to the field its noises draw once the world's
     * random state seeds them.
     *
     * @param first the first noise whose zero surface the tunnels follow
     * @param second the second noise whose zero surface crosses the first's
     */
    public record Definition(Holder<NormalNoise> first, Holder<NormalNoise> second) implements DensityFunction {

        @Override
        public DensitySampler compileSampler(DensityFunction.CompileContext context) {
            return new WarrenTunnels(context.createNoiseSampler(first), context.createNoiseSampler(second));
        }

        @Override
        public DensityFunction rewriteChildren(DfRewriteRule rule) {
            return this;
        }

        @Override
        public Interval range() {
            return Interval.of((float) OPEN, (float) SOLID);
        }

        @Override
        public @DensityFunction.Axes int domainAxes() {
            return DensityFunction.ALL_AXES;
        }

        @Override
        public MapCodec<Definition> codec() {
            return DATA_CODEC;
        }
    }
}
