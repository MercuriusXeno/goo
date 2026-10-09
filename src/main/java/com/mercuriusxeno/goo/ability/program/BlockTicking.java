package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Runs a block entity's own ticker outside the level's tick, as Tick hastens
 * a machine: every ticking block entity counts, goo's and vanilla's alike.
 * tick-channel-marches-squares-on-the-face
 */
public final class BlockTicking {

    private BlockTicking() {
    }

    /**
     * Runs the server ticker of the block entity at a position a number of
     * times, reading the block state afresh each time, since a tick may
     * change it (a furnace lighting).
     *
     * @param level the level
     * @param pos   the block
     * @param times how many ticks to run
     * @return the ticks run: none where no ticking block entity stands
     */
    public static int tickBlockEntity(Level level, BlockPos pos, int times) {
        int ran = 0;
        for (int tick = 0; tick < times; tick++) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null || blockEntity.isRemoved() || !runTicker(level, pos, blockEntity)) {
                break;
            }
            ran++;
        }
        return ran;
    }

    @SuppressWarnings("unchecked") // a block entity's type is the type of its own class
    private static <T extends BlockEntity> boolean runTicker(Level level, BlockPos pos, T blockEntity) {
        BlockState state = level.getBlockState(pos);
        BlockEntityType<T> type = (BlockEntityType<T>) blockEntity.getType();
        BlockEntityTicker<T> ticker = state.getTicker(level, type);
        if (ticker == null) {
            return false;
        }
        ticker.tick(level, pos, state, blockEntity);
        return true;
    }
}
