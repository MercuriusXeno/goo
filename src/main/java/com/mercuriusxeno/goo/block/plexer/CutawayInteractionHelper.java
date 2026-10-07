package com.mercuriusxeno.goo.block.plexer;

import com.mercuriusxeno.goo.block.plexer.PlexerBlock;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.network.PlayerKnowledge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cutaway hit-testing, target-item management, and item ejection for the plexer.
 * All methods are stateless helpers called from PlexerBlock.
 */
public final class CutawayInteractionHelper {

    /** Eject Y - near the bottom of the block where the hatch is. */
    private static final double EJECT_Y = 2.0 / 16.0;
    /** Block center for XZ positioning. */
    private static final double BLOCK_CENTER = 0.5;
    /** Ejected item outward speed. */
    private static final double EJECT_SPEED = 0.2;
    /** Ejected item upward velocity. */
    private static final double EJECT_LIFT = 0.1;

    /** Cutaway volume in model space (south-facing): x in [5,11], y in [8,13], z in [0,4]. */
    private static final double CUTAWAY_MIN_X = 5.0 / 16.0;
    private static final double CUTAWAY_MAX_X = 11.0 / 16.0;
    private static final double CUTAWAY_MIN_Y = 8.0 / 16.0;
    private static final double CUTAWAY_MAX_Y = 13.0 / 16.0;
    private static final double CUTAWAY_MAX_Z = 4.0 / 16.0;
    /** Halves a span to its midpoint; the cutaway's near face sits at model z 0. */
    private static final double HALF = 0.5;

    private CutawayInteractionHelper() { }

    /** Returns true if the interaction missed the cutaway and should pass through.
     *
     * @param stack     the held item
     * @param state     the block state
     * @param pos       the block position
     * @param hitResult the ray trace hit result
     * @return true if the interaction should pass
     */
    public static boolean shouldPassItemInteraction(ItemStack stack, BlockState state,
            BlockPos pos, BlockHitResult hitResult) {
        return !isCutawayClick(state, pos, hitResult);
    }

    /** Sets the plexer's target item; the plexer panel names it, so no message goes to the player.
     * Any item the plexer cannot take, one with no goo value or one the player has not
     * learned, puffs smoke and fizzles instead (decision plexer-messages-go-and-refusal-fizzles).
     *
     * @param plexer the plexer block entity
     * @param player the interacting player
     * @param stack  the item to set as target
     * @return SUCCESS interaction result
     */
    public static InteractionResult applyTargetItem(PlexerBlockEntity plexer, Player player, ItemStack stack) {
        if (!plexer.isValidTarget(stack)
                || refusesTarget(PlayerKnowledge.idOf(stack.getItem()), PlayerKnowledge.of(player))) {
            if (plexer.getLevel() instanceof ServerLevel level) {
                BlockPos pos = plexer.getBlockPos();
                PlexerRefusalCue.playAt(level, pos,
                        cutawayWorldCenter(pos, plexer.getBlockState().getValue(PlexerBlock.FACING)));
            }
            return InteractionResult.SUCCESS;
        }
        plexer.setTargetItem(cleanCopy(stack));
        return InteractionResult.SUCCESS;
    }

    /**
     * Whether the plexer refuses an item as its target: the player has not
     * learned what it is made of (decision plexer-refuses-an-unlearned-item).
     *
     * @param item  the clicked item's id
     * @param known the items the player knows
     * @return true when the item is unknown to the player
     */
    static boolean refusesTarget(Identifier item, KnownItems known) {
        return !known.contains(item);
    }

    /**
     * Creates a single-count copy of the item with no fluid or metadata components.
     *
     * @param stack the source item stack
     * @return a cleaned single-count copy
     */
    private static ItemStack cleanCopy(ItemStack stack) {
        ItemStack clean = new ItemStack(stack.getItem(), 1);
        if (stack.getItem() instanceof CanisterItem) {
            CanisterItem.setFluidContent(clean, CanisterFluidContent.EMPTY);
            CanisterItem.setMetadata(clean, CanisterMetadata.EMPTY);
        }
        return clean;
    }

    /** Clears the plexer's target item; the empty cutaway and the panel's absence show it
     * (decision plexer-messages-go-and-refusal-fizzles).
     *
     * @param plexer the plexer block entity
     * @return SUCCESS interaction result
     */
    public static InteractionResult clearTargetItem(PlexerBlockEntity plexer) {
        plexer.setTargetItem(ItemStack.EMPTY);
        return InteractionResult.SUCCESS;
    }

    /**
     * Returns true if the hit lands on any of the 5 cutaway interior faces:
     * back wall, left/right cheek inners, top slab underside, base slab top.
     * Transforms hit coords to model space and checks against cutaway volume.
     *
     * @param state the block state
     * @param pos   the block position
     * @param hit   the ray trace hit result
     * @return true if cutaway click
     */
    public static boolean isCutawayClick(BlockState state, BlockPos pos, BlockHitResult hit) {
        Direction facing = state.getValue(PlexerBlock.FACING);
        double hitX = hit.getLocation().x - pos.getX();
        double hitY = hit.getLocation().y - pos.getY();
        double hitZ = hit.getLocation().z - pos.getZ();
        double modelX = toModelX(facing, hitX, hitZ);
        double modelZ = toModelZ(facing, hitX, hitZ);
        return isInCutaway(modelX, hitY, modelZ);
    }

