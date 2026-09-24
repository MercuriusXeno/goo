package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Vat block item: retains goo contents when picked up (like shulker boxes).
 * Supports inventory click interactions matching CanisterItem behavior:
 * blob insert, blob drain.
 */
public class VatBlockItem extends BlockItem {

    /**
     * Creates a vat block item for the given block.
     *
     * @param block      the vat block
     * @param properties the item properties
     */
    public VatBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    /**
     * Handles cursor-on-vat inventory clicks: blob/omniblob insert,
     * empty-cursor drain.
     *
     * @param vat         the vat item stack in the slot
     * @param cursor      the item stack on the cursor
     * @param slot        the inventory slot
     * @param action      the click action
     * @param player      the interacting player
     * @param cursorAccess access to set the cursor contents
     * @return true if the interaction was handled
     */
    @Override
    public boolean overrideOtherStackedOnMe(@NonNull ItemStack vat, @NonNull ItemStack cursor,
            @NonNull Slot slot, @NonNull ClickAction action, @NonNull Player player,
            @NonNull SlotAccess cursorAccess) {
        if (cursor.isEmpty() && action == ClickAction.SECONDARY) {
            return CanisterInventoryHandler.drainToCursor(cursorAccess, new VatGooSource(vat));
        }
        return action == ClickAction.PRIMARY && CanisterInventoryHandler.insertFromCursor(
                cursor, cursorAccess, (type, volume) -> addGoo(vat, type, volume));
    }

    // --- Goo contents helpers (vat-specific capacity) ---

    /**
     * Returns the goo contents from the vat item, or EMPTY if none.
     *
     * @param stack the item stack
     * @return the goo contents, never null
     */
    public static GooContents getGooContents(ItemStack stack) {
        GooContents contents = stack.get(GooDataComponents.GOO_CONTENTS.get());
        return contents != null ? contents : GooContents.EMPTY;
    }

    /**
     * Sets the goo contents on the vat item. Removes component if empty.
     *
     * @param stack    the item stack
     * @param contents the goo contents to set
     */
    public static void setGooContents(ItemStack stack, GooContents contents) {
        if (contents.isEmpty()) {
            stack.remove(GooDataComponents.GOO_CONTENTS.get());
        } else {
            stack.set(GooDataComponents.GOO_CONTENTS.get(), contents);
        }
    }

    /**
     * Adds goo to the vat item, capped at vat capacity. Returns amount accepted.
     *
     * @param stack  the vat item stack
     * @param type   the goo type to add
     * @param amount the volume in microblobs to add
     * @return the amount actually accepted
     */
    public static int addGoo(ItemStack stack, ResourceKey<GooTypeDefinition> type, int amount) {
        int capacity = ContainerCapacity.vatCapacity(GooEnchantments.getCompressionLevel(stack));
        return GooContentsOps.addGoo(stack, type, amount, capacity);
    }

    /**
     * Removes goo of a specific type from the vat item. Returns amount removed.
     *
     * @param stack  the vat item stack
     * @param type   the goo type to remove
     * @param amount the volume in microblobs to remove
     * @return the amount actually removed
     */
    public static int removeGoo(ItemStack stack, ResourceKey<GooTypeDefinition> type, int amount) {
        return GooContentsOps.removeGoo(stack, type, amount);
    }

    /**
     * The vat item as a drain source: its largest goo type, removed up to what it holds.
     *
     * @param vat the vat item stack
     */
    private record VatGooSource(ItemStack vat) implements CanisterInventoryHandler.GooSource {
        @Override
        public @Nullable ResourceKey<GooTypeDefinition> dominantType() {
            return getGooContents(vat).largestType();
        }

        @Override
        public int remove(ResourceKey<GooTypeDefinition> type, int volume) {
            return removeGoo(vat, type, volume);
        }
    }
}
