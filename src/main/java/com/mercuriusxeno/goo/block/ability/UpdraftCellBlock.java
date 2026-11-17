package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.UpdraftStep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * Typhoon's Updraft cell: the volume cell bound to Updraft's goo, which on
 * each of its ticks lifts every entity standing in the column above it, a
 * block over its top for each of its layers, as wide and as fast as
 * Updraft's step names, then ticks again on the next tick. Lifting spends no
 * layer: Goop, Slick and Updraft move you for free, where the goos that
 * spend their layers do something permanent.
 * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
 * updraft-goo-lifts-one-block-per-layer
 * updraft-goo-is-semi-permanent-and-does-not-deplete
 */
public class UpdraftCellBlock extends VolumeCellBlock {

    /** The ticks between a cell's lifts: every tick, so a rider rises smoothly. */
    private static final int LIFT_TICKS = 1;

    /**
     * Creates an Updraft cell.
     *
     * @param properties the block properties
     * @param binding    the Updraft binding
     */
    public UpdraftCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * Pours as any cell does, then, where the cell still stands, lifts what
     * stands in its column and keeps a tick coming, a pending pour's or the
     * next lift's.
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        super.tick(state, level, pos, random);
        BlockState poured = level.getBlockState(pos);
        if (!poured.is(this)) {
            return;
        }
        UpdraftStep.in(layingProgram(level)).ifPresent(updraft -> updraft.liftAbove(level, pos, layersOf(poured)));
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, LIFT_TICKS);
        }
    }
}
