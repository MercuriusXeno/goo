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
import net.minecraft.world.level.levelgen.RandomState;
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
public record WarrenTunnels(DensityFunction.NoiseHolder first, DensityFunction.NoiseHolder second)
        implements DensityFunction {

    /** The data codec of the {@code goo:warrens} density function type, naming its two noises. */
    public static final MapCodec<WarrenTunnels> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.NoiseHolder.CODEC.fieldOf("first").forGetter(WarrenTunnels::first),
            DensityFunction.NoiseHolder.CODEC.fieldOf("second").forGetter(WarrenTunnels::second)
    ).apply(instance, WarrenTunnels::new));
    private static final KeyDispatchDataCodec<WarrenTunnels> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
    /** The first noise the tunnels follow. */
    public static final ResourceKey<NormalNoise.NoiseParameters> FIRST_NOISE = key("lunar_warrens_first");
    /** The second noise the tunnels follow. */
    public static final ResourceKey<NormalNoise.NoiseParameters> SECOND_NOISE = key("lunar_warrens_second");

    /** How far from zero both noises may read inside a tunnel, deep in the zone: about three blocks across. */
    static final double HALF_WIDTH = 0.07;
    /** The blocks inside each of the zone's edges over which the tunnels narrow shut. */
    static final double EDGE_MARGIN = 8.0;
    private static final double OPEN = -1.0;
    private static final double SOLID = 1.0;

    private static ResourceKey<NormalNoise.NoiseParameters> key(String path) {
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
        return new WarrenTunnels(holder(randomState.getOrCreateNoise(FIRST_NOISE)),
                holder(randomState.getOrCreateNoise(SECOND_NOISE)));
    }

    private static DensityFunction.NoiseHolder holder(NormalNoise noise) {
        return new DensityFunction.NoiseHolder(Holder.direct(noise.parameters()), noise);
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
        double firstValue = first.getValue(x, y, z);
        if (Math.abs(firstValue) >= HALF_WIDTH) {
            return false;
        }
        return isOpen(y, firstValue, second.getValue(x, y, z));
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        return isOpenAt(context.blockX(), context.blockY(), context.blockZ()) ? OPEN : SOLID;
    }

    @Override
    public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(output, this);
    }

    @Override
    public DensityFunction mapChildren(DensityFunction.Visitor visitor) {
        return new WarrenTunnels(visitor.visitNoise(first), visitor.visitNoise(second));
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
