package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.recipe;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the conversion flows: each recipe whose craft loses one goo type and gains another, the most gained per
 * craft first (decision workbench-lists-type-to-type-paths-worst-first).
 */
class GooConversionFlowsTest {

    private static final String IRON = "minecraft:iron_ingot";
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

    @Test
    void derivedRecipeListsNoFlow() {
        List<GooConversionFlows.Flow> flows = flowsOf("""
                {"oak_planks": {"leaf": 2}}
                """, List.of(recipe("minecraft:stick", 4, slot("minecraft:oak_planks"), slot("minecraft:oak_planks"))));

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

        List<String> outputs = flowsOf(TRANSMUTING, recipes).stream()
                .map(flow -> flow.recipe().output().getPath()).toList();

        assertEquals(List.of("lantern", "chain"), outputs);
        assertEquals(outputs, flowsOf(TRANSMUTING, recipes.reversed()).stream()
                .map(flow -> flow.recipe().output().getPath()).toList());
    }
}
