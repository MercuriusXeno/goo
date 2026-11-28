package com.mercuriusxeno.goo.registry;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.lunar.VolatileCrystalBlock;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.EntityBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Volatile crystal registers as a block with no block entity and an item, carries both crystal and unstable in its
 * shipped base value, and stands in goo:zap_receivers so Zap and the wireless emitter count it as taking a signal.
 * decision volatile-crystals-are-rich-in-crystal-and-unstable
 */
class VolatileCrystalTest {

    private static final Identifier VOLATILE_CRYSTAL = Identifier.fromNamespaceAndPath("goo", "volatile_crystal");

    private static String resourceText(String path) throws IOException {
        try (InputStream stream = VolatileCrystalTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void registersAsABlockAndAnItem() {
        assertTrue(GooBlocks.BLOCKS.getEntries().stream().map(DeferredHolder::getId).anyMatch(VOLATILE_CRYSTAL::equals),
                "no block answers " + VOLATILE_CRYSTAL);
        assertTrue(GooItems.ITEMS.getEntries().stream().map(DeferredHolder::getId).anyMatch(VOLATILE_CRYSTAL::equals),
                "no item answers " + VOLATILE_CRYSTAL);
    }

    @Test
    void holdsNoBlockEntity() {
        assertFalse(EntityBlock.class.isAssignableFrom(VolatileCrystalBlock.class),
                "VolatileCrystalBlock is an EntityBlock");
    }

    @Test
    void carriesCrystalAndUnstable() throws IOException {
        GooValue value = GooBaseValuesPreview.load(resourceText("/data/goo/goo_values/base_values.json")).baseValues()
                .get(VOLATILE_CRYSTAL);
        assertNotNull(value, "base_values.json values no " + VOLATILE_CRYSTAL);
        assertTrue(value.exact(GooTypes.CRYSTAL).isPositive(), VOLATILE_CRYSTAL + " carries no crystal: " + value);
        assertTrue(value.exact(GooTypes.UNSTABLE).isPositive(), VOLATILE_CRYSTAL + " carries no unstable: " + value);
    }

    @Test
    void standsInTheZapReceiversTag() throws IOException {
        List<String> receivers = JsonParser.parseString(resourceText("/data/goo/tags/block/zap_receivers.json"))
                .getAsJsonObject().getAsJsonArray("values").asList().stream().map(v -> v.getAsString()).toList();
        assertTrue(receivers.contains(VOLATILE_CRYSTAL.toString()), "zap_receivers names " + receivers);
    }
}
