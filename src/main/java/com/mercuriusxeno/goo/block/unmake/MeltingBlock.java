package com.mercuriusxeno.goo.block.unmake;

import com.mercuriusxeno.goo.network.ChunkWatchers;
import com.mercuriusxeno.goo.network.UnmakePayload;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * A block an unmake is melting, standing in for the block it was while it
 * melts in place: it draws nothing itself, its renderer drawing the melt.
 * Once the unmake leaves it for {@link #IDLE_TICKS}, it re-solidifies a step
 * a tick, as smoothly as it melted, and turns back into the block it was once
 * fully solid (decision unmake-waves-dissolve-by-crucible-cost).
 */
public class MeltingBlock extends BaseEntityBlock {

    /** The codec. */
    public static final MapCodec<MeltingBlock> CODEC = simpleCodec(MeltingBlock::new);
    /** Ticks a melting block stands unworked before it starts to re-solidify, past the stream's batching. */
    public static final int IDLE_TICKS = 3;

    /**
     * Creates the melting block.
     *
     * @param properties the block's properties
     */
    public MeltingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new MeltingBlockEntity(pos, state);
    }

    /**
     * Re-solidifies a block the unmake has left, a step a tick, showing each
     * step to its viewers, and turns it back into the block it was once solid.
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof MeltingBlockEntity melting)) {
            return;
        }
        if (level.getGameTime() - melting.lastWorked() >= IDLE_TICKS) {
            float melted = melting.resolidify();
            ChunkWatchers.send(level, pos, new UnmakePayload(pos, melted));
            if (melted <= 0f) {
                level.setBlock(pos, melting.original(), Block.UPDATE_ALL);
                return;
            }
        }
        level.scheduleTick(pos, this, 1);
    }
}
