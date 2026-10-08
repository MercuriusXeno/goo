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
 * and synced to the clients that draw it melting; how far it has melted and
 * the work it takes; and the game time the unmake last worked it. Worked, it
 * melts a step a tick; left, it runs back down a step a tick, re-solidifying
 * as smoothly as it melted, and worked again it picks up where it stands.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public class MeltingBlockEntity extends GooSyncedBlockEntity {

    private static final String TAG_ORIGINAL = "original";
    private static final String TAG_MELTED = "melted";
    private static final String TAG_NEEDED = "needed";

    private BlockState original = Blocks.AIR.defaultBlockState();
    private long lastWorked;
    private float melted;
    private int needed = 1;

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
     * Melts the block one step for this tick's work, once a tick however many
     * stream ticks the tick holds.
     *
     * @param workNeeded the work the block takes to melt
     * @param gameTime   the game time
     * @return the work done so far, at least 1
     */
    public int addWork(int workNeeded, long gameTime) {
        needed = Math.max(1, workNeeded);
        if (lastWorked != gameTime) {
            melted = Math.min(1f, melted + 1f / needed);
            lastWorked = gameTime;
            setChanged();
        }
        return Math.max(1, Math.round(melted * needed));
    }

    /**
     * Runs the melt back one step, as a tick left unworked does.
     *
     * @return how far the block is still melted, 0 once solid again
     */
    public float resolidify() {
        melted = Math.max(0f, melted - 1f / needed);
        setChanged();
        return melted;
    }

    /**
     * @return how far the block has melted, 0 solid to 1 gone
     */
    public float melted() {
        return melted;
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
        melted = input.getFloatOr(TAG_MELTED, 0f);
        needed = Math.max(1, input.getIntOr(TAG_NEEDED, 1));
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store(TAG_ORIGINAL, BlockState.CODEC, original);
        output.putFloat(TAG_MELTED, melted);
        output.putInt(TAG_NEEDED, needed);
    }
}
