package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.item.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Map;

/**
 * Static helpers for crucible right-click interactions: fuel insertion,
 * canister collection, blob insertion, fuel removal,
 * and goo extraction. Keeps framework overrides in CrucibleBlock.
 */
final class CrucibleInteraction {

    private CrucibleInteraction() {
    }

    /**
     * Returns true if this item type would be handled by the crucible on the server.
     *
     * @param stack the item stack
     * @return true if the condition is met
     */
    static boolean wouldHandleItem(ItemStack stack) {
        return isFuelItem(stack)
                || isGooCarrier(stack);
    }

    /**
     * Returns true if the stack holds a goo carrier item (canister, blob, or omniblob).
     *
     * @param stack the item stack to test
     * @return true if the item is a goo carrier
     */
    static boolean isGooCarrier(ItemStack stack) {
        return stack.getItem() instanceof CanisterItem
                || stack.getItem() instanceof GooBlobItem
                || stack.getItem() instanceof GooOmniblobItem;
    }

    /**
     * Returns true if the item can be inserted as crucible fuel.
     *
     * @param stack the item stack
     * @return true if the stack is a fuel item
     */
    static boolean isFuelItem(ItemStack stack) {
        return stack.is(Items.BLAZE_ROD) || stack.getItem() instanceof DepletedBlazeRodItem;
    }

    /**
     * Inserts a fuel rod only if the platform is empty.
     *
     * @param stack    the item stack
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return true if fuel was inserted
     */
    static boolean tryInsertFuel(ItemStack stack, CrucibleBlockEntity crucible,
                                 Player player) {
        if (!isFuelItem(stack)) {
            return false;
        }
        if (crucible.hasFuel()) {
            return false;
        }
        crucible.addFuel(stack);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return true;
    }

    /**
     * Collects matching goo type from the reservoir into a canister.
     *
     * @param stack    the item stack
     * @param crucible the crucible block entity
     * @return true if goo was collected
     */
    static boolean tryCollectWithCanister(ItemStack stack, CrucibleBlockEntity crucible) {
        if (!(stack.getItem() instanceof CanisterItem)) {
            return false;
        }
        GooContents res = crucible.getReservoir();
        if (res.isEmpty()) {
            return false;
        }
        ResourceKey<GooTypeDefinition> type = res.largestType();
        return type != null && transferDominantGoo(stack, crucible, type, res.getVolume(type));
    }

    /**
     * Transfers the dominant goo type from the crucible reservoir into the canister.
     *
     * @param canister  the canister item stack to fill
     * @param crucible  the crucible block entity to drain from
     * @param type      the dominant goo type to transfer
     * @param available the volume available in the reservoir (mB)
     * @return true if any goo was transferred
     */
    private static boolean transferDominantGoo(ItemStack canister, CrucibleBlockEntity crucible,
                                               ResourceKey<GooTypeDefinition> type, int available) {
        int added = CanisterItem.addGoo(canister, type, available);
        if (added <= 0) {
            return false;
        }
        crucible.extractGoo(type, added);
        return true;
    }

    /**
     * Inserts a goo blob or omniblob directly into the reservoir (bypass, no fuel needed).
     *
     * @param stack    the item stack
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return true if the stack is a blob, inserted or refused at the cap; a refused
     *         blob still ends the click so it never falls through to goo extraction
     */
    static boolean tryInsertBlob(ItemStack stack, CrucibleBlockEntity crucible, Player player) {
        ResourceKey<GooTypeDefinition> type = BlobStacks.keyOf(stack);
        if (type == null) {
            return false;
        }
        int volume = BlobStacks.volumeOf(stack);
        if (volume <= 0) {
            return false;
        }
        consumeBlobIntoCrucible(stack, crucible, player, type, volume);
        return true;
    }

    /**
     * Inserts the blob's goo into the crucible when its whole volume fits the
     * reservoir, and consumes the item if not creative; a blob that does not
     * fit stays in hand (decision crucible-refuses-past-two-billion).
     *
     * @param stack    the blob item stack to consume
     * @param crucible the crucible block entity to insert into
     * @param player   the interacting player (creative skips consumption)
     * @param type     the goo type of the blob
     * @param volume   the volume of goo in the blob (mB)
     * @return true if the blob was inserted, false if refused at the cap
     */
    static boolean consumeBlobIntoCrucible(ItemStack stack, CrucibleBlockEntity crucible,
                                           Player player, ResourceKey<GooTypeDefinition> type, int volume) {
        if (CrucibleInsertion.reservoirUnitsThatFit(crucible, type, volume, 1) < 1) {
            return false;
        }
        crucible.insertGoo(type, volume);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return true;
    }

    /**
     * Removes the fuel rod from the crucible and gives it to the player.
     *
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return the result
     */
    static InteractionResult tryRemoveFuelRod(CrucibleBlockEntity crucible, Player player) {
        ItemStack rod = crucible.removeFuelRod();
        if (rod.isEmpty()) {
            return InteractionResult.PASS;
        }
        PlayerUtils.addOrDrop(player, rod);
        return InteractionResult.SUCCESS;
    }

    /**
     * Extracts the entire reservoir as one item per goo type (blob stack or omniblob).
     *
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return the result
     */
    static InteractionResult tryExtractGoo(CrucibleBlockEntity crucible, Player player) {
        GooContents res = crucible.getReservoir();
        if (res.isEmpty()) {
            return InteractionResult.PASS;
        }

        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : res.getAll().entrySet()) {
            BlobStacks.mergeIntoInventory(player, entry.getKey(), entry.getValue());
        }
        crucible.drainReservoir();
        return InteractionResult.SUCCESS;
    }
}
