package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.frost.RimeFrozenBlocks;
import com.mercuriusxeno.goo.ability.program.RimeStep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * Frost's Rime cell: the volume cell bound to Rime's endothermic goo, which
 * on each scheduled tick freezes the water and lava sources within the reach
 * Rime's step names, then ticks again after the step's chill ticks, so the
 * cell keeps what is around it frozen for as long as it stands.
 * rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless
 * iceborn-is-dropped-for-rime-a-frost-goo-that-keeps-what-is-around-it-frozen
 */
public class RimeCellBlock extends VolumeCellBlock {

    /**
     * Creates a Rime cell.
     *
     * @param properties the block properties
     * @param binding    the Rime binding
     */
    public RimeCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * Pours as any cell does, then, where the cell still stands, freezes what
     * is in its reach and keeps a tick coming, a pending pour's or the next
     * chill's.
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        super.tick(state, level, pos, random);
        if (!level.getBlockState(pos).is(this)) {
            return;
        }
        RimeStep.in(layingProgram(level)).ifPresent(rime -> {
            RimeFrozenBlocks.freezeAround(level, pos, rime.reach());
            if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
                level.scheduleTick(pos, this, rime.chillTicks());
            }
        });
    }
}
