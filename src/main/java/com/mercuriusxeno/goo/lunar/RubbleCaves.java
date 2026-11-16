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
 * The rubble caves of lunar's shallow zone, as a density that opens their air: low, wide caves spreading where the
 * footprint noise reads high, tallest at their hearts and pinching shut at their edges, over floors of broken
 * rubble the rubble noise heaves up and down, all through the zone's breccia.
 * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
 *
 * @param footprint the noise spreading the caves where it reads past its threshold
 * @param rubble    the noise heaving the caves' floors
 */
public record RubbleCaves(DensityFunction.NoiseHolder footprint, DensityFunction.NoiseHolder rubble)
        implements DensityFunction {

    /** The data codec of the {@code goo:rubble_caves} density function type, naming its two noises. */
    public static final MapCodec<RubbleCaves> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.NoiseHolder.CODEC.fieldOf("footprint").forGetter(RubbleCaves::footprint),
            DensityFunction.NoiseHolder.CODEC.fieldOf("rubble").forGetter(RubbleCaves::rubble)
    ).apply(instance, RubbleCaves::new));
    private static final KeyDispatchDataCodec<RubbleCaves> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    /** The noise spreading the caves. */
    public static final ResourceKey<NormalNoise.NoiseParameters> FOOTPRINT_NOISE = key("lunar_rubble_caves");
    /** The noise heaving the caves' rubble floors. */
    public static final ResourceKey<NormalNoise.NoiseParameters> RUBBLE_NOISE = key("lunar_rubble");

    /** A cave spreads where the footprint noise reads past this. */
    static final double FOOTPRINT_THRESHOLD = 0.2;
    /** A cave's floor sits near the zone's middle, heaved this far either way by its rubble. */
    static final int FLOOR_BASE_Y = (LunarDepths.CAVERN_ZONE_TOP_Y + LunarDepths.SHALLOW_ZONE_TOP_Y) / 2 - 4;
    static final double RUBBLE_HEAVE = 2.0;
    /** A cave's height at its edge, and how much taller it stands at its heart: low and wide. */
    static final double EDGE_HEIGHT = 2.0;
    static final double HEART_RISE = 5.0;
    private static final double OPEN = -1.0;
    private static final double SOLID = 1.0;

    private static ResourceKey<NormalNoise.NoiseParameters> key(String path) {
        return ResourceKey.create(Registries.NOISE, Identifier.fromNamespaceAndPath(Goo.MODID, path));
    }

    /**
     * How deep into a cave a column stands, from its footprint reading: zero at the cave's edge, one at its heart,
     * below zero outside every cave.
     *
     * @param footprintValue the footprint noise at the column
     * @return the depth into the cave
     */
    static double caveDepth(double footprintValue) {
        return (footprintValue - FOOTPRINT_THRESHOLD) / (1.0 - FOOTPRINT_THRESHOLD);
    }

    /**
     * A cave's floor at a column, from its rubble reading.
     *
     * @param rubbleValue the rubble noise at the column
     * @return the floor's top y, the last block of rubble
     */
    static int floorY(double rubbleValue) {
        return FLOOR_BASE_Y + (int) Math.round(rubbleValue * RUBBLE_HEAVE);
    }

    /**
     * A cave's roof at a column, from its floor and how deep into the cave it stands.
     *
     * @param floorY the column's floor
     * @param depth  how deep into the cave the column stands, in [0, 1]
     * @return the first solid y over the cave's air
     */
    static int roofY(int floorY, double depth) {
        return floorY + 1 + (int) Math.round(EDGE_HEIGHT + HEART_RISE * Math.sqrt(Math.min(1.0, depth)));
    }

    /**
     * The rubble caves a world's seeded noises draw.
     *
     * @param footprint the seeded footprint noise
     * @param rubble    the seeded rubble noise
     * @return the caves those noises draw
     */
    public static RubbleCaves seeded(NormalNoise footprint, NormalNoise rubble) {
        return new RubbleCaves(new DensityFunction.NoiseHolder(Holder.direct(footprint.parameters()), footprint),
                new DensityFunction.NoiseHolder(Holder.direct(rubble.parameters()), rubble));
    }

    /**
     * Whether a cave spreads under a column.
     *
     * @param x the column's x
     * @param z the column's z
     * @return true over a cave
     */
    public boolean holdsCaveAt(int x, int z) {
        return caveDepth(footprint.getValue(x, 0.0, z)) > 0.0;
    }

    /**
     * A cave's floor at a column.
     *
     * @param x the column's x
     * @param z the column's z
     * @return the floor's top y
     */
    public int floorYAt(int x, int z) {
        return floorY(rubble.getValue(x, 0.0, z));
    }

    /**
     * A cave's roof at a column.
     *
     * @param x the column's x
     * @param z the column's z
     * @return the first solid y over the cave's air
     */
    public int roofYAt(int x, int z) {
        return roofY(floorYAt(x, z), caveDepth(footprint.getValue(x, 0.0, z)));
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        int y = context.blockY();
        if (y < LunarDepths.CAVERN_ZONE_TOP_Y || y >= LunarDepths.SHALLOW_ZONE_TOP_Y) {
            return SOLID;
        }
        int x = context.blockX();
        int z = context.blockZ();
        if (!holdsCaveAt(x, z)) {
            return SOLID;
        }
        return y > floorYAt(x, z) && y < roofYAt(x, z) ? OPEN : SOLID;
    }

    @Override
    public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(output, this);
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new RubbleCaves(visitor.visitNoise(footprint), visitor.visitNoise(rubble)));
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
