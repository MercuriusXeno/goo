package com.mercuriusxeno.goo.registry;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.lunar.WirelessEmitterBlock;
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
 * The wireless emitter registers as a block with no block entity and an item that drops itself when mined.
 * decision wireless-emitter-beams-its-signal-to-the-closest-receiver
 */
class WirelessEmitterTest {

    private static final Identifier EMITTER = Identifier.fromNamespaceAndPath("goo", "wireless_emitter");

    private static String resourceText(String path) throws IOException {
        try (InputStream stream = WirelessEmitterTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void registersAsABlockAndAnItemThatDropsItself() throws IOException {
        assertTrue(GooBlocks.BLOCKS.getEntries().stream().map(DeferredHolder::getId).anyMatch(EMITTER::equals),
                "no block answers " + EMITTER);
        assertTrue(GooItems.ITEMS.getEntries().stream().map(DeferredHolder::getId).anyMatch(EMITTER::equals),
                "no item answers " + EMITTER);
        String dropped = JsonParser.parseString(resourceText("/data/goo/loot_table/blocks/wireless_emitter.json"))
                .getAsJsonObject().getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                .getAsJsonObject().get("name").getAsString();
        assertEquals(EMITTER.toString(), dropped);
    }

    @Test
    void holdsNoBlockEntity() {
        assertFalse(EntityBlock.class.isAssignableFrom(WirelessEmitterBlock.class),
                "WirelessEmitterBlock is an EntityBlock");
    }
}
