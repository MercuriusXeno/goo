package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Scans a player's inventory for all goo sources and aggregates
 * available volume per goo type. Handles depletion in priority order:
 * loose blobs → omniblobs → canisters (a hub item's included) → vats, bottom-up slot index.
 *
 * <p>Slot coverage: main inventory (0-35) plus offhand (40). Armor slots
 * are excluded - you can't throw goo from your chestplate.</p>
 */
public final class GooSourceScanner {

    /**
     * Main inventory: slots 0-35.
     */
    private static final int MAIN_START = 0;
    private static final int MAIN_END = 36;
    /**
     * Offhand slot index in Inventory.
     */
    private static final int OFFHAND_SLOT = Inventory.SLOT_OFFHAND;
    /**
     * The passes a deplete runs, in order: omniblobs, canisters (a hub item's included), vats.
     */
    private static final List<Class<?>> DEPLETION_PASSES =
            List.of(GooOmniblobItem.class, CanisterItem.class, VatBlockItem.class);
    /**
     * The slots each pass walks, in order: main inventory bottom-up, then offhand.
     */
    private static final int[] DEPLETION_SLOTS = IntStream.concat(
            IntStream.range(MAIN_START, MAIN_END), IntStream.of(OFFHAND_SLOT)).toArray();

    private GooSourceScanner() {
    }

    /**
     * Aggregates available mB per goo type across all inventory sources.
     * Used by radial menu to show throwable quantities.
     *
     * @param player the player whose inventory to scan
     * @return map of goo type to total available mB
     */
    public static Map<ResourceKey<GooTypeDefinition>, Integer> aggregateAvailable(Player player) {
        Map<ResourceKey<GooTypeDefinition>, Integer> totals = new HashMap<>();
        Inventory inv = player.getInventory();

        for (int i = MAIN_START; i < MAIN_END; i++) {
            scanStack(inv.getItem(i), totals);
        }
        scanStack(inv.getItem(OFFHAND_SLOT), totals);

        return totals;
    }

    /**
     * Depletes the specified amount of goo from the player's inventory.
     * Follows priority: blobs → omniblobs → canisters → vats, bottom-up slots.
     * Returns the amount actually depleted (may be less than requested if insufficient).
     *
     * @param player the player whose inventory to deplete from
     * @param type   the goo type to deplete
     * @param amount the amount in mB to deplete
     * @return actual mB depleted
     */
    public static int deplete(Player player, ResourceKey<GooTypeDefinition> type, int amount) {
        if (amount <= 0) {
            return 0;
        }

        int remaining = depleteAllPasses(player.getInventory(), type, amount);
        return amount - remaining;
    }

    /**
     * Runs all four priority passes in order: blobs, omniblobs, canisters, vats.
     *
     * @param inv       the player inventory
     * @param type      the goo type to deplete
     * @param remaining the amount still to deplete
     * @return the amount remaining after all passes
     */
    private static int depleteAllPasses(Inventory inv, ResourceKey<GooTypeDefinition> type, int remaining) {
        int left = remaining;
        for (Class<?> pass : DEPLETION_PASSES) {
            if (left <= 0) {
                break;
            }
            left = depletePass(inv, type, left, pass);
        }
        return left;
    }

    /**
     * Names the stack the next deplete of a goo type draws from first,
     * walking the passes and slots deplete walks (decision
     * crosshair-panel-shows-source-and-cost).
     *
     * @param player the player whose inventory to scan
     * @param type   the goo type
     * @return the stack, or {@link ItemStack#EMPTY} when the player holds none of the type
     */
    public static ItemStack firstSource(Player player, ResourceKey<GooTypeDefinition> type) {
        Inventory inv = player.getInventory();
        for (Class<?> pass : DEPLETION_PASSES) {
            for (int slot : DEPLETION_SLOTS) {
                ItemStack stack = inv.getItem(slot);
                if (drawsFrom(stack, type, pass)) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * The volume of a goo type a single stack holds, loose or in a carrier.
     *
     * @param stack the item stack
     * @param type  the goo type
     * @return the volume in mB
     */
    public static int volumeIn(ItemStack stack, ResourceKey<GooTypeDefinition> type) {
        return volumeOfType(stack, type);
    }

    /**
     * Whether a depletion pass draws any of a type from a stack.
     *
     * @param stack the item stack
     * @param type  the goo type
     * @param pass  the item class of the pass
     * @return true when the pass would shrink the stack
     */
    private static boolean drawsFrom(ItemStack stack, ResourceKey<GooTypeDefinition> type, Class<?> pass) {
        if (stack.isEmpty()) {
            return false;
        }
        boolean loosePass = pass == GooOmniblobItem.class;
        return loosePass
                ? pass.isInstance(stack.getItem()) && looseGooVolume(stack, type) > 0
                : carrierDrawsFrom(stack, type, pass);
    }

    /**
     * Whether a carrier pass draws any of a type from a carrier stack.
     *
     * @param stack the item stack
     * @param type  the goo type
     * @param pass  the item class of the pass
     * @return true when the stack is a carrier of that pass holding the type
     */
    private static boolean carrierDrawsFrom(ItemStack stack, ResourceKey<GooTypeDefinition> type, Class<?> pass) {
        GooCarrier carrier = GooCarrier.of(stack);
        return carrier != null && carrier.depletionPass() == pass && carrier.volumeOf(type) > 0;
    }

    /**
     * Checks if the player has at least the specified amount of a goo type.
     * Early-exit version of aggregate - stops scanning when threshold is met.
     *
     * @param player the player to check
     * @param type   the goo type
     * @param amount minimum mB required
     * @return true if sufficient goo is available
     */
    public static boolean hasEnough(Player player, ResourceKey<GooTypeDefinition> type, int amount) {
        return amount <= 0 || scanForThreshold(player.getInventory(), type, amount) >= amount;
    }

    /**
     * Scans inventory for volume of a specific type, stopping early once threshold is met.
     *
     * @param inv       the player inventory
     * @param type      the goo type
     * @param threshold minimum mB to find before stopping
     * @return total mB found (may be less than threshold if insufficient)
     */
    private static int scanForThreshold(Inventory inv, ResourceKey<GooTypeDefinition> type, int threshold) {
        int found = 0;
        for (int i = MAIN_START; i < MAIN_END && found < threshold; i++) {
            found += volumeOfType(inv.getItem(i), type);
        }
        if (found < threshold) {
            found += volumeOfType(inv.getItem(OFFHAND_SLOT), type);
        }
        return found;
    }

    // --- Private scanning helpers ---

    /**
     * Aggregates all goo from a single stack into the totals map.
     *
     * @param stack  the item stack to scan
     * @param totals the running totals map
     */
    private static void scanStack(ItemStack stack, Map<ResourceKey<GooTypeDefinition>, Integer> totals) {
        if (stack.isEmpty()) {
            return;
        }

        if (stack.getItem() instanceof GooOmniblobItem) {
            ResourceKey<GooTypeDefinition> looseType = BlobStacks.keyOf(stack);
            if (looseType != null) {
                addToMap(totals, looseType, BlobStacks.volumeOf(stack));
            }
        } else {
            scanContainerStack(stack, totals);
        }
    }

    /**
     * Scans a goo carrier (canister, hub or vat item) for all contained goo types.
     *
     * @param stack  the item stack to scan
     * @param totals the running totals map
     */
    private static void scanContainerStack(ItemStack stack, Map<ResourceKey<GooTypeDefinition>, Integer> totals) {
        GooCarrier carrier = GooCarrier.of(stack);
        if (carrier != null) {
            addAllEntries(totals, carrier.contents());
        }
    }

    /**
     * Merges all entries from a goo contents map into the running totals.
     *
     * @param totals  the running totals map
     * @param entries the entries to merge
     */
    private static void addAllEntries(Map<ResourceKey<GooTypeDefinition>, Integer> totals, Map<ResourceKey<GooTypeDefinition>, Integer> entries) {
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> e : entries.entrySet()) {
            addToMap(totals, e.getKey(), e.getValue());
        }
    }

    /**
     * Returns the volume of the given type in a single stack.
     *
     * @param stack the item stack to inspect
     * @param type  the goo type to look for
     * @return volume in microblobs
     */
    private static int volumeOfType(ItemStack stack, ResourceKey<GooTypeDefinition> type) {
        if (stack.isEmpty()) {
            return 0;
        }
        int loose = looseGooVolume(stack, type);
        return loose > 0 ? loose : containerVolumeOfType(stack, type);
    }

    /**
     * Returns volume from loose goo items (blobs or omniblobs) of the given type.
     *
     * @param stack the item stack to inspect
     * @param type  the goo type to match
     * @return volume in microblobs, or 0 if not a matching loose goo
     */
    private static int looseGooVolume(ItemStack stack, ResourceKey<GooTypeDefinition> type) {
        return BlobStacks.keyOf(stack) == type ? BlobStacks.volumeOf(stack) : 0;
    }

    /**
     * Returns volume of a specific type from a goo carrier (canister, hub or vat item).
     *
     * @param stack the item stack to inspect
     * @param type  the goo type to look for
     * @return volume in microblobs, or 0 if not a carrier
     */
    private static int containerVolumeOfType(ItemStack stack, ResourceKey<GooTypeDefinition> type) {
        GooCarrier carrier = GooCarrier.of(stack);
        return carrier != null ? carrier.volumeOf(type) : 0;
    }

    // --- Depletion dispatch ---

    /**
     * Runs one depletion pass across main inventory + offhand for a specific
     * source class. Bottom-up slot order (slot 0 first).
     *
     * @param inv         the player inventory
     * @param type        the goo type to deplete
     * @param remaining   the remaining amount to deplete
     * @param sourceClass the item class to target in this pass
     * @return the remaining amount after this pass
     */
    private static int depletePass(Inventory inv, ResourceKey<GooTypeDefinition> type, int remaining, Class<?> sourceClass) {
        int left = remaining;
        for (int slot : DEPLETION_SLOTS) {
            if (left <= 0) {
                break;
            }
            left = depleteStack(inv.getItem(slot), type, left, sourceClass);
        }
        return left;
    }

    /**
     * Depletes from a single stack if it matches the source class and type.
     *
     * @param stack       the item stack to deplete from
     * @param type        the goo type to deplete
     * @param remaining   the remaining amount to deplete
     * @param sourceClass the item class to match
     * @return the remaining amount after depletion
     */
    private static int depleteStack(ItemStack stack, ResourceKey<GooTypeDefinition> type, int remaining, Class<?> sourceClass) {
        if (stack.isEmpty()) {
            return remaining;
        }

        if (sourceClass == GooOmniblobItem.class) {
            return depleteOmniblobStack(stack, type, remaining);
        }
        return depleteContainerStack(stack, type, remaining, sourceClass);
    }

    /**
     * Depletes partial volume from a matching omniblob stack.
     *
     * @param stack     the item stack to deplete from
     * @param type      the goo type to match
     * @param remaining the amount still to deplete
     * @return the remaining amount after depletion
     */
    private static int depleteOmniblobStack(ItemStack stack, ResourceKey<GooTypeDefinition> type, int remaining) {
        if (!(stack.getItem() instanceof GooOmniblobItem) || BlobStacks.keyOf(stack) != type) {
            return remaining;
        }
        int volume = GooOmniblobItem.getVolume(stack);
        int take = Math.min(remaining, volume);
        reduceOmniblobVolume(stack, volume - take);
        return remaining - take;
    }

    /**
     * Sets omniblob volume to the new value, or destroys the stack if depleted.
     *
     * @param stack     the omniblob item stack
     * @param newVolume the volume to set (destroyed if <= 0)
     */
    private static void reduceOmniblobVolume(ItemStack stack, int newVolume) {
        if (newVolume <= 0) {
            stack.setCount(0);
        } else {
            GooOmniblobItem.setVolume(stack, newVolume);
        }
    }

    /**
     * Depletes from a goo carrier whose depletion pass is the one running.
     *
     * @param stack       the item stack to deplete from
     * @param type        the goo type to deplete
     * @param remaining   the amount still to deplete
     * @param sourceClass the item class of the running pass
     * @return the remaining amount after depletion
     */
    private static int depleteContainerStack(ItemStack stack, ResourceKey<GooTypeDefinition> type, int remaining, Class<?> sourceClass) {
        GooCarrier carrier = GooCarrier.of(stack);
        if (carrier == null || carrier.depletionPass() != sourceClass) {
            return remaining;
        }
        return remaining - carrier.remove(type, remaining);
    }

    // --- Util ---

    /**
     * Adds the given amount to the running total for the given type.
     *
     * @param map    the running totals map
     * @param type   the goo type
     * @param amount the amount to add
     */
    private static void addToMap(Map<ResourceKey<GooTypeDefinition>, Integer> map, ResourceKey<GooTypeDefinition> type, int amount) {
        map.merge(type, amount, Integer::sum);
    }
}
