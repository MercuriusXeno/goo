package com.mercuriusxeno.goo.recipe;

import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GooLabelRemoval;
import com.mercuriusxeno.goo.item.VatBlockItem;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooRecipeSerializers;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import java.util.Optional;

/**
 * A labeled canister or vat alone in the grid crafts its goo label and stays in the
 * grid wearing none, its goo and every other datum intact
 * (decision label-comes-off-in-the-crafting-grid).
 */
public class GooLabelRemoveRecipe extends CustomRecipe {

    public static final MapCodec<GooLabelRemoveRecipe> MAP_CODEC = MapCodec.unit(new GooLabelRemoveRecipe());
    public static final StreamCodec<RegistryFriendlyByteBuf, GooLabelRemoveRecipe> STREAM_CODEC =
            StreamCodec.unit(new GooLabelRemoveRecipe());

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return onlyVessel(input).flatMap(GooLabelRemoveRecipe::labelOf).isPresent();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return onlyVessel(input).flatMap(GooLabelRemoveRecipe::labelOf)
                .map(type -> GooItems.GOO_LABEL.get().labelOf(type)).orElse(ItemStack.EMPTY);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remainders = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (!stack.isEmpty()) {
                remainders.set(slot, unlabeled(stack));
            }
        }
        return remainders;
    }

    @Override
    public RecipeSerializer<GooLabelRemoveRecipe> getSerializer() {
        return GooRecipeSerializers.GOO_LABEL_REMOVE.get();
    }

    /**
     * A stack of labeled vessels matches nothing: the grid would keep the rest still
     * labeled, and shift-crafting would pull one label per vessel.
     * decision goo-label-is-paper-plus-one-mb-of-goo
     *
     * @param input the grid
     * @return the one lone vessel the grid holds, or empty where it holds none, more than
     *         one stack, or a stack of more than one
     */
    private static Optional<ItemStack> onlyVessel(CraftingInput input) {
        return input.ingredientCount() == 1
                ? input.items().stream().filter(stack -> stack.getCount() == 1).findFirst()
                : Optional.empty();
    }

    /**
     * @param vessel a stack in the grid
     * @return the goo label type a canister or vat wears, or empty
     */
    private static Optional<ResourceKey<GooTypeDefinition>> labelOf(ItemStack vessel) {
        if (vessel.getItem() instanceof CanisterItem) {
            return GooLabelRemoval.off(CanisterItem.getMetadata(vessel)).map(GooLabelRemoval.Removal::label);
        }
        return vessel.getItem() instanceof VatBlockItem ? Optional.ofNullable(VatBlockItem.getGooLabel(vessel))
                : Optional.empty();
    }

    /**
     * @param vessel the labeled vessel
     * @return one of the vessel, wearing no goo label, everything else kept
     */
    private static ItemStack unlabeled(ItemStack vessel) {
        ItemStack kept = vessel.copyWithCount(1);
        if (kept.getItem() instanceof CanisterItem) {
            GooLabelRemoval.off(CanisterItem.getMetadata(kept))
                    .ifPresent(removal -> CanisterItem.setMetadata(kept, removal.kept()));
        } else {
            kept.remove(GooDataComponents.GOO_LABEL.get());
        }
        return kept;
    }
}
