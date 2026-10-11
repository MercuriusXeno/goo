package com.mercuriusxeno.goo.ability.aging;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the shipped aging table decodes, read from the classpath, and
 * that an aging throw costs its row's price and is refused on a block no
 * row names (decision old-blob-ages-valuables-slowly).
 */
class AgingTableTest {

    private static final String AGING_DIR = "data/goo/goo_aging";
    private static final Identifier COAL_BLOCK = Identifier.parse("minecraft:coal_block");
    private static final Identifier DIAMOND_BLOCK = Identifier.parse("minecraft:diamond_block");
    private static final Identifier DIRT = Identifier.parse("minecraft:dirt");

    @Test
    void shippedRowsDecodeAndCoalAgesIntoDiamond() {
        Map<Identifier, AgingEntry> table = AgingTable.bySource(shippedRows());
        AgingEntry coal = table.get(COAL_BLOCK);
        assertNotNull(coal);
        assertEquals(DIAMOND_BLOCK, coal.result());
        assertEquals(OptionalInt.of(coal.price()), AgingTable.priceIn(table, COAL_BLOCK));
    }

    @Test
    void unlistedBlockIsRefused() {
        Map<Identifier, AgingEntry> table = AgingTable.bySource(shippedRows());
        assertFalse(table.isEmpty());
        assertTrue(AgingTable.priceIn(table, DIRT).isEmpty());
    }

    private static List<AgingEntry> shippedRows() {
        URL dir = AgingTableTest.class.getClassLoader().getResource(AGING_DIR);
        assertNotNull(dir, AGING_DIR);
        try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
            return files.map(AgingTableTest::decode).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AgingEntry decode(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            return AgingEntry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
