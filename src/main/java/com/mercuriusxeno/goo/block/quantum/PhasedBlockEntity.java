package com.mercuriusxeno.goo.block.quantum;

import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.GooSyncedBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;

/**
 * A block out of phase: the block it stood as, held until the game time it
 * steps back into phase, then put back with whatever stands inside pushed
 * clear. Saved with the chunk and synced to clients, which draw the held
 * block as a ghost.
 * portable-hole-phases-blocks-for-a-while
 */
public class PhasedBlockEntity extends GooSyncedBlockEntity {

    private static final String TAG_HELD = "held";
    private static final String TAG_RESTORES_AT = "restores_at";

    private BlockState held = Blocks.AIR.defaultBlockState();
    private long restoresAt;

    /**
     * Creates the phased block's entity.
     *
     * @param pos   its position
     * @param state its block state
     */
    public PhasedBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.PHASED_BLOCK.get(), pos, state);
    }

    /**
     * Holds the block the cell stood as until a game time, then saves and syncs it.
     *
     * @param original   the block the cell stood as
     * @param restoresAt the game time the block steps back into phase
     */
    public void hold(BlockState original, long restoresAt) {
        this.held = original;
        this.restoresAt = restoresAt;
        setChanged();
        BlockEntitySync.markDirtyAndSync(this);
    }

    /**
     * @return the block the cell stood as before it went out of phase
     */
    public BlockState held() {
        return held;
    }

    /**
     * @return the game time the block steps back into phase
     */
    public long restoresAt() {
        return restoresAt;
    }

    /**
     * Puts the held block back once its time comes.
     *
     * @param level the level
     * @param pos   the cell
     * @param state the phased block's state
     * @param be    this block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, PhasedBlockEntity be) {
        if (level instanceof ServerLevel server && server.getGameTime() >= be.restoresAt) {
            restore(server, pos, be.held);
        }
    }

    /**
     * Puts a held block back in its cell and pushes whatever stands inside clear of it.
     *
     * @param level the level
     * @param pos   the cell
     * @param held  the block to put back
     */
    public static void restore(ServerLevel level, BlockPos pos, BlockState held) {
        level.setBlock(pos, held, Block.UPDATE_ALL);
        PhaseClearance.pushClear(level, pos);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        held = input.read(TAG_HELD, BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState());
        restoresAt = input.getLongOr(TAG_RESTORES_AT, 0L);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store(TAG_HELD, BlockState.CODEC, held);
        output.putLong(TAG_RESTORES_AT, restoresAt);
    }
}
