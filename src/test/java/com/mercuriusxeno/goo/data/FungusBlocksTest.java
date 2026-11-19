package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A fungus block is a block whose block item's goo value holds sculk: every shroom, the nether's trees and growth,
 * and the sculk family, and no stone; read against the shipped base values through the classpath.
 * operator ruling 2026-11-18: a fungus block is a block whose block item contains sculk goo
 * decision shroom-is-a-universal-sculk-input
 */
class FungusBlocksTest {

    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static final List<String> FUNGUS = List.of("brown_mushroom", "red_mushroom_block", "mycelium",
            "crimson_fungus", "warped_nylium", "crimson_stem", "stripped_warped_hyphae", "nether_wart_block",
            "warped_wart_block", "weeping_vines", "shroomlight", "sculk", "sculk_catalyst");
    private static final List<String> NOT_FUNGUS = List.of("stone", "oak_log", "dirt");
    private static IGooValueLookup shipped;

    @BeforeAll
    static void loadShippedBaseValues() throws IOException {
        try (InputStream in = FungusBlocksTest.class.getResourceAsStream(BASE_VALUES)) {
            assertNotNull(in, BASE_VALUES);
            shipped = GooValueTable.ofEffectiveValues(GooBaseValuesPreview.load(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8)).baseValues());
        }
    }

    @Test
    void blocksWhoseItemHoldsSculkAreFungusAndNoOthers() {
        List<String> blocks = new java.util.ArrayList<>(FUNGUS);
        blocks.addAll(NOT_FUNGUS);

        Set<String> fungus = FungusBlocks.containingSculk(blocks, Identifier::withDefaultNamespace, shipped);

        assertEquals(Set.copyOf(FUNGUS), fungus);
    }

    @Test
    void aBlockWithNoItemIsNoFungus() {
        assertEquals(Set.of(), FungusBlocks.containingSculk(List.of("sculk"), block -> null, shipped));
    }
}
