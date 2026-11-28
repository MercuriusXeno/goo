package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * Pulse's Signal cell: the volume cell bound to Signal's goo, a redstone
 * signal source sending to every side the highest layer count of its
 * network, the Signal cells joined to it face to face, so a 15-layer cell
 * anywhere in a network makes every cell of it, out to the edge, a 15
 * strength signal. A cell placed, changed or removed works its networks'
 * highest out again, and each cell whose signal changes updates its
 * neighbours, so what the signal powers reads the new strength at once. It
 * does nothing else: it harms nothing, moves nothing and never spends a layer.
 * extend-is-dropped-for-signal-an-in-world-pulse-goo
 * pulse-goo-network-signals-at-its-highest-layer
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
        return SignalNetworks.highestAt(level, pos, this);
    }

    /**
     * Works out again the highest of each network the placed or changed cell
     * touches, joining networks it bridges.
     * pulse-goo-network-signals-at-its-highest-layer
     */
    @Override
    protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                           @NonNull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && level.getBlockState(pos).is(this)) {
            SignalNetworks.refreshAround(server, pos, this, false);
        }
    }

    /**
     * Works out again the highest of each network the removed cell left,
     * splitting networks it joined.
     * pulse-goo-network-signals-at-its-highest-layer
     */
    @Override
    protected void affectNeighborsAfterRemoval(@NonNull BlockState state, @NonNull ServerLevel level,
                                               @NonNull BlockPos pos, boolean movedByPiston) {
        SignalNetworks.refreshAround(level, pos, this, true);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
