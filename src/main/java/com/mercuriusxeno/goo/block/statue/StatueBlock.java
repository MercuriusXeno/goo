package com.mercuriusxeno.goo.block.statue;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A petrified mob: a block that behaves like cobblestone, mined with a
 * pickaxe for cobblestone and the mob's experience, its block entity holding
 * the mob it was so the renderer draws that mob in stone
 * (decision petrify-stone-encasement-and-calcify-map).
 */
public class StatueBlock extends BaseEntityBlock {

    public static final MapCodec<StatueBlock> CODEC = simpleCodec(StatueBlock::new);

    /**
     * Creates the statue block.
     *
     * @param properties the block properties
     */
    public StatueBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /**
     * The block entity renderer draws the mob in stone, so the chunk mesh draws nothing.
     *
     * @param state the block state
     * @return {@link RenderShape#INVISIBLE}
     */
    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /**
     * The experience the mob would have given, dropped as the statue breaks.
     */
    @Override
    public int getExpDrop(@NonNull BlockState state, @NonNull LevelAccessor level, @NonNull BlockPos pos,
                          @Nullable BlockEntity blockEntity, @Nullable Entity breaker, @NonNull ItemStack tool) {
        return blockEntity instanceof StatueBlockEntity statue ? statue.experience() : 0;
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new StatueBlockEntity(pos, state);
    }
}
