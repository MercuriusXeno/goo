package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooPlacementModifiers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;

/**
 * The {@code goo:dark_side_count} placement modifier: a feature's attempts for each chunk, at the count its JSON names
 * for the side of the moon the chunk stands on, so void and xeno blocks concentrate on the dark side.
 * decision dark-side-is-an-endless-night-of-eldritch-rich-in-void-and-xeno
 */
public final class DarkSideCount extends RepeatingPlacement {

    private static final int MOST_ATTEMPTS = 256;
    /** The data codec: a count for each side. */
    public static final MapCodec<DarkSideCount> CODEC = Sided.mapCodec(Codec.intRange(0, MOST_ATTEMPTS))
            .xmap(DarkSideCount::new, DarkSideCount::counts);

    private final Sided<Integer> counts;

    private DarkSideCount(Sided<Integer> counts) {
        this.counts = counts;
    }

    private Sided<Integer> counts() {
        return counts;
    }

    @Override
    protected int count(RandomSource random, BlockPos origin) {
        return counts.at(origin.getX());
    }

    @Override
    public PlacementModifierType<?> type() {
        return GooPlacementModifiers.DARK_SIDE_COUNT.get();
    }
}
