package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.Objects;

/**
 * Pure utility for omniblob stack math. Centralizes volume calculations
 * and the machine output rule so all producers and consumers share one code path.
 */
public final class BlobStacks {

    /**
     * Volume of one blob in microblobs.
     */
    public static final int MB_PER_BLOB = 1000;

    private BlobStacks() {
    }

    /**
     * Returns the volume of the given item stack in microblobs: the omniblob's
     * BLOB_VOLUME, or 0 for any other item.
     *
     * @param stack the item stack to measure
     * @return volume in microblobs
     */
    public static int volumeOf(ItemStack stack) {
        if (stack.getItem() instanceof GooOmniblobItem) {
            return GooOmniblobItem.getVolume(stack);
        }
        return 0;
    }

    /**
     * Returns the goo type key of the given item stack, or null if not an omniblob.
     *
     * @param stack the item stack to inspect
     * @return the goo type key, or null
     */
    public static @Nullable ResourceKey<GooTypeDefinition> keyOf(ItemStack stack) {
        return stack.getItem() instanceof GooOmniblobItem ? stack.get(GooDataComponents.GOO_TYPE.get()) : null;
    }

    /**
     * Whether two stacks are omniblobs of the same goo type.
     *
     * @param a one stack
     * @param b another stack
     * @return true when both carry one type
     */
    public static boolean sameType(ItemStack a, ItemStack b) {
        ResourceKey<GooTypeDefinition> key = keyOf(a);
        return key != null && Objects.equals(key, keyOf(b));
    }

    /**
     * The volume a stack saved before blobs-become-omniblobs reads as: a goo:goo_blob
     * stack of count n carried no BLOB_VOLUME and held n x 1,000 mB. A stack of
     * count above 1 or with no stored volume reads count x 1,000 mB; any other
     * stack reads its stored volume.
     *
     * @param count        the stack's item count
     * @param storedVolume the BLOB_VOLUME component, or null when absent
     * @return the volume in microblobs
     */
    public static int legacyAwareVolume(int count, @Nullable Integer storedVolume) {
        if (storedVolume == null || count > 1) {
            return count * MB_PER_BLOB;
        }
        return storedVolume;
    }

    /**
     * Creates the omniblob carrying the given volume, the one goo item at every
     * volume (decision blobs-become-omniblobs).
     *
     * @param key      the goo type's registry key
     * @param volumeMb volume in microblobs
     * @return the omniblob, or EMPTY for a volume of zero or less
     */
    public static ItemStack createForOutput(ResourceKey<GooTypeDefinition> key, int volumeMb) {
        if (volumeMb <= 0) {
            return ItemStack.EMPTY;
        }
        return GooOmniblobItem.createWithVolume(key, volumeMb);
    }

    /**
     * Returns the number of whole blobs in the given volume.
     *
     * @param volumeMb volume in microblobs
     * @return number of whole blobs
     */
    public static int wholeBlobs(int volumeMb) {
        return volumeMb / MB_PER_BLOB;
    }

    /**
     * Depletes an omniblob by the accepted volume, consuming the stack if empty.
     *
     * @param stack    the omniblob item stack to deplete
     * @param accepted the volume in microblobs that was accepted
     * @param player   the player holding the stack (for consume callback)
     */
    public static void deplete(ItemStack stack, int accepted, Player player) {
        if (stack.getItem() instanceof GooOmniblobItem) {
            depleteOmniblob(stack, accepted, player);
        }
    }

    /**
     * Deducts volume from an omniblob, consuming the stack if empty.
     *
     * @param stack    the omniblob stack
     * @param accepted the accepted volume in microblobs
     * @param player   the player holding the stack
     */
    private static void depleteOmniblob(ItemStack stack, int accepted, Player player) {
        int remaining = GooOmniblobItem.getVolume(stack) - accepted;
        if (remaining <= 0) {
            stack.consume(1, player);
        } else {
            GooOmniblobItem.setVolume(stack, remaining);
        }
    }

    /**
     * Pure math: the new omniblob sink volume after absorbing a source volume.
     * Extracted so the arithmetic is covered by tests without bootstrapping Minecraft.
     *
     * @param sourceVolumeMb   volume being absorbed, in microblobs (>=0)
     * @param omniblobVolumeMb current omniblob sink volume, in microblobs (>=0)
     * @return the combined volume in microblobs
     */
    public static int absorbedVolume(int sourceVolumeMb, int omniblobVolumeMb) {
        return omniblobVolumeMb + sourceVolumeMb;
    }

    /**
     * Dumps the entire volume of {@code source} (an omniblob of any goo type)
     * into the omniblob sitting in {@code omniblobStack}. Clears {@code source} by setting
     * its count to 0. Caller is responsible for {@code slot.setChanged()} if the sink
     * lives in a container slot.
     *
     * <p>No type check is performed here - the caller must verify that the two stacks
     * share a goo type before calling.</p>
     *
     * @param source        the source goo stack; count becomes 0 on return
     * @param omniblobStack the sink omniblob stack; volume is grown in place
     */
    public static void absorbIntoOmniblobSlot(ItemStack source, ItemStack omniblobStack) {
        int total = absorbedVolume(volumeOf(source), GooOmniblobItem.getVolume(omniblobStack));
        GooOmniblobItem.setVolume(omniblobStack, total);
        source.setCount(0);
    }

    /**
     * Merges goo volume into a player's inventory, stacking with existing items.
     * Adds to the first matching omniblob, or creates one for the volume.
     *
     * @param player   the player to receive the goo
     * @param type     the goo type
     * @param volumeMb volume in microblobs
     */
    public static void mergeIntoInventory(Player player, ResourceKey<GooTypeDefinition> type, int volumeMb) {
        if (volumeMb <= 0) {
            return;
        }
        int remaining = mergeIntoExistingOmniblobs(player, type, volumeMb);
        if (remaining > 0) {
            PlayerUtils.addOrDrop(player, createForOutput(type, remaining));
        }
    }

    /**
     * Adds volume to the first matching omniblob found in the inventory.
     *
     * @param player   the player whose inventory to scan
     * @param type     the goo type to match
     * @param volumeMb volume to merge in microblobs
     * @return remaining volume not merged (0 if fully absorbed)
     */
    private static int mergeIntoExistingOmniblobs(Player player, ResourceKey<GooTypeDefinition> type, int volumeMb) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.getItem() instanceof GooOmniblobItem && keyOf(slot) == type) {
                GooOmniblobItem.setVolume(slot, GooOmniblobItem.getVolume(slot) + volumeMb);
                return 0;
            }
        }
        return volumeMb;
    }

    /**
     * Drops all goo in a {@link GooContents} as omniblobs at the given position, one per goo type.
     *
     * @param contents the goo contents to drop
     * @param level    the world
     * @param pos      the position to drop items at
     */
    public static void dropAll(GooContents contents, Level level, BlockPos pos) {
        if (contents.isEmpty()) {
            return;
        }
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : contents.getAll().entrySet()) {
            Block.popResource(level, pos, createForOutput(entry.getKey(), entry.getValue()));
        }
    }
}
