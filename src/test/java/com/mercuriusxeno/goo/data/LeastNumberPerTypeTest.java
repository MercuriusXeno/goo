package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
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
 * Tests for LeastNumberPerType: the smallest number per goo type that every recipe divides evenly,
 * worked from the recipe graph and carried back to the root items, built from TestRecipeBuilder recipes.
 */
class LeastNumberPerTypeTest {

    private static final String GLASS = "minecraft:glass";
    private static final String PANE = "minecraft:glass_pane";

    private static Map<Identifier, GooValue> roots(Object... itemTypeAmount) {
        Map<Identifier, GooValue> roots = new HashMap<>();
        for (int i = 0; i < itemTypeAmount.length; i += 3) {
            @SuppressWarnings("unchecked")
            ResourceKey<GooTypeDefinition> type = (ResourceKey<GooTypeDefinition>) itemTypeAmount[i + 1];
            roots.put(id((String) itemTypeAmount[i]), goo(type, (Integer) itemTypeAmount[i + 2]));
        }
        return roots;
    }

    private static LeastNumberPerType.TypeLeastNumber onlyType(List<RecipeInput> recipes,
                                                              Map<Identifier, GooValue> roots,
                                                              ResourceKey<GooTypeDefinition> type) {
        return onlyType(recipes, roots, Set.of(), type);
    }

    private static LeastNumberPerType.TypeLeastNumber onlyType(List<RecipeInput> recipes,
                                                              Map<Identifier, GooValue> roots, Set<Identifier> denied,
                                                              ResourceKey<GooTypeDefinition> type) {
        return onlyType(recipes, roots, roots, denied, type);
    }

    private static LeastNumberPerType.TypeLeastNumber onlyType(List<RecipeInput> recipes,
                                                              Map<Identifier, GooValue> roots,
                                                              Map<Identifier, GooValue> valued, Set<Identifier> denied,
                                                              ResourceKey<GooTypeDefinition> type) {
        List<LeastNumberPerType.TypeLeastNumber> types =
                LeastNumberPerType.work(recipes, valued, denied, List.of(type));
        assertEquals(1, types.size());
        return types.getFirst();
    }

    private static LeastNumberPerType.ItemDivisor root(LeastNumberPerType.TypeLeastNumber type, String item) {
        return type.items().stream().filter(r -> r.item().equals(id(item))).findFirst().orElseThrow();
    }

    private static String[][] slots(String item, int count) {
        String[][] slots = new String[count][];
        for (int i = 0; i < count; i++) {
            slots[i] = slot(item);
        }
        return slots;
    }

    @Test
    void sixGlassIntoSixteenPanesForcesGlassToEight() {
        LeastNumberPerType.TypeLeastNumber rock = onlyType(
                List.of(recipe(PANE, 16, slots(GLASS, 6))), roots(GLASS, GooTypes.ROCK, 1), GooTypes.ROCK);

        assertEquals(8, rock.number());
        assertEquals(List.of("  rock: 8 = 2^3 (minecraft:glass)",
                "    minecraft:glass: 8 = 2^3 (6 minecraft:glass -> 16 minecraft:glass_pane)"), rock.lines());
    }

    @Test
    void everyPrimeFactorIsTracedToTheRecipesForcingIt() {
        String ingot = "minecraft:iron_ingot";
        LeastNumberPerType.TypeLeastNumber metal = onlyType(List.of(
                        recipe("minecraft:rail", 16, slots(ingot, 6)),
                        recipe("minecraft:iron_nugget", 9, slot(ingot))),
                roots(ingot, GooTypes.METAL, 72), GooTypes.METAL);

        assertEquals(List.of("  metal: 72 = 2^3 (minecraft:iron_ingot) * 3^2 (minecraft:iron_ingot)",
                "    minecraft:iron_ingot: 72 = 2^3 (6 minecraft:iron_ingot -> 16 minecraft:rail)"
                        + " * 3^2 (1 minecraft:iron_ingot -> 9 minecraft:iron_nugget)"), metal.lines());
    }

