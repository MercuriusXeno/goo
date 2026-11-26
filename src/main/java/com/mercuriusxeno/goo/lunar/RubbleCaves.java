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
 * The rubble caves of lunar's shallow zone, as a density that opens their air: low, wide caves spreading where the
 * footprint noise reads high, tallest at their hearts and pinching shut at their edges, over floors of broken
 * rubble the rubble noise heaves up and down, all through the zone's breccia.
 * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
 * decision moon-is-288-tall-half-of-today
 *
 * @param footprint the noise spreading the caves where it reads past its threshold
 * @param rubble    the noise heaving the caves' floors
 */
public record RubbleCaves(Noise footprint, Noise rubble) implements DensitySampler {

    /** The data codec of the {@code goo:rubble_caves} density function type, naming its two noises. */
    public static final MapCodec<Definition> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NormalNoise.CODEC.fieldOf("footprint").forGetter(Definition::footprint),
            NormalNoise.CODEC.fieldOf("rubble").forGetter(Definition::rubble)
    ).apply(instance, Definition::new));

    /** The noise spreading the caves. */
    public static final ResourceKey<NormalNoise> FOOTPRINT_NOISE = key("lunar_rubble_caves");
    /** The noise heaving the caves' rubble floors. */
    public static final ResourceKey<NormalNoise> RUBBLE_NOISE = key("lunar_rubble");

    /** A cave spreads where the footprint noise reads past this. */
    static final double FOOTPRINT_THRESHOLD = 0.2;
    /** A cave's floor sits near the zone's middle, heaved this far either way by its rubble. */
    static final int FLOOR_BASE_Y = (LunarDepths.CAVERN_ZONE_TOP_Y + LunarDepths.SHALLOW_ZONE_TOP_Y) / 2 - 2;
    static final double RUBBLE_HEAVE = 1.0;
    /** A cave's height at its edge, and how much taller it stands at its heart: low and wide. */
    static final double EDGE_HEIGHT = 1.0;
    static final double HEART_RISE = 2.5;
    private static final double OPEN = -1.0;
    private static final double SOLID = 1.0;

    private static ResourceKey<NormalNoise> key(String path) {
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
    public static RubbleCaves seeded(Noise footprint, Noise rubble) {
        return new RubbleCaves(footprint,
                rubble);
    }

    /**
     * Whether a cave spreads under a column.
     *
     * @param x the column's x
     * @param z the column's z
     * @return true over a cave
     */
    public boolean holdsCaveAt(int x, int z) {
        return caveDepth(footprint.get(x, 0.0, z)) > 0.0;
    }

    /**
     * A cave's floor at a column.
     *
     * @param x the column's x
     * @param z the column's z
     * @return the floor's top y
     */
    public int floorYAt(int x, int z) {
        return floorY(rubble.get(x, 0.0, z));
    }

    /**
     * A cave's roof at a column.
     *
     * @param x the column's x
     * @param z the column's z
     * @return the first solid y over the cave's air
     */
    public int roofYAt(int x, int z) {
        return roofY(floorYAt(x, z), caveDepth(footprint.get(x, 0.0, z)));
    }

    /**
     * Opens a cave's air between its floor and roof; solid everywhere else. Each thread keeps the last column's floor
     * and roof, so the footprint and rubble noises read once per column rather than once per block.
     */
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
        if (y < LunarDepths.CAVERN_ZONE_TOP_Y || y >= LunarDepths.SHALLOW_ZONE_TOP_Y) {
            return SOLID;
        }
        LastColumn column = LAST_COLUMN.get().read(this, blockX, blockZ);
        return y > column.floorY && y < column.roofY ? OPEN : SOLID;
    }

    /** Each thread's last column read and the cave over it. */
    private static final ThreadLocal<LastColumn> LAST_COLUMN = ThreadLocal.withInitial(LastColumn::new);

    /**
     * The last column a thread read, keyed by the caves it read it from: the cave's floor and roof there, or a roof
     * under the floor where no cave spreads.
     */
    private static final class LastColumn {
        private RubbleCaves caves;
        private int x;
        private int z;
        private int floorY;
        private int roofY;

        LastColumn read(RubbleCaves reading, int columnX, int columnZ) {
            if (caves != reading || x != columnX || z != columnZ) {
                caves = reading;
                x = columnX;
                z = columnZ;
                boolean cave = reading.holdsCaveAt(columnX, columnZ);
                floorY = cave ? reading.floorYAt(columnX, columnZ) : 0;
                roofY = cave ? reading.roofYAt(columnX, columnZ) : 0;
            }
            return this;
        }
    }

    /**
     * The density function lunar's noise settings name, compiling to the field its noises draw once the world's
     * random state seeds them.
     *
     * @param footprint the noise spreading the caves where it reads past its threshold
     * @param rubble the noise heaving the caves' floors
     */
    public record Definition(Holder<NormalNoise> footprint, Holder<NormalNoise> rubble) implements DensityFunction {

        @Override
        public DensitySampler compileSampler(DensityFunction.CompileContext context) {
            return new RubbleCaves(context.createNoiseSampler(footprint), context.createNoiseSampler(rubble));
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
