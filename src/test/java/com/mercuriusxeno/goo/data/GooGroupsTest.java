package com.mercuriusxeno.goo.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The base values' custom groups read in file order, and a member paired with the member standing at its position in
 * another group (decision this-becomes-that-is-two-parallel-groups-in-the-goo-workbench).
 */
class GooGroupsTest {

    private static final String SHIPPED_BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static final Identifier LAVA = Identifier.parse("minecraft:lava");
    private static final Identifier MOLTEN_IRON = Identifier.parse("goo:molten_iron");
    private static final Identifier OBSIDIAN = Identifier.parse("minecraft:obsidian");
    private static final Identifier IRON_CORE = Identifier.parse("goo:iron_core");

    private static JsonObject shippedBaseValues() throws IOException {
        try (InputStream in = GooGroupsTest.class.getResourceAsStream(SHIPPED_BASE_VALUES);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Nested
    class ShippedGroups {

        @Test
        void moltenBlocksHoldLavaThenMoltenIron() throws IOException {
            assertEquals(List.of(LAVA, MOLTEN_IRON), GooGroups.groupsOf(shippedBaseValues()).get("molten_blocks"));
        }

        @Test
        void moltenCooledBlocksHoldObsidianThenIronCore() throws IOException {
            assertEquals(List.of(OBSIDIAN, IRON_CORE),
                    GooGroups.groupsOf(shippedBaseValues()).get("molten_cooled_blocks"));
        }
    }

    /**
     * A pack pairs one more liquid with its cooled block by copying both shipped groups, appending one member to
     * each at the same position, and shipping them as replacement lists over the mod's (operator ruling
     * 2026-11-16).
     */
    @Nested
    class SecondPack {

        private static final Identifier MOLTEN_GOLD = Identifier.parse("pack:molten_gold");
        private static final Identifier GOLD_CORE = Identifier.parse("pack:gold_core");

        @Test
        void aPackAppendingOnePairLandsItAtTheSameIndexInBothGroups() throws IOException {
            JsonObject pack = JsonParser.parseString("""
                    {
                        "_groups": {
                            "molten_blocks": ["lava", "goo:molten_iron", "pack:molten_gold"],
                            "molten_cooled_blocks": ["obsidian", "goo:iron_core", "pack:gold_core"]
                        }
                    }
                    """).getAsJsonObject();
            Map<String, List<Identifier>> merged = GooGroups.groupsOf(
                    GooValueMerger.mergeBaseValueJsonLayers(List.of(shippedBaseValues(), pack)));
            assertEquals(List.of(LAVA, MOLTEN_IRON, MOLTEN_GOLD), merged.get("molten_blocks"));
            assertEquals(List.of(OBSIDIAN, IRON_CORE, GOLD_CORE), merged.get("molten_cooled_blocks"));
            assertEquals(Optional.of(GOLD_CORE),
                    GooGroups.pairedMember(merged, "molten_blocks", "molten_cooled_blocks", MOLTEN_GOLD));
        }
    }

    @Nested
    class PairedMember {

        private final Map<String, List<Identifier>> groups = Map.of(
                "from", List.of(LAVA, MOLTEN_IRON),
                "to", List.of(OBSIDIAN));

        @Test
        void aMemberPairsWithTheMemberAtItsPosition() {
            assertEquals(Optional.of(OBSIDIAN), GooGroups.pairedMember(groups, "from", "to", LAVA));
        }

        @Test
        void aMemberWithNoPartnerAtItsPositionPairsWithNothing() {
            assertEquals(Optional.empty(), GooGroups.pairedMember(groups, "from", "to", MOLTEN_IRON));
        }
    }
}
