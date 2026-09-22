package com.mercuriusxeno.goo;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests that the GooTypes keys address the goo:goo_type registry, line up
 * one-to-one with the enum ids the bridge resolves through, and round-trip
 * through the codecs the GOO_TYPE component carries.
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

    /**
     * The GOO_TYPE component's codecs round-trip a key, bundled or from a
     * datapack, as its id in JSON and as its id on the wire (decision
     * generic-goo-items).
     */
    @Test
    void keyCodecsRoundTrip() {
        ResourceKey<GooTypeDefinition> datapack = ResourceKey.create(
                GooTypes.REGISTRY, Identifier.fromNamespaceAndPath("gootest", "seventeenth"));
        for (ResourceKey<GooTypeDefinition> key : List.of(GooTypes.BLAZE, datapack)) {
            JsonElement json = GooTypes.KEY_CODEC.encodeStart(JsonOps.INSTANCE, key).getOrThrow();
            assertEquals(key.identifier().toString(), json.getAsString());
            assertEquals(key, GooTypes.KEY_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());

            ByteBuf buf = Unpooled.buffer();
            GooTypes.KEY_STREAM_CODEC.encode(buf, key);
            assertEquals(key, GooTypes.KEY_STREAM_CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        }
    }

    /**
     * fromKey inverts key() for bundled keys and answers null for a key
     * outside the goo namespace or with an id no enum value holds.
     */
    @Test
    void fromKeyInvertsKeyForBundledTypesOnly() {
        for (GooType type : GooType.values()) {
            assertSame(type, GooType.fromKey(type.key()));
        }
        assertNull(GooType.fromKey(ResourceKey.create(
                GooTypes.REGISTRY, Identifier.fromNamespaceAndPath("gootest", "blaze"))));
        assertNull(GooType.fromKey(GooTypes.bundled("seventeenth")));
    }
}
