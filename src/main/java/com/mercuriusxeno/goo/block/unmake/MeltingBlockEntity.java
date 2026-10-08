package com.mercuriusxeno.goo.block.unmake;

import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.GooSyncedBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;

/**
 * The block an unmake is melting: the block it stands in for, saved with it
 * and synced to the clients that draw its sagging goo copy, and the game time
 * the unmake last worked it, which says when to turn it back.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public class MeltingBlockEntity extends GooSyncedBlockEntity {

    private static final String TAG_ORIGINAL = "original";

    private BlockState original = Blocks.AIR.defaultBlockState();
    private long lastWorked;

    /**
     * Creates the melting block's entity.
     *
     * @param pos   the block's position
     * @param state the melting block's state
     */
    public MeltingBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.MELTING_BLOCK.get(), pos, state);
    }

    /**
     * Holds the block this one stands in for, then saves and syncs it.
     *
     * @param standsFor the block the unmake is melting
     */
    public void hold(BlockState standsFor) {
        this.original = standsFor;
        setChanged();
        BlockEntitySync.markDirtyAndSync(this);
    }

    /**
     * @return the block this one stands in for
     */
    public BlockState original() {
        return original;
    }

    /**
     * Marks the unmake working the block now.
     *
     * @param gameTime the game time
     */
    public void work(long gameTime) {
        lastWorked = gameTime;
    }

    /**
     * @return the game time the unmake last worked the block
     */
    public long lastWorked() {
        return lastWorked;
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        original = input.read(TAG_ORIGINAL, BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState());
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store(TAG_ORIGINAL, BlockState.CODEC, original);
    }
}
