package com.mercuriusxeno.goo.ability.rock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Rock's world goo ships as rock_mortar.json, read in en_us.json as Mortar
 * with a description, and its cost prices a block of Mortar so that each of
 * its layers costs what one stone block costs, read against the shipped base
 * values without naming any shipped number.
 * mortar-is-rocks-residual-a-petrification-and-hardening-goo
 * layer-of-rock-goo-is-a-stone-block
 */
class MortarAbilityTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static final String MORTAR_KEY = "goo.ability.rock.mortar";

    @Test
    void mortarReadsMortarWithADescription() throws IOException {
        JsonObject lang = JsonParser.parseString(classpathText(EN_US)).getAsJsonObject();

        assertEquals("Mortar", lang.get(MORTAR_KEY).getAsString());
        assertNotNull(lang.get(MORTAR_KEY + ".description"), MORTAR_KEY + ".description");
        assertFalse(lang.get(MORTAR_KEY + ".description").getAsString().isBlank(), MORTAR_KEY + ".description");
    }

    @Test
    void aLayerOfMortarCostsOneMundaneBlock() throws IOException {
        IGooValueLookup shipped = GooBaseValuesPreview.registry(classpathText(BASE_VALUES)).table();
        GooFraction mundaneBlock = AbilityJson.decodeText("mundane_block_priced", "{\"gooType\": \"rock\","
                + " \"displayName\": \"mundane_block_priced\", \"cost\": \"$mundane_block\", \"badge\": \"world\","
                + " \"behaviors\": [], \"delivery\": {\"kind\": \"arc\"}}").cost().exactIn(shipped);
        GooFraction mortarBlock = AbilityJson.decode("rock_mortar").cost().exactIn(shipped);

        assertEquals(mundaneBlock, mortarBlock.dividedBy(VolumeCellBlock.MAX_LAYERS));
    }

    private static String classpathText(String path) throws IOException {
        try (InputStream in = MortarAbilityTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
