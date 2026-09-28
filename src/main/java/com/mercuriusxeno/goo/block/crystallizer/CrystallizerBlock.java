package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.GooConstants;
import com.mercuriusxeno.goo.block.BlockEntityTicks;
import com.mercuriusxeno.goo.block.GooBlockInteraction;
import com.mercuriusxeno.goo.block.GooMachineBlock;
import com.mercuriusxeno.goo.block.ShapeHitCheck;
import com.mercuriusxeno.goo.block.canister.SlottedCanisterData;
import com.mercuriusxeno.goo.block.gasket.GasketInstallation;
import com.mercuriusxeno.goo.item.BlobInsert;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.item.GooInteractionType;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * The crystallizer block (decision crystallizer-emits-chrysm): it crystallizes the
 * goo of one of the two canisters standing on its top, back left and back right,
 * with the crystal of the other. A canister click over a slot's footprint slots it
 * in; an empty-hand click on a canister takes it out; a click on the knob, on the
 * face toward the placing player, steps the tier it stops at; an empty-hand click
 * elsewhere takes the chrysm formed inside, and a sneak click on a canister pops
 * the gasket it addresses. The model is the operator's: the body lights its inlay
 * while active, and the dial turns to the knob's position.
 */
public class CrystallizerBlock extends GooMachineBlock {

    /** The face the knob sits on, toward the player who placed the crystallizer. */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /**
     * The knob, sizes 1 to 3: the tier the crystallizer stops at, small, medium or large
     * (operator ruling: a right click on the knob steps it and wraps from 3 to 1).
     */
    public static final IntegerProperty KNOB = IntegerProperty.create("knob", 1, ChrysmTier.values().length);
    public static final MapCodec<CrystallizerBlock> CODEC = simpleCodec(CrystallizerBlock::new);

    /** Whether the crystallizer crystallized within the last few ticks; the model lights its inlay. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /**
     * The dial and its pointer on the facing face, the operator's model's dial at
     * x 6.5 to 9.5, y 5.5 to 9.5 on its front, turned to each facing.
     */
    private static final Map<Direction, VoxelShape> KNOB_SHAPES = Map.of(
            Direction.SOUTH, box(6.5, 5.5, 15, 9.5, 9.5, 16),
            Direction.NORTH, box(6.5, 5.5, 0, 9.5, 9.5, 1),
            Direction.EAST, box(15, 5.5, 6.5, 16, 9.5, 9.5),
            Direction.WEST, box(0, 5.5, 6.5, 1, 9.5, 9.5));
    /** The operator's model's body, 14 by 16 by 14. */
    private static final VoxelShape BODY_SHAPE = box(1, 0, 1, 15, 16, 15);
    /** A canister's footprint half-width, 2 px, and its height, caps included, 12 px. */
    private static final double CANISTER_HALF = 2;
    private static final double CANISTER_HEIGHT = 12;
    private static final double TOP = 16;
    /**
     * The two canister slots' centers in model space, the dial on the south face:
     * back left then back right, 3 px in from the back and side (operator ruling: inset 2 px, then in 1x1).
     */
    private static final double[][] SLOT_CENTERS = {{5, 5}, {11, 5}};
    private static final Map<Direction, VoxelShape[]> CANISTER_SHAPES = buildCanisterShapes();

