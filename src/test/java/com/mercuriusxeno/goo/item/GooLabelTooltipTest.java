package com.mercuriusxeno.goo.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypeNames;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The goo label's tooltip line names its goo type, through a lang entry that
 * spells it (decision hud-panel-shows-the-label-type). Reads the lang file
 * through the classpath.
 */
class GooLabelTooltipTest {

    private static final String LANG = "/assets/goo/lang/en_us.json";

    @Test
    void theTooltipLineNamesTheLabelsType() throws IOException {
        Component line = GooLabelItem.tooltipLine(GooTypes.BLAZE);

        assertTrue(line.getContents() instanceof TranslatableContents, "the line should be translatable: " + line);
        TranslatableContents contents = (TranslatableContents) line.getContents();
        assertEquals(GooTypeNames.name(GooTypes.BLAZE), contents.getArgs()[0]);
        assertTrue(lang().has(contents.getKey()), "the lang file should spell " + contents.getKey());
    }

    private static JsonObject lang() throws IOException {
        try (InputStream in = GooLabelTooltipTest.class.getResourceAsStream(LANG)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
