package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooPotions;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import java.util.List;

/**
 * Registers brewing recipes: blob + awkward potion yields goo potion, one
 * per bundled type, over the same keys GooPotions registers by (decision
 * potions-stay-per-type). The blob is one item carrying its type in a
 * component (decision generic-goo-items), so the ingredient matches the
 * component rather than the item, over each potion container vanilla mixes.
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GooBrewingRecipes {

    /**
     * The containers a vanilla potion mix applies to.
     */
    private static final List<Item> CONTAINERS = List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION);

    private GooBrewingRecipes() {}

    /**
     * Registers the recipes of one potion per potion GooPotions holds.
     *
     * @param event the brewing recipe registration event
     */
    @SubscribeEvent
    public static void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
        PotionBrewing.Builder builder = event.getBuilder();
        for (var entry : GooPotions.GOO_POTIONS.entrySet()) {
            addMix(builder, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Adds awkward + omniblob of the type = the potion, for every container.
     *
     * @param builder the brewing builder
     * @param key     the goo type whose blob brews the potion
     * @param potion  the potion brewed
     */
    private static void addMix(PotionBrewing.Builder builder, ResourceKey<GooTypeDefinition> key,
                               Holder<Potion> potion) {
        Ingredient blob = DataComponentIngredient.of(false, GooDataComponents.GOO_TYPE, key, GooItems.GOO_OMNIBLOB);
        for (Item container : CONTAINERS) {
            Ingredient awkward = DataComponentIngredient.of(false, DataComponents.POTION_CONTENTS,
                    new PotionContents(Potions.AWKWARD), container);
            builder.addRecipe(awkward, blob, PotionContents.createItemStack(container, potion));
        }
    }
}
