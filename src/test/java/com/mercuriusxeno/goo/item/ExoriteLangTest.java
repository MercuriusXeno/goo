package com.mercuriusxeno.goo.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * en_us.json names the exorite upgrade template and its four description lines, and each of the nine exorite pieces.
 */
class ExoriteLangTest {

    private static final String LANG = "/assets/goo/lang/en_us.json";
    private static final List<String> PIECES = List.of("pickaxe", "axe", "shovel", "hoe", "sword", "helmet",
            "chestplate", "leggings", "boots");

    @Test
    void theTemplatesNameAndFourDescriptionLinesEachHaveATranslation() throws IOException {
        List<String> keys = List.of("item.goo.exorite_upgrade_smithing_template", ExoriteUpgradeTemplate.APPLIES_TO_KEY,
                ExoriteUpgradeTemplate.INGREDIENTS_KEY, ExoriteUpgradeTemplate.BASE_SLOT_KEY,
                ExoriteUpgradeTemplate.ADDITIONS_SLOT_KEY);
        JsonObject lang = lang();
        assertEquals(keys, keys.stream().filter(lang::has).toList());
    }

    @Test
    void eachExoritePieceHasAName() throws IOException {
        List<String> keys = PIECES.stream().map(piece -> "item.goo.exorite_" + piece).toList();
        JsonObject lang = lang();
        assertEquals(keys, keys.stream().filter(lang::has).toList());
    }

    private static JsonObject lang() throws IOException {
        try (InputStream in = ExoriteLangTest.class.getResourceAsStream(LANG)) {
            assertNotNull(in, LANG + " should be on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
