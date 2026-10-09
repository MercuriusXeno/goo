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
 * A block liquifying into an Unmake drink, standing in for the block it was
 * while it liquifies: it draws nothing itself, the drink's renderer drawing
 * the block it stands in for receding. The drink removes it once its siphon
 * is done; one the drink lost, to a server stop, turns back into the block it
 * was {@link #ORPHAN_TICKS} after its siphon was due
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
public class MeltingBlock extends BaseEntityBlock {

    /** The codec. */
    public static final MapCodec<MeltingBlock> CODEC = simpleCodec(MeltingBlock::new);
    /** Ticks past its siphon's end a melting block no drink removed waits before it turns back. */
    public static final int ORPHAN_TICKS = 40;

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
     * Turns a melting block no drink removed back into the block it was.
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        if (level.getBlockEntity(pos) instanceof MeltingBlockEntity melting
                && level.getGameTime() >= melting.siphonEnd() + ORPHAN_TICKS) {
            level.setBlock(pos, melting.original(), Block.UPDATE_ALL);
        }
    }
}
