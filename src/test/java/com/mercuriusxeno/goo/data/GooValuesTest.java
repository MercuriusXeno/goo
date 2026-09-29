package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * A reader reaches goo values by the level it stands in: a server level
 * answers its server's registry for as long as the server holds one, and a
 * client level answers its connection's values
 * (decision type-package-and-per-server-holders).
 */
class GooValuesTest {

    private static final Identifier STONE = Identifier.fromNamespaceAndPath("minecraft", "stone");
    private static final GooValue STONE_VALUE = new GooValue(Map.of(GooTypes.ROCK, 16));

    private final MinecraftServer server =
            mock(MinecraftServer.class, withSettings().extraInterfaces(GooServerValueHolder.class));
    private final Level serverLevel = mock(Level.class);

    GooValuesTest() {
        when(serverLevel.getServer()).thenReturn(server);
    }

    private GooServerValueHolder holder() {
        return (GooServerValueHolder) server;
    }

    @Test
    void serverLevelAnswersTheTableItsServerHolds() {
        GooValueRegistry registry = new GooValueRegistry();
        registry.seedBaseValues(Map.of(STONE, STONE_VALUE));
        when(holder().gooValueRegistry()).thenReturn(registry);

        assertSame(registry.table(), GooValues.of(serverLevel));
        assertEquals(STONE_VALUE, GooValues.of(serverLevel).lookup(STONE));
        assertSame(registry, GooValues.registryOf(server));
    }

    @Test
    void stoppedServerAnswersNoValues() {
        when(holder().gooValueRegistry()).thenReturn(null);

        assertSame(GooValueTable.EMPTY, GooValues.of(serverLevel));
        assertNull(GooValues.of(serverLevel).lookup(STONE));
        assertThrows(IllegalStateException.class, () -> GooValues.registryOf(server));
    }

    @Test
    void detachDropsTheServersRegistry() {
        GooValues.detach(server);

        verify(holder()).holdGooValueRegistry(null);
    }

    @Test
    void clientLevelAnswersItsConnectionsValues() {
        Level clientLevel = mock(Level.class, withSettings().extraInterfaces(GooValueSource.class));
        GooValueTable synced = GooValueTable.ofEffectiveValues(Map.of(STONE, STONE_VALUE));
        when(((GooValueSource) clientLevel).gooValueLookup()).thenReturn(synced);

        assertSame(synced, GooValues.of(clientLevel));
    }

    @Test
    void levelWithNoServerAndNoConnectionAnswersNoValues() {
        assertSame(GooValueTable.EMPTY, GooValues.of(mock(Level.class)));
    }

    @Test
    void tableKeepsWhatItWasBuiltFrom() {
        GooValueTable table = new GooValueTable(Map.of(STONE, STONE_VALUE), Set.of(STONE), Set.of(STONE), Set.of(STONE));

        assertEquals(1, table.size());
        assertTrue(table.hasBaseValue(STONE));
        assertTrue(table.isDenied(STONE));
        assertTrue(table.isRestricted(STONE));
        assertEquals(Map.of(STONE, STONE_VALUE), table.getEffectiveValues());
    }
}
