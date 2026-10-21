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
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametest for Jelly's value: every item carrying a food component and no
 * denial resolves a jelly term on the live server, recipe-derived foods included
 * (decision jelly-ships-from-every-food).
 */
@RegistersGameTests
public final class JellyValueTests {
    private static final String EVERY_FOOD_RESOLVES_JELLY = "every_food_resolves_jelly";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, EVERY_FOOD_RESOLVES_JELLY, JellyValueTests::everyFoodResolvesJelly);
    }


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
        // decision goo-values-reviewed-against-the-new-types: a denied food, such as a fish bucket, holds no value to carry jelly
        List<Identifier> unjellied = foods.stream()
                .filter(id -> !values.isDenied(id))
                .filter(id -> jellyOf(values.lookup(id)) <= 0)
                .toList();
        helper.assertTrue(unjellied.isEmpty(), UNJELLIED_FOODS + unjellied);
        helper.succeed();
    }

    private static int jellyOf(GooValue value) {
        return value == null ? 0 : value.get(GooTypes.JELLY);
    }
}