    /** Converts world-local XZ to south-facing model X by reversing facing rotation.
     *
     * @param facing the facing direction
     * @param hitX   block-local X hit coordinate
     * @param hitZ   block-local Z hit coordinate
     * @return the double value
     */
    public static double toModelX(Direction facing, double hitX, double hitZ) {
        return switch (facing) {
            case SOUTH -> hitX;
            case NORTH -> 1.0 - hitX;
            case EAST  -> 1.0 - hitZ;
            case WEST  -> hitZ;
            default    -> hitX;
        };
    }

    /** Converts world-local XZ to south-facing model Z by reversing facing rotation.
     *
     * @param facing the facing direction
     * @param hitX   block-local X hit coordinate
     * @param hitZ   block-local Z hit coordinate
     * @return the double value
     */
    public static double toModelZ(Direction facing, double hitX, double hitZ) {
        return switch (facing) {
            case SOUTH -> hitZ;
            case NORTH -> 1.0 - hitZ;
            case EAST  -> hitX;
            case WEST  -> 1.0 - hitX;
            default    -> hitZ;
        };
    }

    /**
     * Returns the center of the cutaway volume in world coordinates, the model's
     * south-facing center turned to the block's facing.
     *
     * @param pos    the block position
     * @param facing the facing direction
     * @return the cutaway's center
     */
    public static Vec3 cutawayWorldCenter(BlockPos pos, Direction facing) {
        double modelX = (CUTAWAY_MIN_X + CUTAWAY_MAX_X) * HALF;
        double modelY = (CUTAWAY_MIN_Y + CUTAWAY_MAX_Y) * HALF;
        double modelZ = CUTAWAY_MAX_Z * HALF;
        return new Vec3(pos.getX() + toWorldX(facing, modelX, modelZ), pos.getY() + modelY,
                pos.getZ() + toWorldZ(facing, modelX, modelZ));
    }

    /** Converts south-facing model XZ to world-local X, the inverse of {@link #toModelX} and {@link #toModelZ}.
     *
     * @param facing the facing direction
     * @param modelX model-space X coordinate
     * @param modelZ model-space Z coordinate
     * @return block-local X
     */
    static double toWorldX(Direction facing, double modelX, double modelZ) {
        return switch (facing) {
            case NORTH -> 1.0 - modelX;
            case EAST  -> modelZ;
            case WEST  -> 1.0 - modelZ;
            default    -> modelX;
        };
    }

    /** Converts south-facing model XZ to world-local Z, the inverse of {@link #toModelX} and {@link #toModelZ}.
     *
     * @param facing the facing direction
     * @param modelX model-space X coordinate
     * @param modelZ model-space Z coordinate
     * @return block-local Z
     */
    static double toWorldZ(Direction facing, double modelX, double modelZ) {
        return switch (facing) {
            case NORTH -> 1.0 - modelZ;
            case EAST  -> 1.0 - modelX;
            case WEST  -> modelX;
            default    -> modelZ;
        };
    }

    /** Returns true if model-space coords fall within the cutaway volume.
     *
     * @param modelX model-space X coordinate
     * @param modelY model-space Y coordinate
     * @param modelZ model-space Z coordinate
     * @return true if in cutaway
     */
    public static boolean isInCutaway(double modelX, double modelY, double modelZ) {
        return isInCutawayXY(modelX, modelY) && modelZ <= CUTAWAY_MAX_Z;
    }

    /**
     * Returns true if model-space X and Y fall within the cutaway horizontal and vertical range.
     *
     * @param modelX model-space X coordinate
     * @param modelY model-space Y coordinate
     * @return true if within cutaway X/Y bounds
     */
    public static boolean isInCutawayXY(double modelX, double modelY) {
        return modelX >= CUTAWAY_MIN_X && modelX <= CUTAWAY_MAX_X
            && modelY >= CUTAWAY_MIN_Y && modelY <= CUTAWAY_MAX_Y;
    }

    /** Spawns an ItemEntity at the hatch on the block face opposite to facing.
     * The item spawns just outside the block surface to avoid voxel clipping.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param stack the item stack
     */
    public static void ejectFromHatch(ServerLevel level, BlockPos pos, BlockState state, ItemStack stack) {
        Direction hatchDir = state.getValue(PlexerBlock.FACING).getOpposite();
        double x = pos.getX() + BLOCK_CENTER + hatchDir.getStepX();
        double y = pos.getY() + EJECT_Y;
        double z = pos.getZ() + BLOCK_CENTER + hatchDir.getStepZ();
        ItemEntity entity = new ItemEntity(level, x, y, z, stack);
        entity.setDeltaMovement(
            hatchDir.getStepX() * EJECT_SPEED, EJECT_LIFT, hatchDir.getStepZ() * EJECT_SPEED);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
    }
}
