package com.mercuriusxeno.goo.block.ability;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

/**
 * The full-power source Zap stands in its landing cell beside a block with
 * no toggle of its own: it powers what it touches as a redstone block does,
 * then removes itself, so the touched dust, lamp or piston sees one pulse.
 * It draws nothing and nothing collides with it.
 * zap-ticks-the-device-and-stuns
 */
public class ZapPulseBlock extends Block {

    public static final MapCodec<ZapPulseBlock> CODEC = simpleCodec(ZapPulseBlock::new);

    /** Redstone's full signal. */
    private static final int FULL_SIGNAL = 15;
    /** Ticks the pulse stands: long enough for dust to carry it and a piston to see it. */
    static final int PULSE_TICKS = 2;

    /**
     * Creates the block.
     *
     * @param properties the block properties
     */
    public ZapPulseBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected boolean isSignalSource(@NonNull BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos,
                            @NonNull Direction direction) {
        return FULL_SIGNAL;
    }

    @Override
    protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                           @NonNull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        level.scheduleTick(pos, this, PULSE_TICKS);
    }

    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        level.removeBlock(pos, false);
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return Shapes.empty();
    }
}
