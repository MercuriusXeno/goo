package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
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
import java.util.Map;

/**
 * Vat block item: retains goo contents when picked up (like shulker boxes).
 * Supports inventory click interactions matching CanisterItem behavior:
 * goo insert, goo drain.
 */
public class VatBlockItem extends BlockItem implements GooCarrierItem {

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
     * Handles cursor-on-vat inventory clicks: goo/goo insert,
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
            return CanisterInventoryHandler.drainIntoInventory(new VatGooSource(vat), GooDeposit.intoInventory(player, vat));
        }
        if (action != ClickAction.PRIMARY) {
            return false;
        }
        ResourceKey<GooTypeDefinition> label = GooLabelItem.typeOf(cursor);
        if (label != null) {
            return applyLabelFromCursor(vat, cursor, label);
        }
        return CanisterInventoryHandler.insertFromCursor(cursor, cursorAccess, (type, volume) -> addGoo(vat, type, volume));
    }

    /**
     * Puts the cursor's goo label on the vat item and takes one label from the cursor;
     * a refused label leaves both as they stood.
     * decision label-goes-on-an-empty-or-matching-vessel
     *
     * @param vat    the vat item stack in the slot
     * @param cursor the label stack on the cursor
     * @param label  the label's goo type
     * @return true when the label went on
     */
    private static boolean applyLabelFromCursor(ItemStack vat, ItemStack cursor, ResourceKey<GooTypeDefinition> label) {
        if (!applyGooLabel(vat, label)) {
            return false;
        }
        cursor.shrink(1);
        return true;
    }

    /**
     * Puts a goo label on a vat item where the label rule lets it on; an item carries no water.
     * decision label-goes-on-an-empty-or-matching-vessel
     *
     * @param vat  the vat item stack
     * @param type the label's goo type
     * @return true when the label went on
     */
    public static boolean applyGooLabel(ItemStack vat, ResourceKey<GooTypeDefinition> type) {
        if (!GooLabelRule.canLabelVat(getGooContents(vat), 0, getGooLabel(vat), type)) {
            return false;
        }
        vat.set(GooDataComponents.GOO_LABEL.get(), type);
        return true;
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
     * Returns the goo type the vat item's goo label holds it to, or null.
     *
     * @param stack the vat item stack
     * @return the goo label type
     */
    public static @Nullable ResourceKey<GooTypeDefinition> getGooLabel(ItemStack stack) {
        return stack.get(GooDataComponents.GOO_LABEL.get());
    }

    /**
     * Adds goo to the vat item, capped at the capacity its void grows. Returns amount accepted.
     *
     * @param stack  the vat item stack
     * @param type   the goo type to add
     * @param amount the volume to add
     * @return the amount actually accepted
     */
    public static int addGoo(ItemStack stack, ResourceKey<GooTypeDefinition> type, int amount) {
        return GooContentsOps.addGoo(stack, type, amount, ContainerCapacity.VAT_BASE);
    }

    /**
     * Removes goo of a specific type from the vat item. Returns amount removed.
     *
     * @param stack  the vat item stack
     * @param type   the goo type to remove
     * @param amount the volume to remove
     * @return the amount actually removed
     */
    public static int removeGoo(ItemStack stack, ResourceKey<GooTypeDefinition> type, int amount) {
        return GooContentsOps.removeGoo(stack, type, amount, ContainerCapacity.VAT_BASE);
    }

    // --- GooCarrierItem (decision hosts-answer-bounds-through-interfaces) ---

    @Override
    public DepletionPass depletionPass() {
        return DepletionPass.VAT;
    }

    @Override
    public Map<ResourceKey<GooTypeDefinition>, Integer> gooContents(ItemStack stack) {
        return getGooContents(stack).getAll();
    }

    @Override
    public int gooVolume(ItemStack stack, ResourceKey<GooTypeDefinition> type) {
        return getGooContents(stack).getVolume(type);
    }

    @Override
    public int drawGoo(ItemStack stack, ResourceKey<GooTypeDefinition> type, int amount) {
        return removeGoo(stack, type, amount);
    }

    /**
     * The vat item as a drain source: every goo type it holds, whole
     * (decisions vat-click-unpacks-into-inventory and vat-item-drain-shared).
     *
     * @param vat the vat item stack
     */
    private record VatGooSource(ItemStack vat) implements CanisterInventoryHandler.GooSource {
        @Override
        public Map<ResourceKey<GooTypeDefinition>, Integer> drainable() {
            return getGooContents(vat).getAll();
        }

        @Override
        public int remove(ResourceKey<GooTypeDefinition> type, int volume) {
            return removeGoo(vat, type, volume);
        }
    }
}
