package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.goo;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.id;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.recipe;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for LeastNumberPerType: each item's least value per goo type, the floor rule carried back
 * through the winning recipes so every type every item holds comes out at 1 or more, built from
 * TestRecipeBuilder recipes.
 */
class LeastNumberPerTypeTest {

    private static final String GLASS = "minecraft:glass";
    private static final String PANE = "minecraft:glass_pane";
    private static final String DYE = "minecraft:red_dye";
    private static final String STAINED = "minecraft:red_stained_glass";
    private static final String STAINED_PANE = "minecraft:red_stained_glass_pane";

    private static Map<Identifier, GooValue> valued(Object... itemTypeAmount) {
        Map<Identifier, GooValue> valued = new HashMap<>();
        for (int i = 0; i < itemTypeAmount.length; i += 3) {
            @SuppressWarnings("unchecked")
            ResourceKey<GooTypeDefinition> type = (ResourceKey<GooTypeDefinition>) itemTypeAmount[i + 1];
            GooValue held = valued.getOrDefault(id((String) itemTypeAmount[i]), GooValue.EMPTY);
            valued.put(id((String) itemTypeAmount[i]), held.add(goo(type, (Integer) itemTypeAmount[i + 2]), 1));
        }
        return valued;
    }

    private static LeastNumberPerType.TypeLeastValue onlyType(List<RecipeInput> recipes,
                                                              Map<Identifier, GooValue> valued,
                                                              ResourceKey<GooTypeDefinition> type) {
        return onlyType(recipes, valued, Set.of(), type);
    }

    private static LeastNumberPerType.TypeLeastValue onlyType(List<RecipeInput> recipes,
                                                              Map<Identifier, GooValue> valued, Set<Identifier> denied,
                                                              ResourceKey<GooTypeDefinition> type) {
        List<LeastNumberPerType.TypeLeastValue> types =
                LeastNumberPerType.work(recipes, valued, denied, List.of(type));
        assertEquals(1, types.size());
        return types.getFirst();
    }

    private static LeastNumberPerType.ItemBound item(LeastNumberPerType.TypeLeastValue type, String item) {
        return type.items().stream().filter(r -> r.item().equals(id(item))).findFirst().orElseThrow();
    }

    private static String[][] slots(String item, int count) {
        String[][] slots = new String[count][];
        for (int i = 0; i < count; i++) {
            slots[i] = slot(item);
        }
        return slots;
    }

    private static List<RecipeInput> dyeChain() {
        List<String[]> stainedSlots = new ArrayList<>(List.of(slots(GLASS, 8)));
        stainedSlots.add(slot(DYE));
        return List.of(recipe(STAINED, 8, stainedSlots.toArray(String[][]::new)),
                recipe(STAINED_PANE, 16, slots(STAINED, 6)));
    }

    private static Map<Identifier, GooValue> dyeChainValues() {
        return valued(GLASS, GooTypes.ROCK, 1, DYE, GooTypes.LEAF, 1,
                STAINED, GooTypes.ROCK, 1, STAINED, GooTypes.LEAF, 1,
                STAINED_PANE, GooTypes.ROCK, 1, STAINED_PANE, GooTypes.LEAF, 1);
    }

    @Test
    void everyTypeComesOutAtOneOrMoreAndNoDivisorIsDerived() {
        LeastNumberPerType.TypeLeastValue rock = onlyType(List.of(recipe(PANE, 16, slots(GLASS, 6))),
                valued(GLASS, GooTypes.ROCK, 1, PANE, GooTypes.ROCK, 1), GooTypes.ROCK);

        assertEquals(GooFraction.of(1), item(rock, PANE).bound());
        assertEquals(GooFraction.of(8, 3), item(rock, GLASS).bound());
        rock.items().forEach(bound -> assertTrue(bound.bound().compareTo(GooFraction.of(1)) >= 0, bound.line()));
        assertEquals(List.of("  rock: 8/3 at minecraft:glass (melts 2, costs 3)",
                "    minecraft:glass: 8/3 (melts 2, costs 3) = 6 minecraft:glass -> 16 minecraft:glass_pane",
                "    minecraft:glass_pane: 1 (melts 1, costs 1)"), rock.lines());
    }

