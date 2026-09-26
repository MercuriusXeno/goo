package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.block.BlockEntityTicks;
import com.mercuriusxeno.goo.block.GooBlockInteraction;
import com.mercuriusxeno.goo.block.GooMachineBlock;
import com.mercuriusxeno.goo.block.gasket.GasketInstallation;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * Tap block: a faucet with a canister slot that drips goo on a timer.
 * FACING indicates the direction the spigot points. Right-clicking the valve
 * toggles dripping; right-clicking the body inserts/removes the canister.
 */
public class TapBlock extends GooMachineBlock {

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /**
     * Whether a choral gasket is installed on this tap.
     */
    public static final BooleanProperty HAS_GASKET = BooleanProperty.create("has_gasket");
    /**
     * Whether the tap valve is open (dripping).
     */
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final MapCodec<TapBlock> CODEC = simpleCodec(TapBlock::new);

    // --- Sub-region shapes for hit detection ---
    /**
     * South-facing body shape (attachment region).
     */
    private static final VoxelShape SOUTH_BODY = box(5, 0, 0, 11, 4, 6);
    /**
     * South-facing valve shape (toggle region).
     */
    private static final VoxelShape SOUTH_VALVE = box(6.5, 4, 6.5, 9.5, 6.5, 9.5);

    /**
     * South-facing canister slot shape (wireframe preview + BER position).
     */
    private static final VoxelShape SOUTH_CANISTER_SLOT = box(6, 4, 1, 10, 16, 5);
    /**
     * South-facing spigot nozzle shape.
     */
    private static final VoxelShape SOUTH_SPIGOT = box(TapSpigot.MIN_XZ_PX, TapSpigot.BOTTOM_PX, TapSpigot.MIN_XZ_PX,
            TapSpigot.MAX_XZ_PX, TapSpigot.TOP_PX, TapSpigot.MAX_XZ_PX);

    /**
     * Per-facing body shapes for hit detection.
     */
    private static final Map<Direction, VoxelShape> BODY_SHAPES = TapShapeBuilder.buildSubShapes(SOUTH_BODY);
    /**
     * Per-facing valve shapes for hit detection.
     */
    private static final Map<Direction, VoxelShape> VALVE_SHAPES = TapShapeBuilder.buildSubShapes(SOUTH_VALVE);
    /**
     * Per-facing canister slot shapes for wireframe preview.
     */
    private static final Map<Direction, VoxelShape> CANISTER_SLOT_SHAPES = TapShapeBuilder.buildSubShapes(SOUTH_CANISTER_SLOT);
    /**
     * Per-facing composite collision shapes (no canister).
     */
    private static final Map<Direction, VoxelShape> SHAPES =
            TapShapeBuilder.buildShapes(SOUTH_BODY, SOUTH_SPIGOT, SOUTH_VALVE);
    /**
     * Per-facing composite shapes with canister slot included.
     */
    private static final Map<Direction, VoxelShape> SHAPES_WITH_CANISTER =
            TapShapeBuilder.buildShapesWithCanister(SOUTH_BODY, SOUTH_SPIGOT, SOUTH_VALVE, SOUTH_CANISTER_SLOT);

