package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.block.ICanisterClickTaker;
import com.mercuriusxeno.goo.block.ICutawayMachine;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;
import static com.mercuriusxeno.goo.GooConstants.NO_SLOT;

/**
 * Answers where a canister use lands, the one rule the server's placement and the client's green
 * preview both read (decision preview-runs-the-placement-validator).
 */
public final class CanisterPlacementResolver {

    private CanisterPlacementResolver() {
    }

    /**
     * Where a canister use lands: an insert into an existing canister block, a new canister block,
     * or nothing.
     *
     * @param pos          the canister block position
     * @param slot         the slot the canister enters
     * @param intoExisting true when the canister enters a canister block already standing at pos
     */
    public record CanisterPlacement(BlockPos pos, int slot, boolean intoExisting) {
    }

    /**
     * Resolves a canister use. A standing click the aimed block handles itself places nothing; a
     * canister block at the hit, or where the new block would go, takes an insert into its best
     * empty allowed slot; otherwise a new canister block places where vanilla would put it, when
     * the space is replaceable and unobstructed, the block below supports a canister and the
     * aimed slot is allowed.
     *
     * @param context  the placement context the use builds
     * @param sneaking true when the player is sneaking, so the aimed block's own click is skipped
     * @return the placement, or null when the use places nothing
     */
    public static @Nullable CanisterPlacement resolve(BlockPlaceContext context, boolean sneaking) {
        Level level = context.getLevel();
        BlockHitResult hit = aimedHit(context);
        BlockEntity aimed = level.getBlockEntity(hit.getBlockPos());
        if (!sneaking && aimedBlockTakesTheClick(level, aimed, hit, context.getItemInHand())) {
            return null;
        }
        if (aimed instanceof CanisterBlockEntity clicked) {
            return insertInto(clicked, hit.getLocation(), hit.getDirection());
        }
        BlockPos besidePos = hit.getBlockPos().relative(hit.getDirection());
        if (level.getBlockEntity(besidePos) instanceof CanisterBlockEntity beside) {
            return insertInto(beside, hit.getLocation(), hit.getDirection().getOpposite());
        }
        return newBlock(context);
    }

    /**
     * The slot a new canister block at pos takes: the one the click names on the entry face,
     * or the first slot the machine below allows.
     *
     * @param level     the current level
     * @param pos       the new canister block position
     * @param clickLoc  the click location in world coordinates
     * @param clickFace the face the click hit
     * @return the slot index
     */
    public static int newBlockSlot(Level level, BlockPos pos, Vec3 clickLoc, Direction clickFace) {
        return CanisterPlacementValidator.constrainSlot(
                CanisterPlacementValidator.computePlacementSlot(clickLoc, pos, clickFace.getOpposite()),
                level, pos);
    }

    /**
     * Returns true when a standing click on the aimed block is the block's own. A goo machine
     * answers exactly: a canister holder that takes the use, a machine's cutaway, or a machine
     * whose click moves goo with the canister. Any other block takes the click when its class
     * defines its own right click (the operator's rule: no outline where a plain click is the block's).
     *
     * @param level    the current level
     * @param aimed    the block entity at the hit, or null
     * @param hit      the hit
     * @param canister the held canister
     * @return true when the aimed block handles the click
     */
    private static boolean aimedBlockTakesTheClick(Level level, @Nullable BlockEntity aimed, BlockHitResult hit,
                                                   ItemStack canister) {
        if (aimed instanceof ICanisterHolder || aimed instanceof ICutawayMachine
                || aimed instanceof ICanisterClickTaker) {
            return gooMachineTakesTheClick(aimed, hit, canister);
        }
        Block block = level.getBlockState(hit.getBlockPos()).getBlock();
        return block != null && OwnRightClick.definedBy(block.getClass());
    }

    private static boolean gooMachineTakesTheClick(BlockEntity aimed, BlockHitResult hit, ItemStack canister) {
        if (aimed instanceof ICanisterHolder holder && holder.takesCanisterAt(hit, false)) {
            return true;
        }
        if (aimed instanceof ICutawayMachine machine && machine.isCutawayHit(hit)) {
            return true;
        }
        return aimed instanceof ICanisterClickTaker taker && taker.takesCanisterClick(canister);
    }

    private static @Nullable CanisterPlacement insertInto(CanisterBlockEntity canister, Vec3 hitLocation,
                                                          Direction entryFace) {
        BlockPos pos = canister.getBlockPos();
        int slot = CanisterSlotResolver.resolveAndConstrain(hitLocation, pos, entryFace, canister);
        return slot == NO_SLOT ? null : new CanisterPlacement(pos, slot, true);
    }

    private static @Nullable CanisterPlacement newBlock(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!context.canPlace() || !CanisterPlacementValidator.isSupportedBelow(level, pos.below())) {
            return null;
        }
        if (!isUnobstructed(context, level, pos)) {
            return null;
        }
        int slot = newBlockSlot(level, pos, context.getClickLocation(), context.getClickedFace());
        return CanisterPlacementValidator.isSlotAllowed(level, pos, slot)
                ? new CanisterPlacement(pos, slot, false) : null;
    }

    /**
     * Returns true when no entity stands in the new canister block's collision, the test
     * BlockItem.canPlace runs before placing.
     *
     * @param context the placement context
     * @param level   the current level
     * @param pos     the new canister block position
     * @return true when the cell is unobstructed
     */
    private static boolean isUnobstructed(BlockPlaceContext context, Level level, BlockPos pos) {
        if (!(context.getItemInHand().getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        BlockState placed = blockItem.getBlock().defaultBlockState();
        return level.isUnobstructed(placed, pos, CollisionContext.placementContext(context.getPlayer()));
    }

    /**
     * The hit the use aimed at. BlockPlaceContext reports the placement cell as its clicked
     * position, so the aimed block is the cell behind the clicked face unless the click replaces it.
     *
     * @param context the placement context
     * @return the aimed hit
     */
    private static BlockHitResult aimedHit(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockPos aimedPos = context.replacingClickedOnBlock()
                ? context.getClickedPos() : context.getClickedPos().relative(face.getOpposite());
        return new BlockHitResult(context.getClickLocation(), face, aimedPos, context.isInside());
    }
}
