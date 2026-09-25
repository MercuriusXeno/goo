package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An inventory stack that carries goo in a container: a canister, a hub carrying canisters,
 * or a vat. {@link #of} is the one check that knows which items carry goo, so the scanner's
 * aggregate, threshold and depletion paths see the same carriers
 * (decision diagnose-then-fix-overlay-and-scan).
 */
sealed interface GooCarrier {

    /**
     * Answers the carrier view of a stack, or null when the stack carries no container of goo.
     *
     * @param stack the inventory stack
     * @return the carrier, or null
     */
    static @Nullable GooCarrier of(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof CanisterItem) {
            return new Canister(stack);
        }
        if (item instanceof HubBlockItem) {
            return new Hub(stack);
        }
        if (item instanceof VatBlockItem) {
            return new Vat(stack);
        }
        return null;
    }

    /**
     * @return the item class whose depletion pass drains this carrier
     */
    Class<? extends Item> depletionPass();

    /**
     * @return every goo type the carrier holds, with its volume in mB
     */
    Map<ResourceKey<GooTypeDefinition>, Integer> contents();

    /**
     * @param type the goo type
     * @return the mB of that type the carrier holds
     */
    default int volumeOf(ResourceKey<GooTypeDefinition> type) {
        return contents().getOrDefault(type, 0);
    }

    /**
     * Removes up to the amount of the type from the carrier's stack.
     *
     * @param type   the goo type
     * @param amount the mB wanted
     * @return the mB removed
     */
    int remove(ResourceKey<GooTypeDefinition> type, int amount);

    /**
     * A canister item stack.
     *
     * @param stack the canister stack
     */
    record Canister(ItemStack stack) implements GooCarrier {

        @Override
        public Class<? extends Item> depletionPass() {
            return CanisterItem.class;
        }

        @Override
        public Map<ResourceKey<GooTypeDefinition>, Integer> contents() {
            CanisterFluidContent content = CanisterItem.getFluidContent(stack);
            ResourceKey<GooTypeDefinition> type = content.getGooType();
            return type != null && content.amount() > 0 ? Map.of(type, content.amount()) : Map.of();
        }

        @Override
        public int remove(ResourceKey<GooTypeDefinition> type, int amount) {
            return CanisterItem.removeGoo(stack, type, amount);
        }
    }

    /**
     * A hub item stack, whose HUB_CANISTERS drain in the canister pass.
     *
     * @param stack the hub stack
     */
    record Hub(ItemStack stack) implements GooCarrier {

        @Override
        public Class<? extends Item> depletionPass() {
            return CanisterItem.class;
        }

        @Override
        public Map<ResourceKey<GooTypeDefinition>, Integer> contents() {
            Map<ResourceKey<GooTypeDefinition>, Integer> totals = new HashMap<>();
            for (ItemStack canister : canisters()) {
                new Canister(canister).contents().forEach((type, volume) -> totals.merge(type, volume, Integer::sum));
            }
            return totals;
        }

        /**
         * Drains copies of the hub's canisters in order and writes them back once any gave goo.
         */
        @Override
        public int remove(ResourceKey<GooTypeDefinition> type, int amount) {
            List<ItemStack> copies = new ArrayList<>();
            int removed = 0;
            for (ItemStack canister : canisters()) {
                ItemStack copy = canister.copy();
                removed += CanisterItem.removeGoo(copy, type, amount - removed);
                copies.add(copy);
            }
            if (removed > 0) {
                stack.set(GooDataComponents.HUB_CANISTERS.get(), copies);
            }
            return removed;
        }

        private List<ItemStack> canisters() {
            return stack.getOrDefault(GooDataComponents.HUB_CANISTERS.get(), List.of());
        }
    }

    /**
     * A vat item stack.
     *
     * @param stack the vat stack
     */
    record Vat(ItemStack stack) implements GooCarrier {

        @Override
        public Class<? extends Item> depletionPass() {
            return VatBlockItem.class;
        }

        @Override
        public Map<ResourceKey<GooTypeDefinition>, Integer> contents() {
            return VatBlockItem.getGooContents(stack).getAll();
        }

        @Override
        public int volumeOf(ResourceKey<GooTypeDefinition> type) {
            return VatBlockItem.getGooContents(stack).getVolume(type);
        }

        @Override
        public int remove(ResourceKey<GooTypeDefinition> type, int amount) {
            return VatBlockItem.removeGoo(stack, type, amount);
        }
    }
}
