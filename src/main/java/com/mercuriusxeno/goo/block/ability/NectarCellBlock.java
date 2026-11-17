package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.NectarGooStep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * Jelly's Nectar cell: the volume cell bound to Nectar's goo, which on each
 * of its ticks draws the mobs about it to eat or fight over it, a layer for
 * each feeding, as NectarFeeds runs it, then ticks again a quarter second
 * on.
 * nectar-goo-keeps-feeds-effects-as-a-universal-food
 * each-feeding-eats-a-layer-of-nectar
 */
public class NectarCellBlock extends VolumeCellBlock {

    /** The ticks between a cell's feedings: often enough that a mob reaching it eats at once. */
    private static final int FEED_TICKS = 5;

    /**
     * Creates a Nectar cell.
     *
     * @param properties the block properties
     * @param binding    the Nectar binding
     */
    public NectarCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * Pours as any cell does, then, where the cell still stands, feeds the
     * mobs about it and keeps a tick coming, a pending pour's or the next
     * feeding's.
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        super.tick(state, level, pos, random);
        BlockState poured = level.getBlockState(pos);
        if (!poured.is(this)) {
            return;
        }
        NectarGooStep.in(layingProgram(level)).ifPresent(nectar -> NectarFeeds.feedAround(level, pos, poured, nectar));
        if (level.getBlockState(pos).is(this) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, FEED_TICKS);
        }
    }
}
