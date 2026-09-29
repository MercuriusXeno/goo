package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.item.GooItem;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pickup-side stacking fix: when a player picks up a goo, if
 * a same-type goo already exists somewhere in their inventory, route the
 * entire source volume into that goo instead of spawning a fresh slot.
 *
 * <p>Root cause of the bug: gooStacks have {@code maxStackSize=1}, so vanilla's
 * {@code Inventory.add} merge pass short-circuits via {@code ItemStack.isStackable()}
 * and goes straight to empty-slot placement.</p>
 *
 * <p>This mixin only *adds* a pre-pass for the "goo-as-sink" case. When
 * there is no matching goo sink, the mixin is a no-op and vanilla runs
 * normally.</p>
 */
@Mixin(Inventory.class)
public abstract class InventoryPickupMergeMixin {

    /**
     * Intercepts {@link Inventory#add(int, ItemStack)} at HEAD. If the incoming
     * stack is a goo item and a same-type goo already lives in the inventory,
     * the source is absorbed whole into that goo and the call returns true.
     * Otherwise control falls through to vanilla.
     *
     * @param slot  the slot hint (-1 for "any") forwarded from {@link Inventory#add(ItemStack)}
     * @param stack the stack being added; mutated to empty on successful absorb
     * @param cir   mixin callback info; set to true when the absorb handled the add
     */
    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true)
    private void goo$absorbIntoExistingGoo(int slot, ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) { return; }
        ResourceKey<GooTypeDefinition> sourceType = GooStacks.keyOf(stack);
        if (sourceType == null) { return; }

        Container self = (Container) this;
        ItemStack sink = findMatchingGoo(self, sourceType);
        if (sink == null) { return; }

        GooStacks.absorbIntoGooSlot(stack, sink);
        cir.setReturnValue(true);
    }

    /**
     * Linear scan for the first goo of the matching goo type in the inventory.
     *
     * @param container  the inventory to scan
     * @param sourceType the goo type to match
     * @return the matching goo ItemStack, or {@code null} if none found
     */
    private static ItemStack findMatchingGoo(Container container, ResourceKey<GooTypeDefinition> sourceType) {
        int size = container.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack candidate = container.getItem(i);
            if (candidate.getItem() instanceof GooItem
                    && GooStacks.keyOf(candidate) == sourceType) {
                return candidate;
            }
        }
        return null;
    }
}
