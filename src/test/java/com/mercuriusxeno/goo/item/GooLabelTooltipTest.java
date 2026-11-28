package com.mercuriusxeno.goo.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The goo label's lang file spells no tooltip line, so its hover is its item name alone.
 * Reads the lang file through the classpath.
 */
class GooLabelTooltipTest {

    private static final String LANG = "/assets/goo/lang/en_us.json";

    // decision goo-label-carries-no-tooltip-line
    @Test
    void theLangFileSpellsNoLabelTooltipLine() throws IOException {
        assertFalse(lang().has("item.goo.goo_label.tooltip"), "the lang file should spell no goo label tooltip line");
    }

    private static JsonObject lang() throws IOException {
        try (InputStream in = GooLabelTooltipTest.class.getResourceAsStream(LANG)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
