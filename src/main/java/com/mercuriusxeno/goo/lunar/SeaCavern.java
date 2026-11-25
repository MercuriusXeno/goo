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
 * The vast cavern over lunar's core, as a density that opens its air: the sea floor of iron core rolls under a
 * molten iron sea, rising through the sea's level as islands; anorthosite pillars stand from the floor to the roof;
 * and the open air runs from the sea's level up to a roof that rolls just under the sea zone's top. The density keeps
 * everything under the sea's level solid, and lunar's surface rule lays molten iron and iron core there through
 * {@link SeaCavernCondition}, reading the same functions.
 * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
 * decision moon-is-288-tall-half-of-today
 *
 * @param floor   the seeded noise rolling the sea floor
 * @param roof    the seeded noise rolling the roof
 * @param pillars the seeded noise standing the pillars where it reads high
 */
public record SeaCavern(Noise floor, Noise roof, Noise pillars) implements DensitySampler {

    /** The data codec of the {@code goo:sea_cavern} density function type, naming its three noises. */
    public static final MapCodec<Definition> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NormalNoise.CODEC.fieldOf("floor").forGetter(Definition::floor),
            NormalNoise.CODEC.fieldOf("roof").forGetter(Definition::roof),
            NormalNoise.CODEC.fieldOf("pillars").forGetter(Definition::pillars)
    ).apply(instance, Definition::new));

    /** The noise rolling the sea floor. */
    public static final ResourceKey<NormalNoise> FLOOR_NOISE = key("lunar_sea_floor");
    /** The noise rolling the cavern's roof. */
    public static final ResourceKey<NormalNoise> ROOF_NOISE = key("lunar_sea_roof");
    /** The noise standing the pillars. */
    public static final ResourceKey<NormalNoise> PILLAR_NOISE = key("lunar_sea_pillars");

    /** The sea floor's mean height, under the sea's level. */
    static final int FLOOR_BASE_Y = -100;
    /** How far the sea floor rolls either way; past three fifths of it the floor breaks the sea as an island. */
    static final double FLOOR_SWELL = 20.0;
    /** The roof's mean height, under the sea zone's top. */
    static final int ROOF_BASE_Y = LunarDepths.SEA_ZONE_TOP_Y - 4;
    static final double ROOF_SWELL = 4.0;
    /** A pillar stands where the pillar noise reads past this. */
    static final double PILLAR_THRESHOLD = 0.5;
    /** The density of open air in the cavern, and of everything it leaves solid. */
    private static final float OPEN = -1.0f;
    private static final float SOLID = 1.0f;

    private static ResourceKey<NormalNoise> key(String path) {
        return ResourceKey.create(Registries.NOISE, Identifier.fromNamespaceAndPath(Goo.MODID, path));
    }

    /**
     * The sea floor's height at a column, from its floor noise reading.
     *
     * @param floorValue the floor noise at the column
     * @return the floor's top y, the last block of iron core
     */
    static int floorY(double floorValue) {
        return FLOOR_BASE_Y + (int) Math.round(floorValue * FLOOR_SWELL);
    }

    /**
     * The roof's height at a column, from its roof noise reading.
     *
     * @param roofValue the roof noise at the column
     * @return the first solid y over the cavern's air
     */
    static int roofY(double roofValue) {
        return ROOF_BASE_Y + (int) Math.round(roofValue * ROOF_SWELL);
    }

    /**
     * Whether a pillar stands at a column, from its pillar noise reading.
     *
     * @param pillarValue the pillar noise at the column
     * @return true where a pillar stands from floor to roof
     */
    static boolean isPillar(double pillarValue) {
        return pillarValue > PILLAR_THRESHOLD;
    }

    /**
     * Whether a block lies in the cavern's open air: over the sea's level and the floor, under the roof, off every
     * pillar.
     *
     * @param y      the block's y
     * @param floorY the column's floor top
     * @param roofY  the column's roof
     * @param pillar whether a pillar stands at the column
     * @return true in open air
     */
    static boolean isOpenAir(int y, int floorY, int roofY, boolean pillar) {
        return !pillar && y >= LunarDepths.SEA_LEVEL_Y && y > floorY && y < roofY;
    }

    /**
     * Whether a block lies in the molten iron sea: under the sea's level, over the floor, off every pillar.
     *
     * @param y      the block's y
     * @param floorY the column's floor top
     * @param pillar whether a pillar stands at the column
     * @return true in the sea
     */
    static boolean isSea(int y, int floorY, boolean pillar) {
        return !pillar && y < LunarDepths.SEA_LEVEL_Y && y > floorY;
    }

    /**
     * The sea cavern a world's seeded noises draw.
     *
     * @param floor   the seeded floor noise
     * @param roof    the seeded roof noise
     * @param pillars the seeded pillar noise
     * @return the cavern those noises draw
     */
    public static SeaCavern seeded(Noise floor, Noise roof, Noise pillars) {
        return new SeaCavern(floor, roof, pillars);
    }

    /**
     * The sea floor's top at a column.
     *
     * @param x the column's x
     * @param z the column's z
     * @return the last y of iron core
     */
    public int floorYAt(int x, int z) {
        return floorY(floor.get(x, 0.0, z));
    }

    /**
     * The roof at a column.
     *
     * @param x the column's x
     * @param z the column's z
     * @return the first solid y over the cavern's air
     */
    public int roofYAt(int x, int z) {
        return roofY(roof.get(x, 0.0, z));
    }

    /**
     * Whether a pillar stands at a column.
     *
     * @param x the column's x
     * @param z the column's z
     * @return true where a pillar stands from floor to roof
     */
    public boolean isPillarAt(int x, int z) {
        return isPillar(pillars.get(x, 0.0, z));
    }

    /**
     * Whether a column's floor breaks the sea as an island, off every pillar.
     *
     * @param x the column's x
     * @param z the column's z
     * @return true on an island
     */
    public boolean isIslandAt(int x, int z) {
        return !isPillarAt(x, z) && floorYAt(x, z) >= LunarDepths.SEA_LEVEL_Y;
    }

    /**
     * Whether a column runs through the open sea: molten iron over its floor and air over the sea.
     *
     * @param x the column's x
     * @param z the column's z
     * @return true through the open sea
     */
    public boolean isOpenSeaAt(int x, int z) {
        return !isPillarAt(x, z) && floorYAt(x, z) < LunarDepths.SEA_LEVEL_Y - 1;
    }

    @Override
    public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
        if (blockY < LunarDepths.SEA_LEVEL_Y || blockY >= LunarDepths.SEA_ZONE_TOP_Y) {
            return SOLID;
        }
        LastColumn column = LAST_COLUMN.get().read(this, blockX, blockZ);
        return isOpenAir(blockY, column.floorY, column.roofY, column.pillar) ? OPEN : SOLID;
    }

    @Override
    public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
        DensitySampler.sampleVolumeNaive(context, outputBuffer, volume, this);
    }

    /** Each thread's last column read, so the cavern's three noises read once per column rather than per block. */
    private static final ThreadLocal<LastColumn> LAST_COLUMN = ThreadLocal.withInitial(LastColumn::new);

    /**
     * The last column a thread read, keyed by the cavern it read it from: its floor, its roof and whether a pillar
     * stands there.
     */
    private static final class LastColumn {
        private SeaCavern cavern;
        private int x;
        private int z;
        private int floorY;
        private int roofY;
        private boolean pillar;

        LastColumn read(SeaCavern reading, int columnX, int columnZ) {
            if (cavern != reading || x != columnX || z != columnZ) {
                cavern = reading;
                x = columnX;
                z = columnZ;
                floorY = reading.floorYAt(columnX, columnZ);
                roofY = reading.roofYAt(columnX, columnZ);
                pillar = reading.isPillarAt(columnX, columnZ);
            }
            return this;
        }
    }

    /**
     * The {@code goo:sea_cavern} density function lunar's noise settings name, compiling to the cavern its three
     * noises draw once the world's random state seeds them.
     *
     * @param floor   the noise rolling the sea floor
     * @param roof    the noise rolling the roof
     * @param pillars the noise standing the pillars
     */
    public record Definition(Holder<NormalNoise> floor, Holder<NormalNoise> roof, Holder<NormalNoise> pillars)
            implements DensityFunction {

        @Override
        public DensitySampler compileSampler(DensityFunction.CompileContext context) {
            return seeded(context.createNoiseSampler(floor), context.createNoiseSampler(roof),
                    context.createNoiseSampler(pillars));
        }

        @Override
        public DensityFunction rewriteChildren(DfRewriteRule rule) {
            return this;
        }

        @Override
        public Interval range() {
            return Interval.of(OPEN, SOLID);
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
