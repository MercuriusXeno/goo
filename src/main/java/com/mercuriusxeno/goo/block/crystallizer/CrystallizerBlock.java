package com.mercuriusxeno.goo.block.crystallizer;

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
import java.util.Map;

/**
 * The crystallizer block (decision crystallizer-emits-chrysm): a gasket receiver
 * that crystallizes goo into chrysm. An omniblob click pours goo in; a click on
 * the knob, a small part on the face toward the placing player, steps the tier
 * it stops at; an empty-hand click elsewhere takes the chrysm formed inside,
 * and a sneak click pops the gasket. The body is a placeholder until the
 * operator designs the glass machine.
 */
public class CrystallizerBlock extends GooMachineBlock {

    /** Whether a choral gasket is installed on this crystallizer. */
    public static final BooleanProperty HAS_GASKET = BooleanProperty.create("has_gasket");
    /** The face the knob sits on, toward the player who placed the crystallizer. */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /**
     * The knob, sizes 1 to 3: the tier the crystallizer stops at, small, medium or large
     * (operator ruling: a right click on the knob steps it and wraps from 3 to 1).
     */
    public static final IntegerProperty KNOB = IntegerProperty.create("knob", 1, ChrysmTier.values().length);
    public static final MapCodec<CrystallizerBlock> CODEC = simpleCodec(CrystallizerBlock::new);

    /**
     * The knob part's own small shape on each face, standing two pixels proud of
     * it (operator ruling: small, affixed to the side of the block).
     */
    private static final Map<Direction, VoxelShape> KNOB_SHAPES = Map.of(
            Direction.NORTH, box(6, 6, -2, 10, 10, 0),
            Direction.SOUTH, box(6, 6, 16, 10, 10, 18),
            Direction.WEST, box(-2, 6, 6, 0, 10, 10),
            Direction.EAST, box(16, 6, 6, 18, 10, 10));
    private static final VoxelShape BODY_SHAPE = Shapes.block();

    /**
     * @param properties the block properties
     */
    public CrystallizerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HAS_GASKET, false)
                .setValue(KNOB, 1));
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
        builder.add(FACING, HAS_GASKET, KNOB);
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

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return Shapes.or(BODY_SHAPE, knobShape(state));
    }

    /** The knob is too small to stand on or bump; only the body collides. */
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
        return BlockEntityTicks.onServer(GooBlockEntities.CRYSTALLIZER, CrystallizerBlockEntity::serverTick);
    }

    /**
     * Classifies the held item and dispatches: an omniblob pours goo in, and
     * every other item falls through to the empty-hand click.
     */
    @Override
    protected @NonNull InteractionResult useItemOn(
            @NonNull ItemStack stack, @NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {
        return GooBlockInteraction.handleItemInteraction(
                stack, level, pos, player, hand, hitResult,
                CrystallizerBlockEntity.class,
                type -> type != GooInteractionType.BLOB_INSERT && type != GooInteractionType.TUNER_PASS,
                CrystallizerBlock::dispatchItem);
    }

    /**
     * Empty-hand clicks: a click on the knob steps it, sneak elsewhere pops the
     * gasket, and any other click hands the formed chrysm to the player.
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
            level.setBlock(pos, state.setValue(KNOB, CrystallizerPhases.nextKnob(state.getValue(KNOB))), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }
        if (GasketInstallation.removeAddressedGasket(level, pos, player, hitResult)) {
            return InteractionResult.SUCCESS;
        }
        return SlottedCanisterData.handToPlayer(crystallizer.takeFormed(), player, level, pos);
    }

    private static InteractionResult dispatchItem(
            GooInteractionType interaction, CrystallizerBlockEntity crystallizer, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult, BlockPos pos, Level level) {
        int accepted = BlobInsert.pour(stack, player, crystallizer::insertGoo);
        if (accepted <= 0) {
            return InteractionResult.PASS;
        }
        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }
}
