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
 * Tests the UNDERCUT MANUAL VALUES section: each hand-keyed item a cheaper derived value replaced, with both
 * values and each chain back to its entries (decision workbench-traces-each-undercut-manual-value-to-its-roots).
 */
class UndercutManualValuesAuditRuleTest {

    private static final String BAMBOO = "minecraft:bamboo";
    private static final String JSON = """
            {"bamboo": {"leaf": 1}, "stick": {"leaf": 1}, "scaffolding": {"leaf": 3}}
            """;
    private static final List<RecipeInput> RECIPES = List.of(
            recipe("minecraft:stick", 2, slot(BAMBOO)),
            recipe("minecraft:scaffolding", 1, slot(BAMBOO), slot(BAMBOO), slot(BAMBOO), slot(BAMBOO)));

    @Test
    void undercutItemReadsBothValuesAndItsChainAndDearerRecipeReadsNothing() {
        GooAuditSection section = new UndercutManualValuesAuditRule().run(new GooAuditInputs(
                GooBaseValuesPreview.derivedRegistry(JSON, RECIPES), List.of(), Set.of()));

        assertEquals("UNDERCUT MANUAL VALUES (1 items)", section.header());
        assertEquals(List.of("  stick: keyed 1 {leaf: 1}, derived 1/2 {leaf: 1/2}", "    bamboo -> stick"),
                section.lines());
        assertEquals(1, section.issues());
        assertEquals(1, section.flags().size());
    }
}