    @Test
    void paneAtOneLeafForcesTheDyeToSixtyFourThirds() {
        LeastNumberPerType.TypeLeastValue leaf = onlyType(dyeChain(), dyeChainValues(), GooTypes.LEAF);

        assertEquals(GooFraction.of(1), item(leaf, STAINED_PANE).bound());
        assertEquals(GooFraction.of(8, 3), item(leaf, STAINED).bound());
        assertEquals(GooFraction.of(64, 3), item(leaf, DYE).bound());
    }

    @Test
    void dyeLineReadsItsFractionFloorCeilingAndChain() {
        LeastNumberPerType.TypeLeastValue leaf = onlyType(dyeChain(), dyeChainValues(), GooTypes.LEAF);

        assertEquals("    minecraft:red_dye: 64/3 (melts 21, costs 22) = 1 minecraft:red_dye -> 8 minecraft:red_stained_glass"
                        + ", 6 minecraft:red_stained_glass -> 16 minecraft:red_stained_glass_pane",
                item(leaf, DYE).line());
        assertEquals("  leaf: 64/3 at minecraft:red_dye (melts 21, costs 22)", leaf.lines().getFirst());
    }

    @Test
    void boundOnlyReachesAnInputHoldingTheType() {
        LeastNumberPerType.TypeLeastValue rock = onlyType(dyeChain(), dyeChainValues(), GooTypes.ROCK);

        assertEquals(GooFraction.of(8, 3), item(rock, GLASS).bound());
        assertFalse(rock.items().stream().anyMatch(bound -> bound.item().equals(id(DYE))));
    }

    @Test
    void everyTiedAlternativeInASlotTakesTheBound() {
        LeastNumberPerType.TypeLeastValue rock = onlyType(
                List.of(recipe("minecraft:flint", 2, slot("minecraft:andesite", "minecraft:diorite"))),
                valued("minecraft:andesite", GooTypes.ROCK, 1, "minecraft:diorite", GooTypes.ROCK, 1,
                        "minecraft:flint", GooTypes.ROCK, 1), GooTypes.ROCK);

        assertEquals(GooFraction.of(2), item(rock, "minecraft:andesite").bound());
        assertEquals(GooFraction.of(2), item(rock, "minecraft:diorite").bound());
    }

    @Test
    void containerTakesNoBoundFromTheRecipeReturningIt() {
        Map<Identifier, GooValue> valued = valued("minecraft:milk_bucket", GooTypes.JELLY, 4,
                "minecraft:milk_bucket", GooTypes.METAL, 3, "minecraft:bucket", GooTypes.METAL, 3,
                "minecraft:cake", GooTypes.JELLY, 2);
        List<RecipeInput> cake = List.of(recipe("minecraft:cake", 2,
                Map.of("minecraft:milk_bucket", "minecraft:bucket"), slot("minecraft:milk_bucket")));
        LeastNumberPerType.TypeLeastValue jelly = onlyType(cake, valued, GooTypes.JELLY);
        LeastNumberPerType.TypeLeastValue metal = onlyType(cake, valued, GooTypes.METAL);

        assertEquals("    minecraft:milk_bucket: 2 net of minecraft:bucket (melts 2, costs 2)"
                + " = 1 minecraft:milk_bucket -> 2 minecraft:cake", item(jelly, "minecraft:milk_bucket").line());
        assertEquals(GooFraction.of(1), item(metal, "minecraft:milk_bucket").bound());
        assertFalse(metal.items().stream().anyMatch(bound -> bound.item().equals(id("minecraft:bucket"))));
    }

