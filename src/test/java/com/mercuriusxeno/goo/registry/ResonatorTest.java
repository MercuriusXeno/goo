package com.mercuriusxeno.goo.registry;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.lunar.ResonatorBlock;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.EntityBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The resonator registers as a block with no block entity and an item that drops itself when mined, and carries
 * pulse in its shipped base value.
 * decision resonator-echoes-a-vibration-as-a-sound-relay
 */
class ResonatorTest {

    private static final Identifier RESONATOR = Identifier.fromNamespaceAndPath("goo", "resonator");

    private static String resourceText(String path) throws IOException {
        try (InputStream stream = ResonatorTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void registersAsABlockAndAnItemThatDropsItself() throws IOException {
        assertTrue(GooBlocks.BLOCKS.getEntries().stream().map(DeferredHolder::getId).anyMatch(RESONATOR::equals),
                "no block answers " + RESONATOR);
        assertTrue(GooItems.ITEMS.getEntries().stream().map(DeferredHolder::getId).anyMatch(RESONATOR::equals),
                "no item answers " + RESONATOR);
        String dropped = JsonParser.parseString(resourceText("/data/goo/loot_table/blocks/resonator.json"))
                .getAsJsonObject().getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                .getAsJsonObject().get("name").getAsString();
        assertEquals(RESONATOR.toString(), dropped);
    }

    @Test
    void holdsNoBlockEntity() {
        assertFalse(EntityBlock.class.isAssignableFrom(ResonatorBlock.class), "ResonatorBlock is an EntityBlock");
    }

    @Test
    void carriesPulse() throws IOException {
        GooValue value = GooBaseValuesPreview.load(resourceText("/data/goo/goo_values/base_values.json")).baseValues()
                .get(RESONATOR);
        assertNotNull(value, "base_values.json values no " + RESONATOR);
        assertTrue(value.exact(GooTypes.PULSE).isPositive(), RESONATOR + " carries no pulse: " + value);
    }
}
