package com.mercuriusxeno.goo.block.unmake;

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
 * A block an unmake is melting, standing in for the block it was while its
 * goo copy sags in place: it draws nothing itself, its renderer drawing the
 * sag, and turns back into the block it was once the unmake leaves it for
 * {@link #REVERT_TICKS} (decision unmake-waves-dissolve-by-crucible-cost).
 */
public class MeltingBlock extends BaseEntityBlock {

    /** The codec. */
    public static final MapCodec<MeltingBlock> CODEC = simpleCodec(MeltingBlock::new);
    /** Ticks a melting block stands unworked before it turns back into the block it was, a second. */
    public static final int REVERT_TICKS = 20;

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
     * Turns the block back into the one it stands in for once the unmake has
     * left it long enough, or checks again later while it is still being worked.
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof MeltingBlockEntity melting)) {
            return;
        }
        long idle = level.getGameTime() - melting.lastWorked();
        if (idle >= REVERT_TICKS) {
            level.setBlock(pos, melting.original(), Block.UPDATE_ALL);
        } else {
            level.scheduleTick(pos, this, (int) (REVERT_TICKS - idle));
        }
    }
}
