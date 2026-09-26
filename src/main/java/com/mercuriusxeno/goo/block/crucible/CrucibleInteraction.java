package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.item.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * Static helpers for crucible right-click interactions: the flint-and-steel spark,
 * canister collection, blob insertion, and goo extraction. Keeps framework overrides in CrucibleBlock.
 */
final class CrucibleInteraction {

    /** Lowest pitch of the spark sound, matching vanilla's flint and steel. */
    private static final float SPARK_PITCH_BASE = 0.8f;
    /** Pitch spread of the spark sound, matching vanilla's flint and steel. */
    private static final float SPARK_PITCH_SPREAD = 0.4f;

    private CrucibleInteraction() {
    }

    /**
     * Returns true if this item type would be handled by the crucible on the server.
     *
     * @param stack the item stack
     * @return true if the condition is met
     */
    static boolean wouldHandleItem(ItemStack stack) {
        return stack.is(Items.FLINT_AND_STEEL) || isGooCarrier(stack);
    }

    /**
     * Sparks a cold crucible with a flint and steel: adds the spark's heat, costs the tool
     * one durability and starts the ignition spray (decision flint-and-steel-sparks-the-crucible).
     *
     * @param stack    the held item stack
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @param hand     the hand holding the stack
     * @return SUCCESS when the spark lit a cold crucible, PASS when it already holds heat,
     *         null when the stack is no flint and steel
     */
    static @Nullable InteractionResult trySpark(ItemStack stack, CrucibleBlockEntity crucible,
                                                Player player, InteractionHand hand) {
        if (!stack.is(Items.FLINT_AND_STEEL)) {
            return null;
        }
        if (crucible.heatTicks() > 0) {
            return InteractionResult.PASS;
        }
        crucible.addHeat(GooConfig.SPARK_HEAT_TICKS.get());
        stack.hurtAndBreak(1, player, hand);
        Level level = crucible.getLevel();
        if (level != null) {
            level.playSound(null, crucible.getBlockPos(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
                    1.0f, level.getRandom().nextFloat() * SPARK_PITCH_SPREAD + SPARK_PITCH_BASE);
        }
        CrucibleMelting.beginIgnitionSpray(crucible);
        return InteractionResult.SUCCESS;
    }

    /**
     * Returns true if the stack holds a goo carrier item (canister or omniblob).
     *
     * @param stack the item stack to test
     * @return true if the item is a goo carrier
     */
    static boolean isGooCarrier(ItemStack stack) {
        return stack.getItem() instanceof CanisterItem
                || stack.getItem() instanceof GooOmniblobItem;
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
     * Inserts an omniblob directly into the reservoir (bypass, no fuel needed).
     *
     * @param stack    the item stack
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return true if the stack is an omniblob, inserted or refused at the cap; a refused
     *         omniblob still ends the click so it never falls through to goo extraction
     */
    static boolean tryInsertBlob(ItemStack stack, CrucibleBlockEntity crucible, Player player) {
        if (BlobStacks.volumeOf(stack) <= 0) {
            return false;
        }
        BlobInsert.pour(stack, player, (type, volume) -> acceptWholeUnits(crucible, type, volume));
        return true;
    }

    /**
     * Inserts the mB of the offered volume that fit the reservoir under the cap
     * (decision diagnose-then-fix-crucible-blob-duplication).
     *
     * @param crucible the crucible block entity to insert into
     * @param type     the goo type offered
     * @param volume   the volume offered, in mB
     * @return the volume inserted
     */
    private static int acceptWholeUnits(CrucibleBlockEntity crucible, ResourceKey<GooTypeDefinition> type,
                                        int volume) {
        int units = CrucibleInsertion.reservoirUnitsThatFit(crucible, type, 1, volume);
        return units > 0 ? crucible.insertGoo(type, units) : 0;
    }

    /**
     * Extracts the entire reservoir as one omniblob per goo type.
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
