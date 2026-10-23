package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.id;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.recipe;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests each root's solved items and every recipe's remainder at the root, non-dividing recipes first and the
 * smallest remainder first (decision workbench-shows-each-roots-remainders-and-items-solved).
 */
class GooRootRemaindersTest {

    private static final String ROOTS_JSON = """
            {
                "_roots": ["glass", "oak_planks"],
                "glass": {"rock": 8},
                "oak_planks": {"leaf": 4}
            }
            """;

    private static final List<RecipeInput> RECIPES = List.of(
            recipe("minecraft:glass_pane", 16, slot("minecraft:glass"), slot("minecraft:glass"),
                    slot("minecraft:glass"), slot("minecraft:glass"), slot("minecraft:glass"), slot("minecraft:glass")),
            recipe("minecraft:oak_planks", 4, slot("minecraft:oak_log")),
            recipe("minecraft:stick", 4, slot("minecraft:oak_planks"), slot("minecraft:oak_planks")),
            recipe("minecraft:oak_slab", 6, slot("minecraft:oak_planks"), slot("minecraft:oak_planks"),
                    slot("minecraft:oak_planks")),
            recipe("minecraft:ladder", 3, slot("minecraft:stick"), slot("minecraft:stick"), slot("minecraft:stick"),
                    slot("minecraft:stick"), slot("minecraft:stick"), slot("minecraft:stick"), slot("minecraft:stick")));

    @Test
    void panesLeadAndWholeRecipesTrail() {
        List<GooRootRemainders.RootSolution> solutions =
                GooRootRemainders.solveEachRoot(GooBaseValuesPreview.derivedRegistry(ROOTS_JSON, RECIPES));

        GooRootRemainders.RootSolution glass = solutions.getFirst();
        assertEquals(id("minecraft:glass"), glass.root());
        assertEquals(List.of(id("minecraft:glass_pane")), glass.solved());
        GooRootRemainders.Row pane = glass.rows().getFirst();
        assertEquals(GooFraction.of(6), pane.inputTotal());
        assertEquals(16, pane.outputCount());
        assertEquals(GooFraction.of(3, 8), pane.perOutput());
        assertEquals(GooFraction.of(3, 8), pane.remainder());

        GooRootRemainders.RootSolution planks = solutions.get(1);
        assertEquals(List.of(id("minecraft:ladder"), id("minecraft:oak_log"), id("minecraft:oak_slab"),
                id("minecraft:stick")), planks.solved());
        assertEquals(List.of(id("minecraft:ladder"), id("minecraft:oak_slab"), id("minecraft:stick"),
                id("minecraft:oak_planks")), planks.rows().stream().map(row -> row.recipe().output()).toList());
        assertEquals(List.of(GooFraction.of(1, 6), GooFraction.of(1, 2), GooFraction.of(1, 2), GooFraction.ZERO),
                planks.rows().stream().map(GooRootRemainders.Row::remainder).toList());
        GooRootRemainders.Row logIntoPlanks = planks.rows().getLast();
        assertEquals(GooFraction.of(4), logIntoPlanks.inputTotal());
        assertEquals(GooFraction.of(1, 6), planks.smallestRemainder());
    }

    @Test
    void handKeyedItemStopsTheWalkToTheRoot() {
        String json = """
                {"_roots": ["oak_planks"], "oak_planks": {"leaf": 4}, "stick": {"leaf": 3}}
                """;
        List<GooRootRemainders.RootSolution> solutions = GooRootRemainders.solveEachRoot(
                GooBaseValuesPreview.derivedRegistry(json, RECIPES));

        List<Identifier> solved = solutions.getFirst().solved();
        assertEquals(List.of(id("minecraft:oak_log"), id("minecraft:oak_slab")), solved);
    }
}
