package com.mercuriusxeno.goo.recipe;

import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GooLabelItem;
import com.mercuriusxeno.goo.item.VatBlockItem;
import com.mercuriusxeno.goo.registry.GooRecipeSerializers;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One canister beside one goo label crafts the canister wearing the label, where
 * the label rule lets it on; the label is used up
 * (decision label-goes-on-an-empty-or-matching-vessel).
 */
public class GooLabelApplyRecipe extends CustomRecipe {

    public static final MapCodec<GooLabelApplyRecipe> MAP_CODEC = MapCodec.unit(new GooLabelApplyRecipe());
    public static final StreamCodec<RegistryFriendlyByteBuf, GooLabelApplyRecipe> STREAM_CODEC =
            StreamCodec.unit(new GooLabelApplyRecipe());

    /** The grid holds the vessel and the label and nothing else. */
    private static final int VESSEL_AND_LABEL = 2;

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return labeled(input).isPresent();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return labeled(input).orElse(ItemStack.EMPTY);
    }

    @Override
    public RecipeSerializer<GooLabelApplyRecipe> getSerializer() {
        return GooRecipeSerializers.GOO_LABEL_APPLY.get();
    }

    /**
     * The vessel the grid crafts: a copy of its one canister wearing its one label.
     *
     * @param input the grid
     * @return the labeled canister, or empty where the grid is not one canister beside
     *         one label the canister takes
     */
    private static Optional<ItemStack> labeled(CraftingInput input) {
        List<ItemStack> placed = input.items().stream().filter(stack -> !stack.isEmpty()).toList();
        Optional<ItemStack> vessel = placed.stream()
                .filter(stack -> stack.getItem() instanceof CanisterItem || stack.getItem() instanceof VatBlockItem)
                .findFirst();
        Optional<ResourceKey<GooTypeDefinition>> label = placed.stream()
                .map(GooLabelItem::typeOf).filter(Objects::nonNull).findFirst();
        if (placed.size() != VESSEL_AND_LABEL || vessel.isEmpty() || label.isEmpty()) {
            return Optional.empty();
        }
        ItemStack result = vessel.get().copyWithCount(1);
        return applyGooLabel(result, label.get()) ? Optional.of(result) : Optional.empty();
    }

    /**
     * Puts the label on whichever vessel the grid holds.
     *
     * @param vessel a canister or vat stack
     * @param type   the label's goo type
     * @return true when the label went on
     */
    private static boolean applyGooLabel(ItemStack vessel, ResourceKey<GooTypeDefinition> type) {
        return vessel.getItem() instanceof VatBlockItem
                ? VatBlockItem.applyGooLabel(vessel, type)
                : CanisterItem.applyGooLabel(vessel, type);
    }
}
