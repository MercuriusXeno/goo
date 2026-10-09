package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.frost.IcebornEvents;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * The ice an Iceborn player leaves on water: magicked ice that holds while
 * an Iceborn player stands near and thaws back to water once none does
 * (decision iceborn-frozen-hearts-thaw-on-fire). It checks itself every
 * second rather than melting by light.
 */
public class IcebornIceBlock extends MagickedIceBlock {

    public static final MapCodec<IcebornIceBlock> CODEC = simpleCodec(IcebornIceBlock::new);

    /** Ticks between the ice's checks for an Iceborn player near it. */
    static final int CHECK_TICKS = 20;

    /**
     * Creates the Iceborn ice block.
     *
     * @param properties the block properties
     */
    public IcebornIceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @NonNull MapCodec<? extends IceBlock> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                           @NonNull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        level.scheduleTick(pos, this, CHECK_TICKS);
    }

    /**
     * Holds while an Iceborn player stands near, and thaws back to water once none does.
     *
     * @param state  the block state
     * @param level  the server level
     * @param pos    the block position
     * @param random the random source
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        if (IcebornEvents.icebornPlayerNear(level, pos)) {
            level.scheduleTick(pos, this, CHECK_TICKS);
        } else {
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
