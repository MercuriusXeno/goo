package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.RecipeInput;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.recipe;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the ROOT REMAINDERS section: per root _roots names, the items it solves, their count and each recipe's
 * remainder at the root (decision workbench-shows-each-roots-remainders-and-items-solved).
 */
class RootRemaindersAuditRuleTest {

    private static final String GLASS = "minecraft:glass";
    private static final String JSON = """
            {
                "_roots": ["glass"],
                "glass": {"rock": 8},
                "white_dye": {"leaf": 1}
            }
            """;
    private static final List<RecipeInput> RECIPES = List.of(
            recipe("minecraft:glass_pane", 16, slot(GLASS), slot(GLASS), slot(GLASS), slot(GLASS), slot(GLASS),
                    slot(GLASS)),
            recipe("minecraft:glass_bottle", 3, slot(GLASS), slot(GLASS), slot(GLASS)),
            recipe("minecraft:white_stained_glass", 8, slot(GLASS), slot(GLASS), slot(GLASS), slot(GLASS),
                    slot(GLASS), slot(GLASS), slot(GLASS), slot(GLASS), slot("minecraft:white_dye")));

    private static GooAuditSection section() {
        return new RootRemaindersAuditRule().run(new GooAuditInputs(
                GooBaseValuesPreview.derivedRegistry(JSON, RECIPES), List.of(), Set.of()));
    }

    @Test
    void rootOfThreeSolvedItemsReadsCountThree() {
        GooAuditSection section = section();

        assertEquals("ROOT REMAINDERS (1 roots)", section.header());
        assertTrue(section.lines().contains(
                "glass solves 3 items: glass_bottle, glass_pane, white_stained_glass; smallest remainder 3/8"),
                String.join("\n", section.lines()));
    }

    @Test
    void eachRecipeRowReadsItsRemainderNonDividingFirst() {
        List<String> rows = section().lines().stream().filter(line -> line.startsWith("  ")).toList();

        assertEquals(List.of(
                "  6 glass -> 16 glass_pane: input 6, each 3/8, remainder 3/8",
                "  3 glass -> 3 glass_bottle: input 3, each 1, remainder 0",
                "  8 glass + 1 white_dye -> 8 white_stained_glass: input 8, each 1, remainder 0"), rows);
    }
}
