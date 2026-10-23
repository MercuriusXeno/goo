package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.RecipeInput;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.recipe;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the CONVERSION FLOWS section: per source-to-target goo type pair, each recipe's loss and gain per craft
 * (decision workbench-lists-type-to-type-paths-worst-first).
 */
class ConversionFlowsAuditRuleTest {

    private static final String IRON = "minecraft:iron_ingot";
    private static final String JSON = """
            {
                "iron_ingot": {"metal": 9},
                "chain": {"rock": 4}
            }
            """;
    private static final List<RecipeInput> RECIPES = List.of(recipe("minecraft:chain", 1, slot(IRON), slot(IRON)));

    @Test
    void flowReadsUnderItsTypePairWithLossAndGain() {
        GooAuditSection section = new ConversionFlowsAuditRule().run(new GooAuditInputs(
                GooBaseValuesPreview.derivedRegistry(JSON, RECIPES), List.of(), Set.of()));

        assertEquals("CONVERSION FLOWS (1 type pairs)", section.header());
        assertEquals(List.of("metal -> rock", "  2 iron_ingot -> 1 chain: loses 18 metal, gains 4 rock per craft"),
                section.lines());
    }

    @Test
    void pathLineJoinsItsRecipesInOrder() {
        String nugget = "minecraft:iron_nugget";
        List<RecipeInput> recipes = List.of(recipe(nugget, 9, slot(IRON)),
                recipe("minecraft:chain", 1, slot(nugget), slot(nugget), slot(nugget)));

        GooAuditSection section = new ConversionFlowsAuditRule().run(new GooAuditInputs(
                GooBaseValuesPreview.derivedRegistry("""
                        {"iron_ingot": {"metal": 9}, "chain": {"rock": 2}}
                        """, recipes), List.of(), Set.of()));

        assertEquals(List.of("metal -> rock", "  3 iron_nugget -> 1 chain: loses 3 metal, gains 2 rock per craft",
                "  1 iron_ingot -> 9 iron_nugget then 3 iron_nugget -> 1 chain: loses 3 metal, gains 2 rock per craft"),
                section.lines());
    }
}
