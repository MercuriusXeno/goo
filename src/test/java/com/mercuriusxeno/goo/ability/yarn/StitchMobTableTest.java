package com.mercuriusxeno.goo.ability.yarn;

import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Covers the layers each shipped goo:stitch_mob entry takes, squashed to 8:
 * each mob's old layers times 8/12 rounded up, read from its JSON on the classpath.
 * stitch-mob-table-squashes-to-eight-layers
 */
class StitchMobTableTest {

    private static final String TABLE = "/data/goo/goo/stitch_mob/";

    @ParameterizedTest
    @CsvSource({"armadillo, 2", "camel, 7", "cat, 3", "chicken, 2", "cow, 4", "donkey, 5", "fox, 3", "frog, 2",
        "goat, 4", "horse, 6", "llama, 5", "mooshroom, 6", "mule, 6", "ocelot, 3", "panda, 6", "parrot, 2",
        "pig, 4", "polar_bear, 6", "rabbit, 2", "sheep, 4", "sniffer, 8", "turtle, 3", "wolf, 4"})
    void eachMobTakesItsSquashedLayers(String mob, int layers) throws IOException {
        String path = TABLE + mob + ".json";
        try (InputStream in = StitchMobTableTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                assertEquals(layers, JsonParser.parseReader(reader).getAsJsonObject().get("layers").getAsInt());
            }
        }
    }
}
