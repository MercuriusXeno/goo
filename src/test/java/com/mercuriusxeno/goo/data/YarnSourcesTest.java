package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the shipped base values give yarn goo to wool, string and leather.
 * decision yarn-is-the-y-goo-of-wool-string-and-leather
 */
class YarnSourcesTest {

    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static Map<Identifier, GooValue> shipped;

    @BeforeAll
    static void loadShippedBaseValues() throws IOException {
        try (InputStream in = YarnSourcesTest.class.getResourceAsStream(BASE_VALUES)) {
            assertNotNull(in, BASE_VALUES + " is not on the classpath");
            shipped = GooBaseValuesPreview.load(new String(in.readAllBytes(), StandardCharsets.UTF_8)).baseValues();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"white_wool", "string", "leather"})
    void woolStringAndLeatherCarryYarn(String item) {
        GooValue value = shipped.get(Identifier.withDefaultNamespace(item));
        assertNotNull(value, "base_values.json values no " + item);
        assertTrue(value.exact(GooTypes.YARN).isPositive(), item + " carries no yarn: " + value);
    }
}