    /**
     * Constructs a new tap block with default south-facing state.
     *
     * @param properties the block properties
     */
    public TapBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(HAS_GASKET, false)
                .setValue(OPEN, true));
    }

    /**
     * Returns the body VoxelShape for the given facing direction.
     *
     * @param facing the facing direction
     * @return the voxel shape
     */
    public static VoxelShape bodyShape(Direction facing) {
        return BODY_SHAPES.getOrDefault(facing, BODY_SHAPES.get(Direction.SOUTH));
    }

    /**
     * Returns the canister slot VoxelShape for the given facing direction.
     *
     * @param facing the facing direction
     * @return the canister slot shape
     */
    public static VoxelShape canisterSlotShape(Direction facing) {
        return CANISTER_SLOT_SHAPES.getOrDefault(facing, CANISTER_SLOT_SHAPES.get(Direction.SOUTH));
    }

    /**
     * Returns the codec for serialization.
     *
     * @return the codec
     */
    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /**
     * Returns MODEL render shape since the tap uses a block model.
     *
     * @param state the block state
     * @return the render shape
     */
    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Registers all tap blockstate properties.
     *
     * @param builder the state definition builder
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_GASKET, OPEN);
    }

    /**
     * Places the tap facing toward the clicked block face. The tap's FACING
     * is the direction the spigot points (away from the container it attaches to).
     *
     * @param context the collision context
     * @return the state for placement
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (clickedFace.getAxis().isVertical()) {
            clickedFace = context.getHorizontalDirection().getOpposite();
        }
        return this.defaultBlockState().setValue(FACING, clickedFace);
    }

    /**
     * Returns the composite shape, including the canister slot when a canister is inserted.
     *
     * @param state   the block state
     * @param level   the current level
     * @param pos     the block position
     * @param context the collision context
     * @return the shape
     */
    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        Direction facing = state.getValue(FACING);
        boolean hasCanister = level.getBlockEntity(pos) instanceof TapBlockEntity tap
                && !tap.getCanister().isEmpty();
        Map<Direction, VoxelShape> map = hasCanister ? SHAPES_WITH_CANISTER : SHAPES;
        return map.getOrDefault(facing, map.get(Direction.SOUTH));
    }

    /**
     * Creates the tap block entity for this position.
     *
     * @param pos   the block position
     * @param state the block state
     * @return the new block entity
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new TapBlockEntity(pos, state);
    }

    // --- Interactions ---

    @Override
    protected BlockEntityTicks<TapBlockEntity> ticks() {
        return BlockEntityTicks.onServer(GooBlockEntities.TAP, TapBlockEntity::serverTick);
    }

    /**
     * Drops gasket and canister items on break.
     *
     * @param level  the current level
     * @param pos    the block position
     * @param state  the block state
     * @param player the interacting player
     * @return the block state
     */
    @Override
    public @NonNull BlockState playerWillDestroy(
            @NonNull Level level, @NonNull BlockPos pos,
            @NonNull BlockState state, @NonNull Player player) {
        if (!level.isClientSide()) {
            TapInteractionHandler.dropGasketOnBreak(level, pos, state);
            TapInteractionHandler.dropCanisterOnBreak(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // --- Public shape accessors ---

    /**
     * Classifies the held item via GooBlockInteraction and dispatches to
     * tap-specific handlers.
     *
     * @param stack     the item stack
     * @param state     the block state
     * @param level     the current level
     * @param pos       the block position
     * @param player    the interacting player
     * @param hand      the hand used
     * @param hitResult the ray trace hit result
     * @return the interaction result
     */
    @Override
    protected @NonNull InteractionResult useItemOn(
            @NonNull ItemStack stack, @NonNull BlockState state, Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {
        // tap-top-click-inserts-canister: an item click inserts from any region, the slot outline's included
        return GooBlockInteraction.handleItemInteraction(
                stack, level, pos, player, hand, hitResult,
                TapBlockEntity.class,
                t -> t == null,
                TapInteractionHandler::dispatchTap);
    }

    /**
     * Empty-hand interactions: sneak pops the tap's gasket from any region;
     * otherwise a valve hit toggles open/closed and any other hit removes the canister.
     *
     * @param state     the block state
     * @param level     the current level
     * @param pos       the block position
     * @param player    the interacting player
     * @param hitResult the ray trace hit result
     * @return the interaction result
     */
    @Override
    protected @NonNull InteractionResult useWithoutItem(
            @NonNull BlockState state, Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull BlockHitResult hitResult) {
        InteractionResult earlyOut = GooBlockInteraction.validateEmptyHand(level, pos, player);
        if (earlyOut != null) {
            return earlyOut;
        }

        if (!(level.getBlockEntity(pos) instanceof TapBlockEntity tap)) {
            return InteractionResult.PASS;
        }
        if (GasketInstallation.removeAddressedGasket(level, pos, player, hitResult)) {
            return InteractionResult.SUCCESS;
        }

        Direction facing = state.getValue(FACING);
        return TapInteractionHandler.dispatchEmptyHand(
                state, level, pos, player, hitResult, tap, facing, VALVE_SHAPES, CANISTER_SLOT_SHAPES);
    }
}