    @Test
    void recipeFeedingItsOwnOutputNetsTheReturnedCopyOut() {
        String template = "minecraft:bolt_armor_trim_smithing_template";
        List<String[]> slots = new ArrayList<>();
        slots.add(slot(template));
        slots.addAll(List.of(slots("minecraft:diamond", 7)));
        slots.add(slot("minecraft:copper_block"));
        LeastNumberPerType.TypeLeastValue crystal = onlyType(
                List.of(recipe(template, 2, slots.toArray(String[][]::new))),
                valued(template, GooTypes.CRYSTAL, 1, "minecraft:diamond", GooTypes.CRYSTAL, 1,
                        "minecraft:copper_block", GooTypes.CRYSTAL, 1), GooTypes.CRYSTAL);

        assertEquals(GooFraction.of(1), item(crystal, "minecraft:diamond").bound());
        assertEquals(GooFraction.of(1), item(crystal, "minecraft:copper_block").bound());
    }

    @Test
    void boundReachesTheRootBehindAHandKeyedItem() {
        Map<Identifier, GooValue> valued = valued("minecraft:oak_log", GooTypes.LEAF, 4,
                "minecraft:oak_planks", GooTypes.LEAF, 1, "minecraft:stick", GooTypes.LEAF, 1,
                "minecraft:rail", GooTypes.LEAF, 1);
        LeastNumberPerType.TypeLeastValue leaf = onlyType(List.of(
                        recipe("minecraft:oak_planks", 4, slot("minecraft:oak_log")),
                        recipe("minecraft:stick", 4, slot("minecraft:oak_planks"), slot("minecraft:oak_planks")),
                        recipe("minecraft:rail", 16, slot("minecraft:stick"))),
                valued, GooTypes.LEAF);

        assertEquals(GooFraction.of(16), item(leaf, "minecraft:stick").bound());
        assertEquals(GooFraction.of(32), item(leaf, "minecraft:oak_planks").bound());
        assertEquals("    minecraft:oak_log: 128 (melts 128, costs 128) = 1 minecraft:oak_log -> 4 minecraft:oak_planks"
                        + ", 2 minecraft:oak_planks -> 4 minecraft:stick, 1 minecraft:stick -> 16 minecraft:rail",
                item(leaf, "minecraft:oak_log").line());
    }

    @Test
    void everyItemReadsItsLeastValueDerivedFromTheRoots() {
        Map<Identifier, GooValue> valued = valued("minecraft:oak_log", GooTypes.LEAF, 4,
                "minecraft:oak_planks", GooTypes.LEAF, 1, "minecraft:stick", GooTypes.LEAF, 1,
                "minecraft:rail", GooTypes.LEAF, 1);
        List<String> lines = LeastNumberPerType.smallestValueLines(List.of(
                        recipe("minecraft:oak_planks", 4, slot("minecraft:oak_log")),
                        recipe("minecraft:stick", 4, slot("minecraft:oak_planks"), slot("minecraft:oak_planks")),
                        recipe("minecraft:rail", 16, slot("minecraft:stick"))),
                valued, Set.of());

        assertEquals(List.of(
                "    minecraft:oak_log: leaf 128 (melts leaf 128; costs leaf 128)",
                "    minecraft:oak_planks: leaf 32 (melts leaf 32; costs leaf 32)",
                "    minecraft:rail: leaf 1 (melts leaf 1; costs leaf 1)",
                "    minecraft:stick: leaf 16 (melts leaf 16; costs leaf 16)"), lines);
    }

