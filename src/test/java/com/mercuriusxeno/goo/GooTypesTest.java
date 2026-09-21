package com.mercuriusxeno.goo;

import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests that the GooTypes keys address the goo:goo_type registry and line up
 * one-to-one with the enum ids the bridge resolves through.
 */
class GooTypesTest {

    /**
     * The registry key is goo:goo_type.
     */
    @Test
    void registryKeyIsGooType() {
        assertEquals("goo:goo_type", GooTypes.REGISTRY.identifier().toString());
    }

    /**
     * Each bundled key sits in the goo namespace of the goo type registry.
     */
    @Test
    void bundledKeysAddressTheRegistry() {
        for (ResourceKey<GooTypeDefinition> key : GooTypes.BUNDLED) {
            assertSame(GooTypes.REGISTRY, key.registryKey(), key + " is outside the goo type registry");
            assertEquals("goo", key.identifier().getNamespace(), key + " is outside the goo namespace");
        }
    }

    /**
     * The bundled key paths are exactly the enum ids, so every enum value has
     * a key and no key lacks an enum value while the enum stands.
     */
    @Test
    void bundledPathsMatchEnumIds() {
        Set<String> keyPaths = GooTypes.BUNDLED.stream()
                .map(key -> key.identifier().getPath())
                .collect(Collectors.toSet());
        Set<String> enumIds = Stream.of(GooType.values())
                .map(GooType::getId)
                .collect(Collectors.toSet());
        assertEquals(enumIds, keyPaths);
    }

    /**
     * The enum's key() answers the same interned key as the GooTypes constant.
     */
    @Test
    void enumKeyIsTheBundledKey() {
        for (GooType type : GooType.values()) {
            assertSame(GooTypes.bundled(type.getId()), type.key());
        }
    }
}
