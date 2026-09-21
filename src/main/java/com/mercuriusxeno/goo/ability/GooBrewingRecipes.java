package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooPotions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * Registers brewing recipes: blob + awkward potion yields goo potion, one
 * per bundled type, over the same keys GooPotions registers by (decision
 * potions-stay-per-type).
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GooBrewingRecipes {

    private GooBrewingRecipes() {}

    /**
     * Registers one brewing recipe per potion GooPotions holds.
     *
     * @param event the brewing recipe registration event
     */
    @SubscribeEvent
    public static void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
        var builder = event.getBuilder();
        for (var entry : GooPotions.GOO_POTIONS.entrySet()) {
            ResourceKey<GooTypeDefinition> key = entry.getKey();
            var blobHolder = GooItems.blob(key);
            if (blobHolder != null) {
                builder.addMix(Potions.AWKWARD, blobHolder.get(), entry.getValue());
            }
        }
    }
}
