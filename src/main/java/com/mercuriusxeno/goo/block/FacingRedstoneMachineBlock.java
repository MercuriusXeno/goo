package com.mercuriusxeno.goo.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.function.Function;

/**
 * The shell the plexer and the reactor share: a machine that faces the player
 * on placement, records its redstone signal as TRIGGERED, shows CRAFTING while
 * it works, and takes dust from every side. Each machine keeps only its own
 * response to a neighbor change (decision machine-base-owns-the-lifecycle).
 */
public abstract class FacingRedstoneMachineBlock extends GooMachineBlock {

    /**
     * Horizontal facing, toward the player who placed the machine.
     */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /**
     * Whether the machine is receiving a redstone signal.
     */
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    /**
     * Whether the machine is working.
     */
    public static final BooleanProperty CRAFTING = BlockStateProperties.CRAFTING;

    private final MapCodec<? extends FacingRedstoneMachineBlock> codec;

    /**
     * Creates the machine and registers its default state: facing north, untriggered, idle.
     *
     * @param properties  the block properties
     * @param constructor the machine's own constructor, which its codec rebuilds it with
     */
    protected FacingRedstoneMachineBlock(Properties properties,
                                         Function<Properties, ? extends FacingRedstoneMachineBlock> constructor) {
        super(properties);
        this.codec = simpleCodec(constructor);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(TRIGGERED, false)
                .setValue(CRAFTING, false));
    }

    @Override
    protected final void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(FACING, TRIGGERED, CRAFTING);
    }

    /**
     * Faces the player, triggered when placed against a signal.
     *
     * @param context the placement context
     * @return the state for placement
     */
    @Override
    public final BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(TRIGGERED, context.getLevel().hasNeighborSignal(pos));
    }

    /**
     * The machine responds to redstone power on any side, so dust visually
     * connects from any direction.
     *
     * @param state     the block state
     * @param level     the level
     * @param pos       the block position
     * @param direction the side the dust is approaching from, or null
     * @return true: dust connects on every side
     */
    @Override
    public final boolean canConnectRedstone(@NonNull BlockState state, @NonNull BlockGetter level,
                                            @NonNull BlockPos pos, @Nullable Direction direction) {
        return true;
    }

    @Override
    protected final @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected final @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }
}
