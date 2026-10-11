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
 * A block liquifying into an Unmake drink: the block it stands in for, saved
 * with it and synced to the clients that draw it receding, and the game time
 * its siphon is done.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public class MeltingBlockEntity extends GooSyncedBlockEntity {

    private static final String TAG_ORIGINAL = "original";
    private static final String TAG_SIPHON_END = "siphon_end";

    private BlockState original = Blocks.AIR.defaultBlockState();
    private long siphonEnd;

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
     * Marks the game time the block's siphon is done.
     *
     * @param end the game time
     */
    public void siphonUntil(long end) {
        siphonEnd = end;
        setChanged();
    }

    /**
     * @return the game time the block's siphon is done
     */
    public long siphonEnd() {
        return siphonEnd;
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        original = input.read(TAG_ORIGINAL, BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState());
        siphonEnd = input.getLongOr(TAG_SIPHON_END, 0L);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store(TAG_ORIGINAL, BlockState.CODEC, original);
        output.putLong(TAG_SIPHON_END, siphonEnd);
    }
}
