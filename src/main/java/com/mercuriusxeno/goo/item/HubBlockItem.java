package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hub block item: carries its canisters in HUB_CANISTERS. A primary click with a
 * blob or omniblob on the cursor routes the goo into those canisters, type match
 * first then empty (decision hub-item-blob-insert).
 *
 * <p>The hub item takes insert and no drain: no click names which of its canisters
 * to drain, so an empty-cursor secondary click is left to vanilla, which picks the
 * hub up (decision hub-item-insert-only).</p>
 */
public class HubBlockItem extends BlockItem implements GooCarrierItem {

    /**
     * Creates a hub block item for the given block.
     *
     * @param block      the hub block
     * @param properties the item properties
     */
    public HubBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    // --- GooCarrierItem (decision hosts-answer-bounds-through-interfaces) ---

    /**
     * The hub's carried canisters drain in the canister pass.
     */
    @Override
    public DepletionPass depletionPass() {
        return DepletionPass.CANISTER;
    }

    @Override
    public Map<ResourceKey<GooTypeDefinition>, Integer> gooContents(ItemStack stack) {
        Map<ResourceKey<GooTypeDefinition>, Integer> totals = new HashMap<>();
        for (ItemStack canister : carried(stack)) {
            CanisterItem.gooContentsOf(canister).forEach((type, volume) -> totals.merge(type, volume, Integer::sum));
        }
        return totals;
    }

    /**
     * Drains copies of the hub's canisters in order and writes them back once any gave goo.
     */
    @Override
    public int drawGoo(ItemStack stack, ResourceKey<GooTypeDefinition> type, int amount) {
        List<ItemStack> copies = new ArrayList<>();
        int removed = 0;
        for (ItemStack canister : carried(stack)) {
            ItemStack copy = canister.copy();
            removed += CanisterItem.removeGoo(copy, type, amount - removed);
            copies.add(copy);
        }
        if (removed > 0) {
            stack.set(GooDataComponents.HUB_CANISTERS.get(), copies);
        }
        return removed;
    }

    private static List<ItemStack> carried(ItemStack hub) {
        return hub.getOrDefault(GooDataComponents.HUB_CANISTERS.get(), List.of());
    }

    /**
     * Handles a blob or omniblob clicked onto the hub item.
     *
     * @param hub          the hub item stack in the slot
     * @param cursor       the item stack on the cursor
     * @param slot         the inventory slot
     * @param action       the click action
     * @param player       the interacting player
     * @param cursorAccess access to set the cursor contents
     * @return true if any goo was routed into the hub's canisters
     */
    @Override
    public boolean overrideOtherStackedOnMe(@NonNull ItemStack hub, @NonNull ItemStack cursor,
            @NonNull Slot slot, @NonNull ClickAction action, @NonNull Player player,
            @NonNull SlotAccess cursorAccess) {
        return action == ClickAction.PRIMARY
                && CanisterInventoryHandler.insertFromCursor(cursor, cursorAccess,
                        (type, volume) -> routeIntoCanisters(hub, type, volume));
    }

    /**
     * Routes goo into copies of the hub's canisters and writes them back when any was accepted.
     *
     * @param hub    the hub item stack
     * @param type   the goo type offered
     * @param volume the volume offered, in mB
     * @return the volume accepted
     */
    private static int routeIntoCanisters(ItemStack hub, ResourceKey<GooTypeDefinition> type, int volume) {
        List<ItemStack> held = hub.get(GooDataComponents.HUB_CANISTERS.get());
        if (held == null || held.isEmpty()) { return 0; }
        List<ItemStack> copies = new ArrayList<>(held.size());
        List<CanisterStack> canisters = new ArrayList<>(held.size());
        for (ItemStack canister : held) {
            ItemStack copy = canister.copy();
            copies.add(copy);
            canisters.add(new CanisterStack(copy));
        }
        int accepted = HubCanisterRouting.route(canisters, type, volume);
        if (accepted > 0) {
            hub.set(GooDataComponents.HUB_CANISTERS.get(), copies);
        }
        return accepted;
    }

    /**
     * A canister item stack seen through the routing seam.
     *
     * @param stack the canister item stack
     */
    private record CanisterStack(ItemStack stack) implements HubCanisterRouting.RoutableCanister {

        @Override
        public boolean isEmpty() {
            return CanisterItem.getFluidContent(stack).isEmpty();
        }

        @Override
        public boolean holds(ResourceKey<GooTypeDefinition> type) {
            CanisterFluidContent content = CanisterItem.getFluidContent(stack);
            return !content.isEmpty() && type.equals(content.getGooType());
        }

        @Override
        public int addGoo(ResourceKey<GooTypeDefinition> type, int volume) {
            return CanisterItem.addGoo(stack, type, volume);
        }
    }
}
