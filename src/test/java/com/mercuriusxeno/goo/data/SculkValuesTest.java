package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shrooms and sculk are one goo type, sculk: the bundled types hold sculk in shroom's place, and the shipped base
 * values, read through the classpath, give sculk for mushrooms, mycelium and sculk alike.
 * decision shroom-is-a-universal-sculk-input
 */
class SculkValuesTest {

    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static final ResourceKey<GooTypeDefinition> SCULK = GooTypes.bundled("sculk");
    private static Map<Identifier, GooValue> shipped;

    @BeforeAll
    static void loadShippedBaseValues() throws IOException {
        try (InputStream in = SculkValuesTest.class.getResourceAsStream(BASE_VALUES)) {
            assertNotNull(in, BASE_VALUES);
            shipped = GooBaseValuesPreview.load(new String(in.readAllBytes(), StandardCharsets.UTF_8)).baseValues();
        }
    }

    @Test
    void bundledTypesHoldSculkInShroomsPlace() {
        List<String> bundled = GooTypes.BUNDLED.stream().map(GooTypes::id).toList();
        assertEquals(List.of(true, false), List.of(bundled.contains("sculk"), bundled.contains("shroom")),
                "sculk and shroom in " + bundled);
    }

    @ParameterizedTest
    @ValueSource(strings = {"brown_mushroom", "mycelium", "sculk"})
    void shroomAndSculkMatterGivesSculk(String item) {
        GooValue value = shipped.get(Identifier.withDefaultNamespace(item));
        assertNotNull(value, "base_values.json values no " + item);
        assertTrue(value.exact(SCULK).isPositive(), item + " gives no sculk: " + value);
    }
}
