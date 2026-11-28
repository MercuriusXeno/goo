package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.ability.pulse.ZapDevice;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Arrays;
import java.util.Optional;

/**
 * The wireless emitter: alien tech in a simple entityless block that takes a signal and beams it to the closest block
 * within 16 Manhattan that takes one, at the level it takes, a redstone relay. When the level reaching it changes it
 * lets its old receiver go and scans once for the closest receiver, standing a wireless signal at its level in that
 * receiver's first empty neighbour; it never scans per tick. Its state holds the level it transmits, and the scan's
 * fixed order finds the same receiver again, so it clears its own source when its input changes or it is broken.
 * Another emitter takes a signal too, so emitters chain; an emitter never picks one that is transmitting, so two never
 * power each other in a loop, and never stands its source beside itself, so it never powers itself.
 * decision wireless-emitter-beams-its-signal-to-the-closest-receiver
 */
public class WirelessEmitterBlock extends Block {

    /** The level the emitter transmits, 0 while it takes no signal. */
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private static final int PLACE_FLAGS = Block.UPDATE_ALL;

    /**
     * Creates the emitter, transmitting nothing.
     *
     * @param properties the block properties
     */
    public WirelessEmitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    @Override
    protected void neighborChanged(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                   @NonNull Block neighbor, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, orientation, movedByPiston);
        if (level instanceof ServerLevel server) {
            retransmit(server, pos, state);
        }
    }

    @Override
    protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                           @NonNull BlockState old, boolean movedByPiston) {
        super.onPlace(state, level, pos, old, movedByPiston);
        if (level instanceof ServerLevel server && !old.is(this)) {
            retransmit(server, pos, state);
        }
    }

    /** An emitter taken from the world while it transmits lets its receiver go. */
    @Override
    protected void affectNeighborsAfterRemoval(@NonNull BlockState state, @NonNull ServerLevel level,
                                               @NonNull BlockPos pos, boolean movedByPiston) {
        if (state.getValue(POWER) > 0) {
            release(level, pos);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    /**
     * Matches what the emitter transmits to the level reaching it: when that level changed, it lets its old receiver
     * go and beams the new level to the closest receiver.
     *
     * @param level the server level
     * @param pos   the emitter
     * @param state its state
     */
    private void retransmit(ServerLevel level, BlockPos pos, BlockState state) {
        int input = level.getBestNeighborSignal(pos);
        int held = state.getValue(POWER);
        if (input == held) {
            return;
        }
        if (held > 0) {
            release(level, pos);
        }
        level.setBlock(pos, state.setValue(POWER, input), Block.UPDATE_CLIENTS);
        if (input > 0) {
            transmit(level, pos, input);
        }
    }

    private static void transmit(ServerLevel level, BlockPos emitter, int input) {
        WirelessBeam.closest(emitter, at -> takesSignal(level, at) && sourceCell(level, emitter, at).isPresent())
                .flatMap(receiver -> sourceCell(level, emitter, receiver))
                .ifPresent(cell -> level.setBlock(cell, GooBlocks.WIRELESS_SIGNAL.get().defaultBlockState()
                        .setValue(WirelessSignalBlock.POWER, input), PLACE_FLAGS));
    }

    /**
     * Clears the source the emitter stands beside its receiver, found again by the scan's fixed order.
     *
     * @param level   the server level
     * @param emitter the emitter
     */
    private static void release(ServerLevel level, BlockPos emitter) {
        receiverOf(level, emitter).flatMap(receiver -> heldCell(level, emitter, receiver))
                .ifPresent(cell -> level.removeBlock(cell, false));
    }

    /**
     * The receiver a transmitting emitter powers: the closest block that takes a signal with the emitter's source
     * beside it, found by the scan's fixed order, on the server to clear its source and on the client to draw its beam.
     *
     * @param level   the level
     * @param emitter the emitter
     * @return the receiver, or empty where the emitter powers none
     */
    public static Optional<BlockPos> receiverOf(BlockGetter level, BlockPos emitter) {
        return WirelessBeam.closest(emitter, at -> takesSignal(level, at) && heldCell(level, emitter, at).isPresent());
    }

    /**
     * Whether a position holds a block that takes a signal, as Zap reads one, and is no transmitting emitter.
     *
     * @param level the level
     * @param at    the position
     * @return whether the emitter may pick it
     */
    static boolean takesSignal(BlockGetter level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        boolean transmitting = state.getBlock() instanceof WirelessEmitterBlock && state.getValue(POWER) > 0;
        return !transmitting && ZapDevice.ticks(state);
    }

    /**
     * The receiver's first empty neighbour, in a fixed order, that does not touch the emitter.
     *
     * @param level    the server level
     * @param emitter  the emitter
     * @param receiver the receiver
     * @return the cell its source stands in, or empty where it has none free
     */
    private static Optional<BlockPos> sourceCell(ServerLevel level, BlockPos emitter, BlockPos receiver) {
        return Arrays.stream(Direction.values()).map(receiver::relative)
                .filter(cell -> !WirelessBeam.touching(cell, emitter) && level.getBlockState(cell).isAir())
                .findFirst();
    }

    /**
     * The receiver's first neighbour, in a fixed order, that holds a wireless signal and does not touch the emitter.
     *
     * @param level    the level
     * @param emitter  the emitter
     * @param receiver the receiver
     * @return the cell its source stands in, or empty where none stands
     */
    private static Optional<BlockPos> heldCell(BlockGetter level, BlockPos emitter, BlockPos receiver) {
        return Arrays.stream(Direction.values()).map(receiver::relative)
                .filter(cell -> !WirelessBeam.touching(cell, emitter)
                        && level.getBlockState(cell).is(GooBlocks.WIRELESS_SIGNAL.get()))
                .findFirst();
    }
}
