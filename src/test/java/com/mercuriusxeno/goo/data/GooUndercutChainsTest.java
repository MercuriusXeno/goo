package com.mercuriusxeno.goo.data;

import org.junit.jupiter.api.Test;
import java.util.List;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.recipe;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the undercut chains: each hand-keyed value a cheaper derived value replaced, with the chain of items the
 * derived value came through back to each hand-keyed entry that held, every backward solve marked at its step
 * (decision workbench-traces-each-undercut-manual-value-to-its-roots).
 */
class GooUndercutChainsTest {

    private static final String BAMBOO = "minecraft:bamboo";
    private static final String STICK = "minecraft:stick";
    private static final List<RecipeInput> BAMBOO_TO_PLANKS = List.of(
            recipe(STICK, 1, slot(BAMBOO), slot(BAMBOO)),
            recipe("minecraft:bamboo_planks", 1, slot(STICK), slot(STICK)));

    private static List<GooUndercutChains.Undercut> undercutsOf(String json, List<RecipeInput> recipes) {
        return GooUndercutChains.traceEachUndercut(GooBaseValuesPreview.derivedRegistry(json, recipes));
    }

    private static String chainText(List<GooUndercutChains.Step> chain) {
        return String.join(" -> ", chain.stream()
                .map(step -> step.item().getPath() + (step.solvedBackward() ? " (backward)" : "")).toList());
    }

    private static List<String> chainsOf(List<GooUndercutChains.Undercut> undercuts, String item) {
        return undercuts.stream().filter(undercut -> undercut.item().getPath().equals(item)).findFirst()
                .orElseThrow(() -> new AssertionError(item + " not undercut in " + undercuts))
                .chains().stream().map(GooUndercutChainsTest::chainText).toList();
    }

    @Test
    void undercutItemChainsBackToItsEntry() {
        List<GooUndercutChains.Undercut> undercuts = undercutsOf("""
                {"bamboo": {"leaf": 1}, "bamboo_planks": {"leaf": 8}}
                """, BAMBOO_TO_PLANKS);

        assertEquals(List.of("bamboo_planks"), undercuts.stream().map(undercut -> undercut.item().getPath()).toList());
        assertEquals(List.of("bamboo -> stick -> bamboo_planks"), chainsOf(undercuts, "bamboo_planks"));
    }

    @Test
    void chainContinuesThroughAnUndercutKeyedItem() {
        List<GooUndercutChains.Undercut> undercuts = undercutsOf("""
                {"bamboo": {"leaf": 1}, "stick": {"leaf": 5}, "bamboo_planks": {"leaf": 8}}
                """, BAMBOO_TO_PLANKS);

        assertEquals(List.of("bamboo -> stick"), chainsOf(undercuts, "stick"));
        assertEquals(List.of("bamboo -> stick -> bamboo_planks"), chainsOf(undercuts, "bamboo_planks"));
    }

    @Test
    void backwardSolvedStepIsMarkedAndForwardStepsAreNot() {
        String nugget = "minecraft:iron_nugget";
        List<RecipeInput> recipes = List.of(
                recipe("minecraft:iron_ingot", 1, slot(nugget), slot(nugget), slot(nugget)),
                recipe("minecraft:chain", 1, slot(nugget)));

        List<GooUndercutChains.Undercut> undercuts = undercutsOf("""
                {"iron_ingot": {"metal": 9}, "chain": {"metal": 7}}
                """, recipes);

        assertEquals(List.of("iron_ingot -> iron_nugget (backward) -> chain"), chainsOf(undercuts, "chain"));
    }
}
