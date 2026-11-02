package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An entry naming an item that holds no value loads no value, rather than reading the item as zero: the 26.1 draft's
 * "36 lapis_lazuli + $mundane_block", with lapis_lazuli unvalued, loaded as $mundane_block alone and handed rock to
 * lapis, its dyes and every flower the solver reached from them.
 */
class UnresolvedItemReferenceTest {
    private static final String FILE = """
            {
                "_constants": {"mundane_block": {"rock": 6}},
                "bowl": {"leaf": 3},
                "lapis_ore": "36 lapis_lazuli + $mundane_block",
                "copper_ore": {"rock": "$mundane_block.rock", "metal": "raw_copper.metal"},
                "wooden_thing": "2 bowl + $mundane_block"
            }
            """;

    private static Map<Identifier, GooValue> loaded() throws IOException {
        return GooBaseValuesPreview.baseValues(FILE);
    }

    /**
     * An expression naming an unvalued item loads nothing for its entry.
     */
    @Test
    void expressionNamingAnUnvaluedItemLoadsNoValue() throws IOException {
        assertFalse(loaded().containsKey(Identifier.withDefaultNamespace("lapis_ore")), String.valueOf(loaded()));
    }

    /**
     * An object entry whose type reads an unvalued item loads nothing for its entry.
     */
    @Test
    void objectEntryReadingAnUnvaluedItemLoadsNoValue() throws IOException {
        assertFalse(loaded().containsKey(Identifier.withDefaultNamespace("copper_ore")), String.valueOf(loaded()));
    }

    /**
     * An entry whose items all hold values still loads.
     */
    @Test
    void entryNamingValuedItemsStillLoads() throws IOException {
        assertTrue(loaded().containsKey(Identifier.withDefaultNamespace("wooden_thing")), String.valueOf(loaded()));
    }
}
