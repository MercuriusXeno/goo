package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The bare rock caverns of lunar's middle zone, as a density that opens their air: large caverns swelling where a
 * three dimensional noise reads high, its height squashed so they spread wider than they stand tall, closing as they
 * near the zone's top and bottom so they stay inside it.
 * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
 * decision middle-caverns-are-bare-rock-with-no-ice-or-glass
 * decision moon-is-288-tall-half-of-today
 *
 * @param shape the noise swelling the caverns
 */
public record CrystalCaverns(Noise shape) implements DensitySampler {

    /** The data codec of the {@code goo:crystal_caverns} density function type, naming its noise. */
    public static final MapCodec<Definition> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NormalNoise.CODEC.fieldOf("shape").forGetter(Definition::shape)
    ).apply(instance, Definition::new));
    /** The noise swelling the caverns. */
    public static final ResourceKey<NormalNoise> NOISE = ResourceKey.create(Registries.NOISE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_crystal_caverns"));

    /** A cavern opens where the noise reads past this, deep inside the zone. */
    static final double THRESHOLD = 0.15;
    /** How far the threshold climbs at the zone's edges, closing the caverns there. */
    static final double EDGE_CLOSE = 0.8;
    /** The blocks inside each of the zone's edges over which the caverns close. */
    static final double EDGE_MARGIN = 7.0;
    /** The noise reads heights stretched by this, so caverns spread wider than they stand tall. */
    static final double VERTICAL_STRETCH = 4.0;
    /** How fast the density climbs from a cavern's air to its rock across the noise's threshold. */
    static final double WALL_SHARPNESS = 8.0;
    private static final double OPEN = -1.0;
    private static final double SOLID = 1.0;

    /**
     * Whether a block lies in a cavern's air, from its height and the noise there.
     *
     * @param y          the block's y
     * @param shapeValue the noise at the block
     * @return true in a cavern
     */
    static boolean isOpen(int y, double shapeValue) {
        return density(y, shapeValue) < 0.0;
    }

    /**
     * The caverns' density at a block, from its height and the noise there: below zero inside a cavern, rising
     * smoothly through zero at its wall, so the terrain's interpolation shapes the walls between cell corners.
     *
     * @param y          the block's y
     * @param shapeValue the noise at the block
     * @return the density, in [-1, 1]
     */
    static double density(int y, double shapeValue) {
        if (y < LunarDepths.WARREN_ZONE_TOP_Y || y >= LunarDepths.CAVERN_ZONE_TOP_Y) {
            return SOLID;
        }
        double fromEdge = Math.min(y - LunarDepths.WARREN_ZONE_TOP_Y, LunarDepths.CAVERN_ZONE_TOP_Y - 1 - y);
        double closing = Math.max(0.0, 1.0 - fromEdge / EDGE_MARGIN);
        double threshold = THRESHOLD + EDGE_CLOSE * closing;
        return Math.max(OPEN, Math.min(SOLID, (threshold - shapeValue) * WALL_SHARPNESS));
    }

    /**
     * The caverns a world's seeded noise draws, as its random state wires {@link #NOISE}.
     *
     * @param noise the seeded noise
     * @return the caverns that noise draws
     */
    public static CrystalCaverns seeded(Noise noise) {
        return new CrystalCaverns(noise);
    }

    /**
     * Whether a block lies in a cavern's air.
     *
     * @param x the block's x
     * @param y the block's y
     * @param z the block's z
     * @return true in a cavern
     */
    public boolean isOpenAt(int x, int y, int z) {
        return isOpen(y, shape.get(x, y * VERTICAL_STRETCH, z));
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
        int y = blockY;
        if (y < LunarDepths.WARREN_ZONE_TOP_Y || y >= LunarDepths.CAVERN_ZONE_TOP_Y) {
            return SOLID;
        }
        return density(y, shape.get(blockX, y * VERTICAL_STRETCH, blockZ));
    }

    /**
     * The density function lunar's noise settings name, compiling to the field its noises draw once the world's
     * random state seeds them.
     *
     * @param shape the noise swelling the caverns
     */
    public record Definition(Holder<NormalNoise> shape) implements DensityFunction {

        @Override
        public DensitySampler compileSampler(DensityFunction.CompileContext context) {
            return new CrystalCaverns(context.createNoiseSampler(shape));
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
