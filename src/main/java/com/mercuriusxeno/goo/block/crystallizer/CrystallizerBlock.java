package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.block.BlockEntityTicks;
import com.mercuriusxeno.goo.block.GooMachineBlock;
import com.mercuriusxeno.goo.block.ShapeHitCheck;
import com.mercuriusxeno.goo.block.canister.SlottedCanisterData;
import com.mercuriusxeno.goo.item.ChrysmTier;
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
 * The crystallizer block (decision crystallizer-emits-chrysm): it crystallizes the
 * goo of one of the two canisters standing in the canister block on its top, back
 * left and back right, with the crystal of the other. Canister clicks, omniblob
 * pours and gaskets land on that canister block, as on the reactor. A click on the
 * knob, on the face toward the placing player, steps the tier it stops at; a click
 * on a mature crystal, whatever the player holds, takes the chrysm formed, as does
 * an empty-hand click elsewhere. The model is the operator's: the body lights its
 * inlay while active, and the dial turns to the knob's position.
 */
public class CrystallizerBlock extends GooMachineBlock {

    /** The face the knob sits on, toward the player who placed the crystallizer. */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /**
     * The knob, positions 1 to 3: the tier the crystallizer stops at, chrysm, budding or
     * flowering chrysm (operator ruling: a right click on the knob steps it and wraps from 3 to 1).
     */
    public static final IntegerProperty KNOB = IntegerProperty.create("knob", 1, CrystallizerPhases.KNOB_POSITIONS);
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
    private static final double TOP = CrystallizerLayout.TOP;

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

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        VoxelShape shape = Shapes.or(BODY_SHAPE, knobShape(state));
        if (level.getBlockEntity(pos) instanceof CrystallizerBlockEntity crystallizer
                && crystallizer.isMature(knobTier(state))) {
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
        double[] center = CrystallizerLayout.modelToWorld(facing, CrystalCluster.BASE_X, CrystalCluster.BASE_Z);
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
     * A click with an item in hand: the knob and a mature crystal are the crystallizer's
     * whatever the player holds; any other click passes to the item's own use, so a
     * canister places its canister block on top, sound included.
     */
    @Override
    protected @NonNull InteractionResult useItemOn(
            @NonNull ItemStack stack, @NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {
        return click(state, level, pos, player, hitResult);
    }

    /**
     * An empty-hand click: the knob steps, a mature crystal breaks into its chrysm, and
     * any other click passes.
     */
    @Override
    protected @NonNull InteractionResult useWithoutItem(
            @NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull BlockHitResult hitResult) {
        return click(state, level, pos, player, hitResult);
    }

    /**
     * The crystallizer's own clicks, the same on client and server so neither side
     * swallows a click the other passes: the knob and a mature crystal, nothing else.
     *
     * @param state     the block state
     * @param level     the level
     * @param pos       the crystallizer's position
     * @param player    the clicking player
     * @param hitResult the click's hit
     * @return SUCCESS for the knob or a mature crystal, PASS otherwise
     */
    private static InteractionResult click(BlockState state, Level level, BlockPos pos, Player player,
                                           BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof CrystallizerBlockEntity crystallizer)) {
            return InteractionResult.PASS;
        }
        if (ShapeHitCheck.hitInsideShape(hitResult, pos, knobShape(state))) {
            return level.isClientSide() ? InteractionResult.SUCCESS : stepKnob(crystallizer, state, level, pos);
        }
        if (hitsMatureCrystal(crystallizer, state, pos, hitResult)) {
            return level.isClientSide() ? InteractionResult.SUCCESS
                    : SlottedCanisterData.handToPlayer(crystallizer.takeFormed(), player, level, pos);
        }
        return InteractionResult.PASS;
    }

    /**
     * Steps the knob; a crystal still growing shatters back into omniblobs first (operator ruling).
     *
     * @param crystallizer the crystallizer
     * @param state        its block state
     * @param level        the level
     * @param pos          its position
     * @return SUCCESS
     */
    private static InteractionResult stepKnob(CrystallizerBlockEntity crystallizer, BlockState state, Level level,
                                              BlockPos pos) {
        if (!crystallizer.isMature(knobTier(state))) {
            crystallizer.shatter().forEach(shard -> popResource(level, pos.above(), shard));
        }
        level.setBlock(pos, state.setValue(KNOB, CrystallizerPhases.nextKnob(state.getValue(KNOB))), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * @param crystallizer the crystallizer
     * @param state        its block state
     * @param pos          its position
     * @param hit          the click's hit
     * @return true when the hit lands on a mature crystal
     */
    private static boolean hitsMatureCrystal(CrystallizerBlockEntity crystallizer, BlockState state, BlockPos pos,
                                             BlockHitResult hit) {
        return crystallizer.isMature(knobTier(state)) && ShapeHitCheck.hitInsideShape(hit, pos,
                crystalShape(state.getValue(FACING), crystallizer.crystallized()));
    }

    /**
     * Whether a click with a held canister is the crystallizer's own rather than a
     * placement on the canister block above.
     *
     * @param crystallizer the crystallizer
     * @param state        its block state
     * @param pos          its position
     * @param hit          the click's hit
     * @return true when the hit lands on the knob or a mature crystal
     */
    static boolean hitsKnobOrMatureCrystal(CrystallizerBlockEntity crystallizer, BlockState state, BlockPos pos,
                                           BlockHitResult hit) {
        return ShapeHitCheck.hitInsideShape(hit, pos, knobShape(state))
                || hitsMatureCrystal(crystallizer, state, pos, hit);
    }
}
