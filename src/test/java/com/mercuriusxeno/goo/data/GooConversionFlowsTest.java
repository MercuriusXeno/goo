package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.recipe;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the conversion flows: each crafting path whose crafts lose one goo type and gain another, the most gained
 * per craft first (decision workbench-lists-type-to-type-paths-worst-first).
 */
class GooConversionFlowsTest {

    private static final String IRON = "minecraft:iron_ingot";
    private static final String NUGGET = "minecraft:iron_nugget";
    private static final String PEBBLE = "minecraft:pebble";
    private static final String TRANSMUTING = """
            {
                "iron_ingot": {"metal": 9},
                "chain": {"rock": 4},
                "lantern": {"rock": 8}
            }
            """;

    private static List<GooConversionFlows.Flow> flowsOf(String json, List<RecipeInput> recipes) {
        return GooConversionFlows.flowsOf(GooBaseValuesPreview.derivedRegistry(json, recipes));
    }

    private static List<String> outputsOf(GooConversionFlows.Flow flow) {
        return flow.recipes().stream().map(recipe -> recipe.output().getPath()).toList();
    }

    @Nested
    class SingleRecipes {
        @Test
        void derivedRecipeListsNoFlow() {
            List<GooConversionFlows.Flow> flows = flowsOf("""
                    {"oak_planks": {"leaf": 2}}
                    """, List.of(recipe("minecraft:stick", 4, slot("minecraft:oak_planks"),
                    slot("minecraft:oak_planks"))));

            assertTrue(flows.isEmpty(), flows.toString());
        }

        @Test
        void recipeLosingOneTypeAndGainingAnotherIsOneFlow() {
            List<GooConversionFlows.Flow> flows = flowsOf(TRANSMUTING,
                    List.of(recipe("minecraft:chain", 1, slot(IRON), slot(IRON))));

            assertEquals(1, flows.size(), flows.toString());
            GooConversionFlows.Flow flow = flows.getFirst();
            assertEquals(GooTypes.METAL, flow.source());
            assertEquals(GooTypes.ROCK, flow.target());
            assertEquals(GooFraction.of(18), flow.sourceLost());
            assertEquals(GooFraction.of(4), flow.targetGained());
        }

        @Test
        void largestGainPerCraftReadsFirst() {
            List<RecipeInput> recipes = List.of(recipe("minecraft:chain", 1, slot(IRON), slot(IRON)),
                    recipe("minecraft:lantern", 1, slot(IRON)));

            List<List<String>> outputs = flowsOf(TRANSMUTING, recipes).stream()
                    .map(GooConversionFlowsTest::outputsOf).toList();

            assertEquals(List.of(List.of("lantern"), List.of("chain")), outputs);
            assertEquals(outputs, flowsOf(TRANSMUTING, recipes.reversed()).stream()
                    .map(GooConversionFlowsTest::outputsOf).toList());
        }
    }

    @Nested
    class ChainedPaths {
        @Test
        void twoRecipesFeedingOneAnotherReadAsOnePath() {
            List<RecipeInput> recipes = List.of(recipe(NUGGET, 9, slot(IRON)),
                    recipe("minecraft:chain", 1, slot(NUGGET), slot(NUGGET), slot(NUGGET)));

            GooConversionFlows.Flow path = flowsOf("""
                    {"iron_ingot": {"metal": 9}, "chain": {"rock": 2}}
                    """, recipes).stream().filter(flow -> flow.recipes().size() == 2).findFirst().orElseThrow();

            assertEquals(List.of("iron_nugget", "chain"), outputsOf(path));
            assertEquals(GooTypes.METAL, path.source());
            assertEquals(GooTypes.ROCK, path.target());
            assertEquals(GooFraction.of(3), path.sourceLost());
            assertEquals(GooFraction.of(2), path.targetGained());
        }

        @Test
        void cycleListsEachPathOnceWithNoItemRevisited() {
            List<RecipeInput> recipes = List.of(recipe("minecraft:rock_item", 1, slot("minecraft:leaf_item")),
                    recipe("minecraft:leaf_item", 1, slot("minecraft:rock_item")));

            List<GooConversionFlows.Flow> flows = flowsOf("""
                    {"leaf_item": {"leaf": 1}, "rock_item": {"rock": 1}}
                    """, recipes);

            assertEquals(List.of(List.of("leaf_item"), List.of("rock_item")),
                    flows.stream().map(GooConversionFlowsTest::outputsOf).sorted(
                            java.util.Comparator.comparing(Object::toString)).toList());
        }

        @Test
        void chainLongerThanTheBoundListsNoPathPastIt() {
            List<RecipeInput> recipes = new java.util.ArrayList<>();
            int length = GooConversionFlows.MAX_PATH_RECIPES + 1;
            for (int step = 1; step < length; step++) {
                recipes.add(recipe("minecraft:step_" + step, 1, slot("minecraft:step_" + (step - 1))));
            }
            recipes.add(recipe("minecraft:rock_item", 1, slot("minecraft:step_" + (length - 1))));

            List<GooConversionFlows.Flow> flows = flowsOf("""
                    {"step_0": {"leaf": 1}, "rock_item": {"rock": 1}}
                    """, recipes);

            assertEquals(GooConversionFlows.MAX_PATH_RECIPES, flows.size(), flows.toString());
            for (GooConversionFlows.Flow flow : flows) {
                Set<Object> distinct = new HashSet<>(flow.recipes());
                assertEquals(flow.recipes().size(), distinct.size());
                assertTrue(flow.recipes().size() <= GooConversionFlows.MAX_PATH_RECIPES);
            }
        }

        @Test
        void chainTransmutingMoreThanASingleRecipeReadsAboveIt() {
            List<RecipeInput> recipes = List.of(recipe(PEBBLE, 1, slot(IRON)),
                    recipe("minecraft:boulder", 1, slot(PEBBLE), slot(PEBBLE), slot(PEBBLE), slot(PEBBLE),
                            slot(PEBBLE), slot(PEBBLE), slot(PEBBLE), slot(PEBBLE)));

            List<GooConversionFlows.Flow> flows = flowsOf("""
                    {"iron_ingot": {"metal": 9}, "pebble": {"rock": 1}, "boulder": {"rock": 8}}
                    """, recipes);

            assertEquals(List.of(List.of("pebble", "boulder"), List.of("pebble")),
                    flows.stream().map(GooConversionFlowsTest::outputsOf).toList());
            assertEquals(GooFraction.of(72), flows.getFirst().sourceLost());
            assertEquals(GooFraction.of(8), flows.getFirst().targetGained());
        }
    }
}
