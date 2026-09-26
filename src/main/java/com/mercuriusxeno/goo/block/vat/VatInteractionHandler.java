package com.mercuriusxeno.goo.block.vat;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.item.BlobInsert;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import com.mercuriusxeno.goo.item.gasket.ChoralGasketItem;
import com.mercuriusxeno.goo.item.gasket.GasketInstallHelper;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Stateless dispatch and handler methods for vat block interactions:
 * item-use and empty-hand logic.
 */
final class VatInteractionHandler {

    /**
     * Block update flags: notify neighbors + send to clients.
     */
    private static final int BLOCK_UPDATE_FLAGS = 3;
    /** The volume one empty-hand extract takes: 64 blobs. */
    private static final int EXTRACT_VOLUME = 64 * BlobStacks.MB_PER_BLOB;

    private VatInteractionHandler() {
    }

    // --- Dispatch ---

    /**
     * Server-side instanceof dispatch chain for item interactions.
     *
     * @param vat       the vat block entity
     * @param stack     the item stack
     * @param player    the interacting player
     * @param hand      the hand used
     * @param hitResult the ray trace hit result
     * @return the interaction result
     */
    static InteractionResult dispatchInteraction(
            VatBlockEntity vat, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        InteractionResult result = dispatchGasketOrBlob(vat, stack, player, hitResult);
        if (result != null) {
            return result;
        }
        result = VatFluidInteraction.dispatchFluidContainers(vat, stack, player, hand);
        if (result != null) {
            return result;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /**
     * Dispatches gasket apply and blob insert interactions.
     *
     * @param vat       the vat block entity
     * @param stack     the item stack
     * @param player    the interacting player
     * @param hitResult the ray trace hit result
     * @return the interaction result, or null if no match
     */
    @Nullable
    private static InteractionResult dispatchGasketOrBlob(
            VatBlockEntity vat, ItemStack stack,
            Player player, BlockHitResult hitResult) {
        if (stack.getItem() instanceof ChoralGasketItem) {
            return handleGasketApply(vat, stack, player, hitResult);
        }
        if (stack.getItem() instanceof GooOmniblobItem) {
            return handleBlobInsert(vat, stack, player);
        }
        return null;
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
        consumeIfSurvival(stack, player);
        return InteractionResult.SUCCESS;
    }

    /**
     * Shrinks the stack by one unless the player is in creative mode.
     *
     * @param stack  the item stack to consume from
     * @param player the interacting player
     */
    private static void consumeIfSurvival(ItemStack stack, Player player) {
        if (!player.isCreative()) {
            stack.shrink(1);
        }
    }

    // --- Blob handlers ---

    /**
     * Inserts a blob or omniblob's volume into the vat.
     *
     * @param vat    the vat block entity
     * @param stack  the item stack
     * @param player the interacting player
     * @return the interaction result
     */
    static InteractionResult handleBlobInsert(
            VatBlockEntity vat, ItemStack stack, Player player) {
        int accepted = BlobInsert.pour(stack, player,
                (type, volume) -> volume > 0 && vat.canAccept() ? vat.insertGoo(type, volume) : 0);
        return accepted > 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /**
     * Extracts 64,000 mB of the dominant type from the vat into the player's inventory.
     *
     * @param vat    the vat block entity
     * @param player the interacting player
     * @return the interaction result
     */
    static InteractionResult handleBlobExtract(VatBlockEntity vat, Player player) {
        ResourceKey<GooTypeDefinition> dominant = VatFluidInteraction.extractableDominant(vat);
        if (dominant == null) {
            return InteractionResult.PASS;
        }
        int extractAmount = Math.min(vat.getContents().getVolume(dominant), EXTRACT_VOLUME);
        int extracted = vat.extractGoo(dominant, extractAmount);
        if (extracted <= 0) {
            return InteractionResult.PASS;
        }

        ItemStack output = BlobStacks.createForOutput(dominant, extracted);
        PlayerUtils.addOrDrop(player, output);
        return InteractionResult.SUCCESS;
    }

}
