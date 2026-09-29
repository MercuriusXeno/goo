package com.mercuriusxeno.goo.block.vat;

import com.mercuriusxeno.goo.block.GooBlockInteraction;
import com.mercuriusxeno.goo.item.GooDeposit;
import com.mercuriusxeno.goo.item.GooInsert;
import com.mercuriusxeno.goo.item.GooInteractionType;
import com.mercuriusxeno.goo.item.gasket.GasketInstallHelper;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Stateless dispatch and handler methods for vat block interactions: gasket install,
 * goo insert and the empty-hand unpack every other item falls through to.
 */
final class VatInteractionHandler {

    /** Error message prefix for an interaction type outside the vat's rows reaching dispatch. */
    private static final String ERR_UNHANDLED = "Unhandled interaction: ";

    private VatInteractionHandler() {
    }

    // --- Dispatch ---

    /**
     * Routes a vat row to its handler: a gasket installs on the vat itself, a goo pours
     * in. Every other item, a canister among them, never reaches here and falls through to the
     * empty-hand unpack (decision canister-click-is-any-other-click-on-crucible-and-vat).
     *
     * @param interaction the classified interaction, one of VatBlock.CLICK_ROWS
     * @param vat         the vat block entity
     * @param stack       the item stack
     * @param player      the interacting player
     * @param hand        the hand used
     * @param hitResult   the ray trace hit result
     * @param pos         the block position
     * @param level       the current level
     * @return the interaction result
     */
    static InteractionResult dispatchInteraction(
            GooInteractionType interaction, VatBlockEntity vat, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult, BlockPos pos, Level level) {
        return switch (interaction) {
            case GASKET_INSTALL -> handleGasketApply(vat, stack, player, hitResult);
            case GOO_INSERT -> handleGooInsert(vat, stack, player);
            default -> throw new IllegalStateException(ERR_UNHANDLED + interaction);
        };
    }

    // --- Gasket handlers ---

    /**
     * Applies a gasket to the cap or base face depending on where the player clicked.
     * Click on upper half or UP face -> cap gasket. Lower half or DOWN face -> base gasket.
     * Cannot apply to a face that is occluded by another vat or already has a gasket.
     *
     * @param vat       the vat block entity
     * @param stack     the item stack
     * @param player    the interacting player
     * @param hitResult the ray trace hit result
     * @return the interaction result
     */
    static InteractionResult handleGasketApply(
            VatBlockEntity vat, ItemStack stack,
            Player player, BlockHitResult hitResult) {
        BooleanProperty target = VatGasketOps.resolveGasketFace(hitResult, vat.getBlockPos());
        if (VatGasketOps.isFaceOccluded(vat.getBlockState(), target)) {
            return InteractionResult.PASS;
        }
        GasketRole role = target == VatBlock.GASKET_CAP ? GasketRole.RECEIVER : GasketRole.TRANSMITTER;
        if (!GasketInstallHelper.installBlockGasket(vat.getLevel(), vat.getBlockPos(), vat, role)) {
            return InteractionResult.PASS;
        }
        GooBlockInteraction.consumeOneHeld(stack, player);
        return InteractionResult.SUCCESS;
    }

    // --- Goo handlers ---

    /**
     * Inserts a goo or goo's volume into the vat.
     *
     * @param vat    the vat block entity
     * @param stack  the item stack
     * @param player the interacting player
     * @return the interaction result
     */
    static InteractionResult handleGooInsert(
            VatBlockEntity vat, ItemStack stack, Player player) {
        int accepted = GooInsert.pour(stack, player,
                (type, volume) -> volume > 0 && vat.canAccept() ? vat.insertGoo(type, volume) : 0);
        return accepted > 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /**
     * Unpacks every type the vat holds into the player's inventory; what finds no home stays
     * in the vat (decision vat-click-unpacks-into-inventory).
     *
     * @param vat    the vat block entity
     * @param player the interacting player
     * @return SUCCESS if any goo moved, else PASS
     */
    static InteractionResult handleGooExtract(VatBlockEntity vat, Player player) {
        boolean moved = GooDeposit.drainEveryType(vat.getContents().getAll(), vat::extractGoo,
                GooDeposit.intoInventory(player, ItemStack.EMPTY));
        return moved ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
