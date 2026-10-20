package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.recipe.GooLabelApplyRecipe;
import com.mercuriusxeno.goo.recipe.GooLabelRecipe;
import com.mercuriusxeno.goo.recipe.GooLabelRemoveRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's custom crafting recipe serializers. */
public final class GooRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Goo.MODID);

    /** Paper beside goo crafts a goo label (decision goo-label-is-paper-plus-one-mb-of-goo). */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<GooLabelRecipe>> GOO_LABEL =
            RECIPE_SERIALIZERS.register("goo_label",
                    () -> new RecipeSerializer<>(GooLabelRecipe.MAP_CODEC, GooLabelRecipe.STREAM_CODEC));

    /** A vessel beside a goo label crafts the labeled vessel (decision label-goes-on-an-empty-or-matching-vessel). */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<GooLabelApplyRecipe>> GOO_LABEL_APPLY =
            RECIPE_SERIALIZERS.register("goo_label_apply",
                    () -> new RecipeSerializer<>(GooLabelApplyRecipe.MAP_CODEC, GooLabelApplyRecipe.STREAM_CODEC));

    /** A labeled vessel alone crafts its label back off (decision label-comes-off-in-the-crafting-grid). */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<GooLabelRemoveRecipe>> GOO_LABEL_REMOVE =
            RECIPE_SERIALIZERS.register("goo_label_remove",
                    () -> new RecipeSerializer<>(GooLabelRemoveRecipe.MAP_CODEC, GooLabelRemoveRecipe.STREAM_CODEC));

    private GooRecipeSerializers() {
    }
}
