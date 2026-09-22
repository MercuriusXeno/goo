package com.mercuriusxeno.goo;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the GooTypes keys address the goo:goo_type registry, spell and
 * parse the short ids data files use, order the way the enum constants did,
 * and round-trip through the codecs the GOO_TYPE component carries
 * (decision datapack-goo-registry).
 */
class GooTypesTest {

    private static final ResourceKey<GooTypeDefinition> SEVENTEENTH = ResourceKey.create(
            GooTypes.REGISTRY, Identifier.fromNamespaceAndPath("gootest", "seventeenth"));
    private static final List<String> BUNDLED_IDS = List.of(
            "aeon", "blaze", "crystal", "ender", "frost", "glow", "hex", "leaf",
            "metal", "nether", "pulse", "rock", "shroom", "typhoon", "unstable", "vital");

    /**
     * The registry key is goo:goo_type.
     */
    @Test
    void registryKeyIsGooType() {
        assertEquals("goo:goo_type", GooTypes.REGISTRY.identifier().toString());
    }

    /**
     * Each bundled key sits in the goo namespace of the goo type registry, in
     * the constant order, which is the ids the enum constants held.
     */
    @Test
    void bundledKeysAddressTheRegistryInEnumOrder() {
        assertEquals(BUNDLED_IDS, GooTypes.BUNDLED.stream().map(GooTypes::id).toList());
        for (ResourceKey<GooTypeDefinition> key : GooTypes.BUNDLED) {
            assertSame(GooTypes.REGISTRY, key.registryKey(), key + " is outside the goo type registry");
            assertEquals("goo", key.identifier().getNamespace(), key + " is outside the goo namespace");
        }
    }

    /**
     * A bundled type's id is its bare path and a datapack type's id carries
     * its namespace; byId reads both spellings back to the interned key and
     * refuses text no identifier accepts.
     */
    @Test
    void idAndByIdInvertEachOther() {
        assertEquals("blaze", GooTypes.id(GooTypes.BLAZE));
        assertEquals("gootest:seventeenth", GooTypes.id(SEVENTEENTH));
        assertSame(GooTypes.BLAZE, GooTypes.byId("blaze"));
        assertSame(GooTypes.BLAZE, GooTypes.byId("goo:blaze"));
        assertSame(SEVENTEENTH, GooTypes.byId("gootest:seventeenth"));
        assertNull(GooTypes.byId("Not An Id"));
    }

    /**
     * Before a level captures the registry the known set is the bundled one:
     * a bundled id parses, a datapack id is unknown, and parseKnown refuses
     * it the way the enum's valueOf refused an unknown constant.
     */
    @Test
    void knownTypesAreTheBundledSetBeforeCapture() {
        assertEquals(GooTypes.BUNDLED, GooTypes.order());
        assertSame(GooTypes.ROCK, GooTypes.known("rock"));
        assertSame(GooTypes.ROCK, GooTypes.parseKnown("rock"));
        assertNull(GooTypes.known("gootest:seventeenth"));
        assertThrows(IllegalArgumentException.class, () -> GooTypes.parseKnown("gootest:seventeenth"));
        assertEquals(BUNDLED_IDS.indexOf("metal"), GooTypes.indexOf(GooTypes.METAL));
        assertEquals(-1, GooTypes.indexOf(SEVENTEENTH));
    }

    /**
     * ORDER sorts by identifier, namespace then path, so the bundled set
     * sorts as the enum did and a datapack type follows it.
     */
    @Test
    void orderSortsByIdentifier() {
        assertTrue(GooTypes.ORDER.compare(GooTypes.AEON, GooTypes.BLAZE) < 0);
        assertTrue(GooTypes.ORDER.compare(GooTypes.VITAL, SEVENTEENTH) < 0);
        assertEquals(0, GooTypes.ORDER.compare(GooTypes.LEAF, GooTypes.bundled("leaf")));
    }

    /**
     * ID_CODEC writes a bundled type as its bare id and a datapack type
     * namespaced, and reads both back, so the enum-era data files still load.
     */
    @Test
    void idCodecSpellsShortIds() {
        assertEquals("blaze", GooTypes.ID_CODEC.encodeStart(JsonOps.INSTANCE, GooTypes.BLAZE)
                .getOrThrow().getAsString());
        assertEquals("gootest:seventeenth", GooTypes.ID_CODEC.encodeStart(JsonOps.INSTANCE, SEVENTEENTH)
                .getOrThrow().getAsString());
        assertSame(GooTypes.BLAZE, GooTypes.ID_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"blaze\""))
                .getOrThrow());
        assertSame(SEVENTEENTH, GooTypes.ID_CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("\"gootest:seventeenth\"")).getOrThrow());
        assertTrue(GooTypes.ID_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"Not An Id\"")).isError());
    }

    /**
     * The GOO_TYPE component's codecs round-trip a key, bundled or from a
     * datapack, as its id in JSON and as its id on the wire (decision
     * generic-goo-items).
     */
    @Test
    void keyCodecsRoundTrip() {
        for (ResourceKey<GooTypeDefinition> key : List.of(GooTypes.BLAZE, SEVENTEENTH)) {
            JsonElement json = GooTypes.KEY_CODEC.encodeStart(JsonOps.INSTANCE, key).getOrThrow();
            assertEquals(key.identifier().toString(), json.getAsString());
            assertEquals(key, GooTypes.KEY_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());

            ByteBuf buf = Unpooled.buffer();
            GooTypes.KEY_STREAM_CODEC.encode(buf, key);
            assertEquals(key, GooTypes.KEY_STREAM_CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        }
    }
}
