package com.mercuriusxeno.goo.recipe;

import com.mercuriusxeno.goo.item.GooItem;
import com.mercuriusxeno.goo.item.GooLabelCraft;
import com.mercuriusxeno.goo.item.GooLabelCraft.Cell;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooRecipeSerializers;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One paper beside one goo crafts a label of the goo's type and leaves the goo in
 * the grid 1 mB lighter; nothing crafts a label back
 * (decision goo-label-is-paper-plus-one-mb-of-goo).
 */
public class GooLabelRecipe extends CustomRecipe {

    public static final MapCodec<GooLabelRecipe> MAP_CODEC = MapCodec.unit(new GooLabelRecipe());
    public static final StreamCodec<RegistryFriendlyByteBuf, GooLabelRecipe> STREAM_CODEC =
            StreamCodec.unit(new GooLabelRecipe());

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return craftOf(input).isPresent();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return craftOf(input).map(craft -> GooItems.GOO_LABEL.get().labelOf(craft.labelType())).orElse(ItemStack.EMPTY);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remainders = CraftingRecipe.defaultCraftingReminder(input);
        craftOf(input).ifPresent(craft -> remainders.set(craft.gooCell(),
                GooStacks.createForOutput(craft.labelType(), craft.remainingVolume())));
        return remainders;
    }

    @Override
    public RecipeSerializer<GooLabelRecipe> getSerializer() {
        return GooRecipeSerializers.GOO_LABEL.get();
    }

    private static Optional<GooLabelCraft.Craft> craftOf(CraftingInput input) {
        List<Cell> cells = new ArrayList<>(input.size());
        for (int slot = 0; slot < input.size(); slot++) {
            cells.add(cellOf(input.getItem(slot)));
        }
        return GooLabelCraft.match(cells);
    }

    private static Cell cellOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Cell.EMPTY;
        }
        if (stack.is(Items.PAPER)) {
            return Cell.PAPER;
        }
        if (stack.getItem() instanceof GooItem) {
            return Cell.goo(GooStacks.keyOf(stack), GooStacks.volumeOf(stack));
        }
        return Cell.OTHER;
    }
}
