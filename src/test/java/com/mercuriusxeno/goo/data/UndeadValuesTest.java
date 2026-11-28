package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The undead's items give zombie goo and no nether, while the nether as a place keeps its nether goo; reads the
 * shipped base values through the classpath.
 * decision zombie-goo-is-the-undead-and-takes-the-decay-abilities
 */
class UndeadValuesTest {

    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static Map<Identifier, GooValue> shipped;

    @BeforeAll
    static void loadShippedBaseValues() throws IOException {
        try (InputStream in = UndeadValuesTest.class.getResourceAsStream(BASE_VALUES)) {
            assertNotNull(in, BASE_VALUES);
            shipped = GooBaseValuesPreview.load(new String(in.readAllBytes(), StandardCharsets.UTF_8)).baseValues();
        }
    }

    private static GooValue valueOf(String item) {
        GooValue value = shipped.get(Identifier.withDefaultNamespace(item));
        assertNotNull(value, "base_values.json values no " + item);
        return value;
    }

    @ParameterizedTest
    @ValueSource(strings = {"rotten_flesh", "zombie_head"})
    void undeadMatterGivesZombieAndNoNether(String item) {
        GooValue value = valueOf(item);
        assertTrue(value.exact(GooTypes.ZOMBIE).isPositive(), item + " gives no zombie: " + value);
        assertTrue(value.exact(GooTypes.NETHER).isZero(), item + " gives nether: " + value);
    }

    @Test
    void netherrackKeepsItsNether() {
        GooValue value = valueOf("netherrack");
        assertTrue(value.exact(GooTypes.NETHER).isPositive(), "netherrack gives no nether: " + value);
    }
}
