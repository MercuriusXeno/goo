package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.ability.pulse.ZapDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Arrays;

/**
 * The source a wireless emitter stands beside its receiver: it powers what it touches at the emitter's level, as a
 * redstone block does at full power, and stands until the emitter lets it go. It draws nothing and nothing collides
 * with it; with no block that takes a signal left beside it, it removes itself.
 * decision wireless-emitter-beams-its-signal-to-the-closest-receiver
 */
public class WirelessSignalBlock extends Block {

    /** The level the source holds, its emitter's. */
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    /**
     * Creates the source.
     *
     * @param properties the block properties
     */
    public WirelessSignalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    @Override
    protected boolean isSignalSource(@NonNull BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos,
                            @NonNull Direction direction) {
        return state.getValue(POWER);
    }

    /** A source whose receiver is gone removes itself, so none is left standing alone. */
    @Override
    protected void neighborChanged(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                   @NonNull Block neighbor, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, orientation, movedByPiston);
        boolean receiverBeside = Arrays.stream(Direction.values())
                .anyMatch(side -> ZapDevice.ticks(level.getBlockState(pos.relative(side))));
        if (!level.isClientSide() && !receiverBeside) {
            level.removeBlock(pos, false);
        }
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