    @Test
    void loopOfWinningRecipesStartsFromItsLargestBound() {
        String ingot = "minecraft:iron_ingot";
        String nugget = "minecraft:iron_nugget";
        Map<Identifier, GooValue> valued = valued(ingot, GooTypes.METAL, 9, nugget, GooTypes.METAL, 1,
                "minecraft:rail", GooTypes.METAL, 1);
        List<String> lines = LeastNumberPerType.smallestValueLines(List.of(
                        recipe(nugget, 9, slot(ingot)),
                        recipe(ingot, 1, slots(nugget, 9)),
                        recipe("minecraft:rail", 16, slots(ingot, 6))),
                valued, Set.of());

        assertEquals(List.of(
                "    minecraft:iron_ingot: metal 9 (melts metal 9; costs metal 9)",
                "    minecraft:iron_nugget: metal 1 (melts metal 1; costs metal 1)",
                "    minecraft:rail: metal 27/8 (melts metal 3; costs metal 4)"), lines);
    }

    @Test
    void typeLineNamesTheItemWithTheHighestBound() {
        LeastNumberPerType.TypeLeastValue rock = onlyType(List.of(
                        recipe(PANE, 16, slots(GLASS, 6)),
                        recipe("minecraft:stone_slab", 3, slot("minecraft:stone"))),
                valued(GLASS, GooTypes.ROCK, 1, PANE, GooTypes.ROCK, 1, "minecraft:stone", GooTypes.ROCK, 1,
                        "minecraft:stone_slab", GooTypes.ROCK, 1), GooTypes.ROCK);

        assertEquals("  rock: 3 at minecraft:stone (melts 3, costs 3)", rock.lines().getFirst());
    }

    @Test
    void cycleWhoseDemandNeverSettlesHoldsNoLeastValue() {
        LeastNumberPerType.TypeLeastValue rock = onlyType(List.of(
                        recipe("minecraft:b", 2, slot("minecraft:a")),
                        recipe("minecraft:a", 2, slot("minecraft:b"))),
                valued("minecraft:a", GooTypes.ROCK, 1, "minecraft:b", GooTypes.ROCK, 1), GooTypes.ROCK);

        LeastNumberPerType.ItemBound a = item(rock, "minecraft:a");
        assertFalse(a.resolvable());
        assertTrue(a.cycle().containsAll(List.of("1 minecraft:a -> 2 minecraft:b", "1 minecraft:b -> 2 minecraft:a")));
        assertTrue(a.line().contains("no least value"));
        assertFalse(rock.resolvable());
    }

    @Test
    void blockAndIngotCycleSettles() {
        LeastNumberPerType.TypeLeastValue metal = onlyType(List.of(
                        recipe("minecraft:iron_block", 1, slots("minecraft:iron_ingot", 9)),
                        recipe("minecraft:iron_ingot", 9, slot("minecraft:iron_block"))),
                valued("minecraft:iron_block", GooTypes.METAL, 9, "minecraft:iron_ingot", GooTypes.METAL, 1),
                GooTypes.METAL);

        assertTrue(metal.resolvable());
        assertEquals(GooFraction.of(9), item(metal, "minecraft:iron_block").bound());
        assertEquals(GooFraction.of(1), item(metal, "minecraft:iron_ingot").bound());
    }

    @Test
    void recipeMakingADeniedItemForcesNothing() {
        LeastNumberPerType.TypeLeastValue rock = onlyType(
                List.of(recipe("minecraft:iron_ore", 3, slot("minecraft:stone"))),
                valued("minecraft:stone", GooTypes.ROCK, 1), Set.of(id("minecraft:iron_ore")), GooTypes.ROCK);

        assertEquals(List.of(), rock.items());
        assertEquals(List.of("  rock: 1 (no item in the recipe graph)"), rock.lines());
    }

    @Test
    void alternativeHoldingNoValueIsSkipped() {
        LeastNumberPerType.TypeLeastValue rock = onlyType(
                List.of(recipe("minecraft:gravel", 2, slot("minecraft:stone", "minecraft:unvalued"))),
                valued("minecraft:stone", GooTypes.ROCK, 1, "minecraft:gravel", GooTypes.ROCK, 1), GooTypes.ROCK);

        assertEquals(2, rock.items().size());
        assertEquals(GooFraction.of(2), item(rock, "minecraft:stone").bound());
    }
}
