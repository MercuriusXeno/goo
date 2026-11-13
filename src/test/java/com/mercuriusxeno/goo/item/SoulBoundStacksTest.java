package com.mercuriusxeno.goo.item;

import com.google.gson.JsonElement;
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
 * The goo:soul_bound item tag a stack keeps through death, read from its shipped JSON on the classpath
 * (decision exorite-kept-through-death).
 */
class SoulBoundStacksTest {

    @Test
    void theTagHoldsTheNineExoritePiecesAndTheExoGauntlet() throws IOException {
        assertEquals(List.of("goo:exorite_pickaxe", "goo:exorite_axe", "goo:exorite_shovel", "goo:exorite_hoe",
                        "goo:exorite_sword", "goo:exorite_helmet", "goo:exorite_chestplate", "goo:exorite_leggings",
                        "goo:exorite_boots", "goo:exo_gauntlet"),
                tagValues("/data/" + SoulBoundStacks.SOUL_BOUND.location().getNamespace() + "/tags/item/"
                        + SoulBoundStacks.SOUL_BOUND.location().getPath() + ".json"));
    }

    private static List<String> tagValues(String path) throws IOException {
        try (InputStream in = SoulBoundStacksTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " should be on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("values").asList().stream().map(JsonElement::getAsString).toList();
        }
    }
}
