package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * A plexer pick acts only on a plexer within the player's block reach; one out of reach, or a
 * position holding no plexer, changes nothing (decision right-click-on-the-plexer-opens-the-known-items-menu).
 */
class PlexerTargetPickHandlerTest {

    @Test
    void plexerWithinReachTakesThePick() {
        PlexerBlockEntity plexer = mock(PlexerBlockEntity.class);
        assertSame(plexer, PlexerTargetPickHandler.reachedPlexer(true, plexer));
    }

    @Test
    void plexerOutOfReachChangesNothing() {
        assertNull(PlexerTargetPickHandler.reachedPlexer(false, mock(PlexerBlockEntity.class)));
    }

    @Test
    void positionHoldingNoPlexerChangesNothing() {
        assertNull(PlexerTargetPickHandler.reachedPlexer(true, mock(BlockEntity.class)));
        assertNull(PlexerTargetPickHandler.reachedPlexer(true, null));
    }
}