    /**
     * @param properties the block properties
     */
    public CrystallizerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(KNOB, 1).setValue(ACTIVE, false));
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, KNOB, ACTIVE);
    }

    /**
     * Turns the knob's face toward the placing player.
     *
     * @param context the placement context
     * @return the placed state
     */
    @Override
    public @NonNull BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * @param state a crystallizer block state
     * @return the tier its knob names
     */
    public static ChrysmTier knobTier(BlockState state) {
        return ChrysmTier.values()[state.getValue(KNOB) - 1];
    }

    /**
     * @param state a crystallizer block state
     * @return the knob's shape on the face it sits on
     */
    public static VoxelShape knobShape(BlockState state) {
        return KNOB_SHAPES.get(state.getValue(FACING));
    }

    /**
     * @param facing the face the dial sits on
     * @param slot   the canister slot, 0 back left or 1 back right
     * @return the slot's canister shape standing on the top, caps included
     */
    public static VoxelShape canisterSlotShape(Direction facing, int slot) {
        return CANISTER_SHAPES.get(facing)[slot];
    }

    /**
     * The canister slot a hit lands on: the canister itself, or the top face over its footprint.
     *
     * @param state the crystallizer's state
     * @param pos   the crystallizer's position
     * @param hit   the hit
     * @return the slot, or NO_SLOT when the hit lands on neither
     */
    public static int slotAt(BlockState state, BlockPos pos, BlockHitResult hit) {
        for (int slot = 0; slot < SLOT_CENTERS.length; slot++) {
            if (ShapeHitCheck.hitInsideShape(hit, pos, canisterSlotShape(state.getValue(FACING), slot))) {
                return slot;
            }
        }
        return GooConstants.NO_SLOT;
    }

    private static Map<Direction, VoxelShape[]> buildCanisterShapes() {
        Map<Direction, VoxelShape[]> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape[] slots = new VoxelShape[SLOT_CENTERS.length];
            for (int slot = 0; slot < SLOT_CENTERS.length; slot++) {
                double[] center = modelToWorld(facing, SLOT_CENTERS[slot][0], SLOT_CENTERS[slot][1]);
                slots[slot] = box(center[0] - CANISTER_HALF, TOP, center[1] - CANISTER_HALF,
                        center[0] + CANISTER_HALF, TOP + CANISTER_HEIGHT, center[1] + CANISTER_HALF);
            }
            shapes.put(facing, slots);
        }
        return shapes;
    }

    /**
     * Turns a model-space point, the dial on the south face, to the world by the
     * blockstate's y rotation for the facing (south 0, west 90, north 180, east 270).
     *
     * @param facing the face the dial sits on
     * @param x      model x, in pixels
     * @param z      model z, in pixels
     * @return world {x, z}, in pixels
     */
    static double[] modelToWorld(Direction facing, double x, double z) {
        return switch (facing) {
            case WEST -> new double[] {TOP - z, x};
            case NORTH -> new double[] {TOP - x, TOP - z};
            case EAST -> new double[] {z, TOP - x};
            default -> new double[] {x, z};
        };
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        VoxelShape shape = Shapes.or(BODY_SHAPE, knobShape(state));
        if (level.getBlockEntity(pos) instanceof CrystallizerBlockEntity crystallizer) {
            for (int slot = 0; slot < CrystallizerBlockEntity.SLOT_COUNT; slot++) {
                if (crystallizer.isSlotFilled(slot)) {
                    shape = Shapes.or(shape, canisterSlotShape(state.getValue(FACING), slot));
                }
            }
            shape = Shapes.or(shape, crystalShape(state.getValue(FACING), crystallizer.crystallized()));
        }
        return shape;
    }

    /**
     * The box around the quartz cluster, so a click on the crystal lands on the crystallizer.
     *
     * @param facing       the face the dial sits on
     * @param crystallized the crystallized volume, in mB
     * @return the cluster's box, empty while nothing is crystallized
     */
    public static VoxelShape crystalShape(Direction facing, long crystallized) {
        double[] reach = CrystalCluster.reach(crystallized);
        if (reach[1] <= 0) {
            return Shapes.empty();
        }
        double[] center = modelToWorld(facing, CrystalCluster.BASE_X, CrystalCluster.BASE_Z);
        return box(center[0] - reach[0], TOP, center[1] - reach[0],
                center[0] + reach[0], TOP + reach[1], center[1] + reach[0]);
    }

    /** The dial is too small to stand on or bump; only the body collides. */
    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                                    @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return BODY_SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new CrystallizerBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityTicks<CrystallizerBlockEntity> ticks() {
        return BlockEntityTicks.bothSides(GooBlockEntities.CRYSTALLIZER, CrystallizerBlockEntity::serverTick,
                CrystallizerBlockEntity::clientTick);
    }

    /**
     * Classifies the held item and dispatches as the canister block does: a canister
     * over a slot slots in, or takes a filled slot's canister out; an omniblob pours
     * into the addressed slot's canister; every other item falls through to the
     * empty-hand click.
     */
    @Override
    protected @NonNull InteractionResult useItemOn(
            @NonNull ItemStack stack, @NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {
        return GooBlockInteraction.handleItemInteraction(
                stack, level, pos, player, hand, hitResult,
                CrystallizerBlockEntity.class,
                type -> type != GooInteractionType.CANISTER_INSERT && type != GooInteractionType.BLOB_INSERT
                        && type != GooInteractionType.TUNER_PASS,
                CrystallizerBlock::dispatchItem);
    }

    /**
     * Empty-hand clicks: a click on the knob steps it, a sneak click pops the
     * gasket it addresses, a click on a canister takes it out, and any other click
     * hands the formed chrysm to the player.
     */
    @Override
    protected @NonNull InteractionResult useWithoutItem(
            @NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull BlockHitResult hitResult) {
        InteractionResult earlyOut = GooBlockInteraction.validateEmptyHand(level, pos, player);
        if (earlyOut != null) {
            return earlyOut;
        }
        if (!(level.getBlockEntity(pos) instanceof CrystallizerBlockEntity crystallizer)) {
            return InteractionResult.PASS;
        }
        if (ShapeHitCheck.hitInsideShape(hitResult, pos, knobShape(state))) {
            return stepKnob(state, level, pos);
        }
        if (GasketInstallation.removeAddressedGasket(level, pos, player, hitResult)) {
            return InteractionResult.SUCCESS;
        }
        return takeCanisterOrChrysm(crystallizer, state, level, pos, player, hitResult);
    }

    private static InteractionResult stepKnob(BlockState state, Level level, BlockPos pos) {
        level.setBlock(pos, state.setValue(KNOB, CrystallizerPhases.nextKnob(state.getValue(KNOB))), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Hands the canister the hit lands on to the player, or the formed chrysm when it lands on none.
     *
     * @param crystallizer the crystallizer
     * @param state        its block state
     * @param level        the level
     * @param pos          its position
     * @param player       the clicking player
     * @param hitResult    the click's hit
     * @return SUCCESS when something was handed over, PASS otherwise
     */
    private static InteractionResult takeCanisterOrChrysm(CrystallizerBlockEntity crystallizer, BlockState state,
                                                          Level level, BlockPos pos, Player player,
                                                          BlockHitResult hitResult) {
        int slot = slotAt(state, pos, hitResult);
        ItemStack taken = slot != GooConstants.NO_SLOT && crystallizer.isSlotFilled(slot)
                ? crystallizer.containerState().remove(slot) : crystallizer.takeFormed();
        return SlottedCanisterData.handToPlayer(taken, player, level, pos);
    }

    private static InteractionResult dispatchItem(
            GooInteractionType interaction, CrystallizerBlockEntity crystallizer, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult, BlockPos pos, Level level) {
        int slot = slotAt(crystallizer.getBlockState(), pos, hitResult);
        if (interaction == GooInteractionType.BLOB_INSERT) {
            return pourBlob(crystallizer, stack, player, slot, level, pos);
        }
        if (slot == GooConstants.NO_SLOT) {
            // Off both slots the canister's own use runs, and the placement resolver refuses the crystallizer's top.
            return InteractionResult.PASS;
        }
        if (crystallizer.isSlotFilled(slot)) {
            return SlottedCanisterData.handToPlayer(crystallizer.containerState().remove(slot), player, level, pos);
        }
        if (!crystallizer.containerState().insert(slot, stack.copyWithCount(1), false)) {
            return InteractionResult.PASS;
        }
        stack.consume(1, player);
        level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Pours an omniblob into the addressed slot's canister, or the first canister that takes it,
     * as the canister block does.
     *
     * @param crystallizer the crystallizer
     * @param stack        the omniblob
     * @param player       the pouring player
     * @param hitSlot      the slot the click addresses, or NO_SLOT
     * @param level        the level
     * @param pos          the crystallizer's position
     * @return SUCCESS when goo went in, PASS otherwise
     */
    private static InteractionResult pourBlob(CrystallizerBlockEntity crystallizer, ItemStack stack, Player player,
                                              int hitSlot, Level level, BlockPos pos) {
        int accepted = BlobInsert.pour(stack, player, (type, volume) -> {
            int slot = GooBlockInteraction.findSlot(hitSlot, CrystallizerBlockEntity.SLOT_COUNT, crystallizer::canAccept);
            return slot == GooConstants.NO_SLOT ? 0 : crystallizer.insertGoo(slot, type, volume);
        });
        if (accepted <= 0) {
            return InteractionResult.PASS;
        }
        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Drops both canisters on break; the base drops their gaskets.
     *
     * @param level  the level
     * @param pos    the block position
     * @param state  the block state
     * @param player the breaking player
     * @return the block state
     */
    @Override
    public @NonNull BlockState playerWillDestroy(@NonNull Level level, @NonNull BlockPos pos,
                                                @NonNull BlockState state, @NonNull Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CrystallizerBlockEntity crystallizer) {
            for (int slot = 0; slot < CrystallizerBlockEntity.SLOT_COUNT; slot++) {
                ItemStack canister = crystallizer.containerState().remove(slot);
                if (!canister.isEmpty()) {
                    popResource(level, pos, canister);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
