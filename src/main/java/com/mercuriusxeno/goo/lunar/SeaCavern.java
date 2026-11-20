package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The vast cavern over lunar's core, as a density that opens its air: the sea floor of iron core rolls under a
 * molten iron sea, rising through the sea's level as islands; anorthosite pillars stand from the floor to the roof;
 * and the open air runs from the sea's level up to a roof that rolls just under the sea zone's top. The density keeps
 * everything under the sea's level solid, and lunar's surface rule lays molten iron and iron core there through
 * {@link SeaCavernCondition}, reading the same functions.
 * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
 *
 * @param floor   the noise rolling the sea floor
 * @param roof    the noise rolling the roof
 * @param pillars the noise standing the pillars where it reads high
 */
public record SeaCavern(DensityFunction.NoiseHolder floor, DensityFunction.NoiseHolder roof,
        DensityFunction.NoiseHolder pillars) implements DensityFunction {

    /** The data codec of the {@code goo:sea_cavern} density function type, naming its three noises. */
    public static final MapCodec<SeaCavern> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.NoiseHolder.CODEC.fieldOf("floor").forGetter(SeaCavern::floor),
            DensityFunction.NoiseHolder.CODEC.fieldOf("roof").forGetter(SeaCavern::roof),
            DensityFunction.NoiseHolder.CODEC.fieldOf("pillars").forGetter(SeaCavern::pillars)
    ).apply(instance, SeaCavern::new));
    private static final KeyDispatchDataCodec<SeaCavern> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    /** The noise rolling the sea floor. */
    public static final ResourceKey<NormalNoise.NoiseParameters> FLOOR_NOISE = key("lunar_sea_floor");
    /** The noise rolling the cavern's roof. */
    public static final ResourceKey<NormalNoise.NoiseParameters> ROOF_NOISE = key("lunar_sea_roof");
    /** The noise standing the pillars. */
    public static final ResourceKey<NormalNoise.NoiseParameters> PILLAR_NOISE = key("lunar_sea_pillars");

    /** The sea floor's mean height, under the sea's level. */
    static final int FLOOR_BASE_Y = -200;
    /** How far the sea floor rolls either way; past three fifths of it the floor breaks the sea as an island. */
    static final double FLOOR_SWELL = 40.0;
    /** The roof's mean height, under the sea zone's top. */
    static final int ROOF_BASE_Y = LunarDepths.SEA_ZONE_TOP_Y - 8;
    static final double ROOF_SWELL = 8.0;
    /** A pillar stands where the pillar noise reads past this. */
    static final double PILLAR_THRESHOLD = 0.5;
    /** The density of open air in the cavern, and of everything it leaves solid. */
    private static final double OPEN = -1.0;
    private static final double SOLID = 1.0;

    private static ResourceKey<NormalNoise.NoiseParameters> key(String path) {
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
    public static SeaCavern seeded(NormalNoise floor, NormalNoise roof, NormalNoise pillars) {
        return new SeaCavern(holder(floor), holder(roof), holder(pillars));
    }

    private static DensityFunction.NoiseHolder holder(NormalNoise noise) {
        return new DensityFunction.NoiseHolder(Holder.direct(noise.parameters()), noise);
    }

    /**
     * The sea floor's top at a column.
     *
     * @param x the column's x
     * @param z the column's z
     * @return the last y of iron core
     */
    public int floorYAt(int x, int z) {
        return floorY(floor.getValue(x, 0.0, z));
    }

    /**
     * The roof at a column.
     *
     * @param x the column's x
     * @param z the column's z
     * @return the first solid y over the cavern's air
     */
    public int roofYAt(int x, int z) {
        return roofY(roof.getValue(x, 0.0, z));
    }

    /**
     * Whether a pillar stands at a column.
     *
     * @param x the column's x
     * @param z the column's z
     * @return true where a pillar stands from floor to roof
     */
    public boolean isPillarAt(int x, int z) {
        return isPillar(pillars.getValue(x, 0.0, z));
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
    public double compute(DensityFunction.FunctionContext context) {
        int y = context.blockY();
        if (y < LunarDepths.SEA_LEVEL_Y || y >= LunarDepths.SEA_ZONE_TOP_Y) {
            return SOLID;
        }
        LastColumn column = LAST_COLUMN.get().read(this, context.blockX(), context.blockZ());
        return isOpenAir(y, column.floorY, column.roofY, column.pillar) ? OPEN : SOLID;
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

    @Override
    public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(output, this);
    }

    @Override
    public DensityFunction mapChildren(DensityFunction.Visitor visitor) {
        return new SeaCavern(visitor.visitNoise(floor), visitor.visitNoise(roof),
                visitor.visitNoise(pillars));
    }

    @Override
    public double minValue() {
        return OPEN;
    }

    @Override
    public double maxValue() {
        return SOLID;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
