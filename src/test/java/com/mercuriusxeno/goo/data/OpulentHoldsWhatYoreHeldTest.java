package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Each item that held yore goo holds its opulent amount once yore leaves: the yore it held, moved one for one,
 * or, where opulent already stood beside yore, the opulent it held. Reads the shipped base values through the
 * classpath against the list the commit message names.
 * decision yore-leaves-and-rewind-moves-to-xeno-as-revert
 */
class OpulentHoldsWhatYoreHeldTest {

    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static Map<Identifier, GooValue> shipped;

    @BeforeAll
    static void loadShippedBaseValues() throws IOException {
        try (InputStream in = OpulentHoldsWhatYoreHeldTest.class.getResourceAsStream(BASE_VALUES)) {
            assertNotNull(in, BASE_VALUES);
            shipped = GooBaseValuesPreview.load(new String(in.readAllBytes(), StandardCharsets.UTF_8)).baseValues();
        }
    }

    @ParameterizedTest(name = "{0} holds {1}/{2} opulent")
    @CsvFileSource(resources = "/goo_values/opulent_holds_what_yore_held.csv")
    void itemHoldsItsOpulent(String item, long numerator, long denominator) {
        GooValue value = shipped.get(Identifier.withDefaultNamespace(item));
        assertNotNull(value, "base_values.json values no " + item);
        assertEquals(GooFraction.of(numerator, denominator), value.exact(GooTypes.OPULENT), item + ": " + value);
    }
}