    @Test
    void requirementCarriesBackUpTheChainToTheRoot() {
        Map<Identifier, GooValue> roots = roots("minecraft:oak_log", GooTypes.LEAF, 4);
        Map<Identifier, GooValue> valued = new HashMap<>(roots);
        valued.put(id("minecraft:oak_planks"), goo(GooTypes.LEAF, 1));
        LeastNumberPerType.TypeLeastNumber leaf = onlyType(List.of(
                        recipe("minecraft:oak_planks", 4, slot("minecraft:oak_log")),
                        recipe("minecraft:stick", 4, slot("minecraft:oak_planks"), slot("minecraft:oak_planks"))),
                roots, valued, Set.of(), GooTypes.LEAF);

        LeastNumberPerType.ItemDivisor log = root(leaf, "minecraft:oak_log");
        assertEquals(8, log.divisor());
        assertEquals(List.of(new LeastNumberPerType.FactorProof(2, 3, List.of(
                "1 minecraft:oak_log -> 4 minecraft:oak_planks", "2 minecraft:oak_planks -> 4 minecraft:stick"))),
                log.factors());
    }

    @Test
    void handKeyedItemDoesNotStopTheWalk() {
        Map<Identifier, GooValue> valued = roots("minecraft:oak_log", GooTypes.LEAF, 4,
                "minecraft:oak_planks", GooTypes.LEAF, 1, "minecraft:stick", GooTypes.LEAF, 1);
        LeastNumberPerType.TypeLeastNumber leaf = onlyType(List.of(
                        recipe("minecraft:oak_planks", 4, slot("minecraft:oak_log")),
                        recipe("minecraft:stick", 4, slot("minecraft:oak_planks"), slot("minecraft:oak_planks")),
                        recipe("minecraft:rail", 16, slot("minecraft:stick"))),
                valued, valued, Set.of(), GooTypes.LEAF);

        assertEquals(16, root(leaf, "minecraft:stick").divisor());
        assertEquals(32, root(leaf, "minecraft:oak_planks").divisor());
        assertEquals(128, root(leaf, "minecraft:oak_log").divisor());
    }

    @Test
    void everyItemReadsItsSmallestValue() {
        Map<Identifier, GooValue> valued = roots("minecraft:oak_log", GooTypes.LEAF, 4,
                "minecraft:oak_planks", GooTypes.LEAF, 1, "minecraft:stick", GooTypes.LEAF, 1);
        List<String> lines = LeastNumberPerType.smallestValueLines(List.of(
                        recipe("minecraft:oak_planks", 4, slot("minecraft:oak_log")),
                        recipe("minecraft:stick", 4, slot("minecraft:oak_planks"), slot("minecraft:oak_planks")),
                        recipe("minecraft:rail", 16, slot("minecraft:stick"))),
                valued, Set.of());

        assertEquals(List.of(
                "    minecraft:oak_log: leaf 128 (multiple 128)",
                "    minecraft:oak_planks: leaf 32 (multiple 32)",
                "    minecraft:rail: leaf 1 (multiple 1)",
                "    minecraft:stick: leaf 16 (multiple 16)"), lines);
    }

    @Test
    void loopOfWinningRecipesStartsFromItsLargestMultiple() {
        String ingot = "minecraft:iron_ingot";
        String nugget = "minecraft:iron_nugget";
        Map<Identifier, GooValue> valued = roots(ingot, GooTypes.METAL, 9, nugget, GooTypes.METAL, 1);
        List<String> lines = LeastNumberPerType.smallestValueLines(List.of(
                        recipe(nugget, 9, slot(ingot)),
                        recipe(ingot, 1, slots(nugget, 9)),
                        recipe("minecraft:rail", 16, slots(ingot, 6))),
                valued, Set.of());

        assertEquals(List.of(
                "    minecraft:iron_ingot: metal 72 (multiple 72)",
                "    minecraft:iron_nugget: metal 8 (multiple 8)",
                "    minecraft:rail: metal 27 (multiple 1)"), lines);
    }

    @Test
    void typeNumberIsTheLeastCommonMultipleOfItsRoots() {
        LeastNumberPerType.TypeLeastNumber rock = onlyType(List.of(
                        recipe(PANE, 16, slots(GLASS, 6)),
                        recipe("minecraft:stone_slab", 3, slot("minecraft:stone"))),
                roots(GLASS, GooTypes.ROCK, 1, "minecraft:stone", GooTypes.ROCK, 1), GooTypes.ROCK);

        assertEquals(24, rock.number());
        assertEquals(8, root(rock, GLASS).divisor());
        assertEquals(3, root(rock, "minecraft:stone").divisor());
    }

