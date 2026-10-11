package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import java.util.List;

/**
 * Gametest for Jelly's value: every item carrying a food component resolves
 * a jelly term on the live server, recipe-derived foods included
 * (decision jelly-ships-from-every-food).
 */
public final class JellyValueTests {

    private static final String UNJELLIED_FOODS = "Every food item should resolve jelly, but these do not: ";
    private static final String NO_FOODS = "The item registry should hold food items";

    private JellyValueTests() {
    }

    /**
     * Walks the item registry for every item whose default components carry
     * food and asserts the server's goo values give each a jelly term.
     *
     * @param helper the gametest helper
     */
    public static void everyFoodResolvesJelly(GameTestHelper helper) {
        IGooValueLookup values = GooValues.of(helper.getLevel());
        List<Identifier> foods = BuiltInRegistries.ITEM.stream()
                .filter(item -> item.components().has(DataComponents.FOOD))
                .map(BuiltInRegistries.ITEM::getKey)
                .toList();
        helper.assertFalse(foods.isEmpty(), NO_FOODS);
        List<Identifier> unjellied = foods.stream()
                .filter(id -> jellyOf(values.lookup(id)) <= 0)
                .toList();
        helper.assertTrue(unjellied.isEmpty(), UNJELLIED_FOODS + unjellied);
        helper.succeed();
    }

    private static int jellyOf(GooValue value) {
        return value == null ? 0 : value.get(GooTypes.JELLY);
    }
}
