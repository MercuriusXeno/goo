package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.block.BlockEntityTicks;
import com.mercuriusxeno.goo.block.GooBlockInteraction;
import com.mercuriusxeno.goo.block.GooMachineBlock;
import com.mercuriusxeno.goo.block.gasket.GasketInstallation;
import com.mercuriusxeno.goo.item.GooInteractionType;
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
import org.jspecify.annotations.Nullable;
import java.util.Set;

/**
 * The crucible block (goocible): melts items into goo. Items for melting are
 * item entities that reach the basin's kill box (CrucibleItemDrift), not right-clicks.
 * Right-click handles the flint-and-steel spark, goo insertion, and the goo
 * extraction that every other item, a canister among them, falls through to.
 * Drops internal state (PMI, fuel rod, reservoir goo) when broken.
 *
 * Blockstate properties: LIT (active/melting visual),
 * HAS_GASKET (bottom gasket).
 * The goocible model switches between on (LIT=true) and off (LIT=false) states.
 */
public class CrucibleBlock extends GooMachineBlock {

    public static final MapCodec<CrucibleBlock> CODEC = simpleCodec(CrucibleBlock::new);

    /** Horizontal facing direction - orients the crucible's front (fire-glow) face toward the player. */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /** Whether the crucible is actively melting (drives on/off model state). */
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    /** Light level emitted by the firebox while LIT (matches the prior
     * Properties.lightLevel(13) value). */
    private static final int CRUCIBLE_LIT_LIGHT = 13;

    /** The clicks a crucible answers through its dispatcher; a canister click is not among them. */
    static final Set<GooInteractionType> CLICK_ROWS = Set.of(GooInteractionType.SPARK, GooInteractionType.GOO_INSERT);

    /** Error message prefix for an interaction type outside CLICK_ROWS reaching dispatch. */
    private static final String ERR_UNHANDLED = "Unhandled interaction: ";
    /** Whether a gasket is attached to this crucible. */
    public static final BooleanProperty HAS_GASKET = BooleanProperty.create("has_gasket");

    /** Creates a crucible block and registers default blockstate values.
     *
     * @param properties the block properties
     */
    public CrucibleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(LIT, false)
            .setValue(HAS_GASKET, false));
    }

    /** Places the crucible so its front face points toward the player.
     *
     * @param context the block placement context
     * @return the state for placement
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Returns the codec for serialization.
     *
     * @return the codec
     */
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Registers all crucible blockstate properties.
     *
     * @param builder the state definition builder
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, HAS_GASKET);
    }

    /** Returns MODEL render shape since the crucible uses a block model.
     *
     * @param state the block state
     * @return the render shape
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** The LIT firebox glow, the 13-light burn; the base takes the brighter
     * of it and the reservoir's goo glow.
     *
     * @param state the block state
     * @return the burn light
     */
    @Override
    protected int stateLightEmission(BlockState state) {
        return state.getValue(LIT) ? CRUCIBLE_LIT_LIGHT : 0;
    }

    /** Returns the goocible pot collision/outline shape, the basin hollowed to its drawn floor.
     *
     * @param state   the block state
     * @param level   the current level
     * @param pos     the block position
     * @param context the collision context
     * @return the shape
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return CrucibleShape.SHAPE;
    }

    /** Enables shape-based light occlusion for the non-full-block crucible.
     *
     * @param state the block state
     * @return true if the condition is met
     */
    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    /** Creates the crucible block entity for this position.
     *
     * @param pos   the block position
     * @param state the block state
     * @return the new block entity
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrucibleBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityTicks<CrucibleBlockEntity> ticks() {
        return BlockEntityTicks.onServer(GooBlockEntities.CRUCIBLE, CrucibleBlockEntity::serverTick);
    }

    /** Dispatches held-item interactions: the flint-and-steel spark and goo insertion; a
     * tuner or gasket passes to its own use, and every other item, a canister among them, falls
     * through to the empty-hand drain (decision canister-click-is-any-other-click-on-crucible-and-vat).
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
    protected InteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        return GooBlockInteraction.handleItemInteraction(
                stack, level, pos, player, hand, hitResult,
                CrucibleBlockEntity.class, CLICK_ROWS, CrucibleBlock::dispatchClick);
    }

    /** Routes a crucible row to its handler.
     *
     * @param interaction the classified interaction, one of CLICK_ROWS
     * @param crucible    the crucible block entity
     * @param stack       the held item stack
     * @param player      the interacting player
     * @param hand        the hand used
     * @param hitResult   the ray trace hit result
     * @param pos         the block position
     * @param level       the current level
     * @return the interaction result
     */
    private static InteractionResult dispatchClick(
            GooInteractionType interaction, CrucibleBlockEntity crucible, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult, BlockPos pos, Level level) {
        return switch (interaction) {
            case SPARK -> CrucibleInteraction.spark(stack, crucible, player, hand);
            case GOO_INSERT -> CrucibleInteraction.pourGoo(stack, crucible, player);
            default -> throw new IllegalStateException(ERR_UNHANDLED + interaction);
        };
    }

    /** Handles empty-hand interactions: sneak pops the gasket, else passes; plain = goo extraction.
     *
     * @param state     the block state
     * @param level     the current level
     * @param pos       the block position
     * @param player    the interacting player
     * @param hitResult the ray trace hit result
     * @return the result
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) { return InteractionResult.SUCCESS; }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CrucibleBlockEntity crucible)) { return InteractionResult.PASS; }

        if (GasketInstallation.removeAddressedGasket(level, pos, player, hitResult)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        return CrucibleInteraction.tryExtractGoo(crucible, player);
    }

    // -- Block break drops --

    /** Drops the crucible's PMI and reservoir before the block breaks; the base drops its gasket.
     *
     * @param level  the current level
     * @param pos    the block position
     * @param state  the block state
     * @param player the interacting player
     * @return the result
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos,
            BlockState state, Player player) {
        CrucibleDrops.dropCrucibleContents(level, pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

}