    @Test
    void everyRecipeAndEveryAlternativeCounts() {
        LeastNumberPerType.TypeLeastNumber rock = onlyType(List.of(
                        recipe("minecraft:gravel", 1, slot("minecraft:cobblestone")),
                        recipe("minecraft:gravel", 4, slot("minecraft:stone")),
                        recipe("minecraft:flint", 2, slot("minecraft:andesite", "minecraft:diorite"))),
                roots("minecraft:cobblestone", GooTypes.ROCK, 1, "minecraft:stone", GooTypes.ROCK, 9,
                        "minecraft:andesite", GooTypes.ROCK, 1, "minecraft:diorite", GooTypes.ROCK, 1),
                GooTypes.ROCK);

        assertEquals(4, root(rock, "minecraft:stone").divisor());
        assertEquals(2, root(rock, "minecraft:andesite").divisor());
        assertEquals(2, root(rock, "minecraft:diorite").divisor());
    }

    @Test
    void cycleWhoseDemandNeverSettlesIsResolvableByNoNumber() {
        LeastNumberPerType.TypeLeastNumber rock = onlyType(List.of(
                        recipe("minecraft:b", 2, slot("minecraft:a")),
                        recipe("minecraft:a", 2, slot("minecraft:b"))),
                roots("minecraft:a", GooTypes.ROCK, 1, "minecraft:b", GooTypes.ROCK, 1), GooTypes.ROCK);

        LeastNumberPerType.ItemDivisor a = root(rock, "minecraft:a");
        assertFalse(a.resolvable());
        assertTrue(a.cycle().containsAll(List.of("1 minecraft:a -> 2 minecraft:b", "1 minecraft:b -> 2 minecraft:a")));
        assertTrue(a.line().contains("resolvable by no number"));
        assertFalse(rock.resolvable());
    }

    @Test
    void blockAndIngotCycleSettles() {
        LeastNumberPerType.TypeLeastNumber metal = onlyType(List.of(
                        recipe("minecraft:iron_block", 1, slots("minecraft:iron_ingot", 9)),
                        recipe("minecraft:iron_ingot", 9, slot("minecraft:iron_block"))),
                roots("minecraft:iron_block", GooTypes.METAL, 9, "minecraft:iron_ingot", GooTypes.METAL, 1),
                GooTypes.METAL);

        assertTrue(metal.resolvable());
        assertEquals(9, root(metal, "minecraft:iron_block").divisor());
        assertEquals(1, root(metal, "minecraft:iron_ingot").divisor());
    }

    @Test
    void recipeFeedingItsOwnOutputNetsTheReturnedCopyOut() {
        String template = "minecraft:bolt_armor_trim_smithing_template";
        List<String[]> slots = new java.util.ArrayList<>();
        slots.add(slot(template));
        slots.addAll(List.of(slots("minecraft:diamond", 7)));
        slots.add(slot("minecraft:copper_block"));
        LeastNumberPerType.TypeLeastNumber crystal = onlyType(
                List.of(recipe(template, 2, slots.toArray(String[][]::new))),
                roots(template, GooTypes.CRYSTAL, 1, "minecraft:diamond", GooTypes.CRYSTAL, 1,
                        "minecraft:copper_block", GooTypes.CRYSTAL, 1), GooTypes.CRYSTAL);

        assertTrue(crystal.resolvable());
        assertEquals(1, crystal.number());
    }

    @Test
    void containerItemDividesNetOfTheContainer() {
        LeastNumberPerType.TypeLeastNumber jelly = onlyType(List.of(
                        recipe("minecraft:cake", 2, Map.of("minecraft:milk_bucket", "minecraft:bucket"),
                                slot("minecraft:milk_bucket"))),
                roots("minecraft:milk_bucket", GooTypes.JELLY, 4), GooTypes.JELLY);

        assertEquals("    minecraft:milk_bucket: 2 net of minecraft:bucket = 2^1 (1 minecraft:milk_bucket -> 2 minecraft:cake)",
                root(jelly, "minecraft:milk_bucket").line());
    }

    @Test
    void recipeMakingADeniedItemForcesNothing() {
        LeastNumberPerType.TypeLeastNumber rock = onlyType(
                List.of(recipe("minecraft:iron_ore", 3, slot("minecraft:stone"))),
                roots("minecraft:stone", GooTypes.ROCK, 1), Set.of(id("minecraft:iron_ore")), GooTypes.ROCK);

        assertEquals(List.of(), rock.items());
        assertEquals(1, rock.number());
    }

    @Test
    void alternativeHoldingNoValueIsSkipped() {
        LeastNumberPerType.TypeLeastNumber rock = onlyType(
                List.of(recipe("minecraft:gravel", 2, slot("minecraft:stone", "minecraft:unvalued"))),
                roots("minecraft:stone", GooTypes.ROCK, 1), GooTypes.ROCK);

        assertEquals(1, rock.items().size());
        assertEquals(2, rock.number());
    }
}
