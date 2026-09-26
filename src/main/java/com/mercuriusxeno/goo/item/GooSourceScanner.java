package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.item.GooCarrierItem.DepletionPass;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Scans a player's inventory for all goo sources and aggregates available
 * volume per goo type, reading every stack through {@link GooCarrierItem}
 * (decision hosts-answer-bounds-through-interfaces). Depletion runs the
 * carriers' passes in {@link DepletionPass} order, bottom-up slot index.
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
     * The slots each pass walks, in order: main inventory bottom-up, then offhand.
     */
    private static final int[] SCAN_SLOTS = IntStream.concat(
            IntStream.range(MAIN_START, MAIN_END), IntStream.of(OFFHAND_SLOT)).toArray();

    private GooSourceScanner() {
    }

    /**
     * The carrier a stack's item is, the one dispatch every scan path shares.
     *
     * @param stack the item stack
     * @return the carrier, or null for an empty stack or an item carrying no goo
     */
    private static @Nullable GooCarrierItem carrierOf(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof GooCarrierItem carrier ? carrier : null;
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
        for (int slot : SCAN_SLOTS) {
            ItemStack stack = inv.getItem(slot);
            GooCarrierItem carrier = carrierOf(stack);
            if (carrier != null) {
                carrier.gooContents(stack).forEach((type, volume) -> totals.merge(type, volume, Integer::sum));
            }
        }
        return totals;
    }

    /**
     * Depletes the specified amount of goo from the player's inventory, pass by
     * pass in {@link DepletionPass} order. Returns the amount actually depleted,
     * less than requested when the inventory runs short.
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
        Inventory inv = player.getInventory();
        int left = amount;
        for (DepletionPass pass : DepletionPass.values()) {
            left = drawPass(inv, pass, type, left);
        }
        return amount - left;
    }

    /**
     * Draws from each carrier of one pass, bottom-up, until nothing is left to draw.
     *
     * @param inv  the player inventory
     * @param pass the pass running
     * @param type the goo type to draw
     * @param left the mB still wanted
     * @return the mB still wanted after the pass
     */
    private static int drawPass(Inventory inv, DepletionPass pass, ResourceKey<GooTypeDefinition> type, int left) {
        int wanted = left;
        for (int slot : SCAN_SLOTS) {
            if (wanted <= 0) {
                break;
            }
            ItemStack stack = inv.getItem(slot);
            GooCarrierItem carrier = carrierOf(stack);
            if (carrier != null && carrier.depletionPass() == pass) {
                wanted -= carrier.drawGoo(stack, type, wanted);
            }
        }
        return wanted;
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
        for (DepletionPass pass : DepletionPass.values()) {
            for (int slot : SCAN_SLOTS) {
                ItemStack stack = inv.getItem(slot);
                GooCarrierItem carrier = carrierOf(stack);
                if (carrier != null && carrier.depletionPass() == pass && carrier.gooVolume(stack, type) > 0) {
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
        GooCarrierItem carrier = carrierOf(stack);
        return carrier != null ? carrier.gooVolume(stack, type) : 0;
    }

    /**
     * Checks if the player has at least the specified amount of a goo type,
     * stopping the scan once the threshold is met.
     *
     * @param player the player to check
     * @param type   the goo type
     * @param amount minimum mB required
     * @return true if sufficient goo is available
     */
    public static boolean hasEnough(Player player, ResourceKey<GooTypeDefinition> type, int amount) {
        if (amount <= 0) {
            return true;
        }
        Inventory inv = player.getInventory();
        int found = 0;
        for (int slot : SCAN_SLOTS) {
            found += volumeIn(inv.getItem(slot), type);
            if (found >= amount) {
                return true;
            }
        }
        return false;
    }
}
