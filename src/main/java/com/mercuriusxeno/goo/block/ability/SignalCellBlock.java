package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * Pulse's Signal cell: the volume cell bound to Signal's goo, a redstone
 * signal source sending a signal equal to its layers to every side, one at
 * one layer and fifteen at fifteen, as a redstone block sends its own. Every
 * change to its layers writes its state with a neighbour update, so what the
 * signal powers reads the new strength at once. It does nothing else: it
 * harms nothing, moves nothing and never spends a layer.
 * extend-is-dropped-for-signal-an-in-world-pulse-goo
 * signal-goo-sends-a-redstone-signal-equal-to-its-layers
 */
public class SignalCellBlock extends VolumeCellBlock {

    /**
     * Creates a Signal cell.
     *
     * @param properties the block properties
     * @param binding    the Signal binding
     */
    public SignalCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    @Override
    protected boolean isSignalSource(@NonNull BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos,
                            @NonNull Direction direction) {
        return layersOf(state);
    }
}
