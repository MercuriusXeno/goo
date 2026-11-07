package com.mercuriusxeno.goo.ability.held;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import com.mercuriusxeno.goo.network.EntityVisuals;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Telekinesis lengthens the reach and lays no overlay on the player, since Kinetic is electric blue and white and no decision asked for one (decision kinetic-is-electric-blue-and-white). */
class TelekinesisTest {

    private static final double REACH = 8;

    @Test
    void layingTheReachSendsNoOverlay() {
        ServerPlayer player = mock(ServerPlayer.class);
        try (MockedStatic<EntityVisuals> visuals = mockStatic(EntityVisuals.class)) {
            Telekinesis.lay(player, REACH);

            visuals.verify(() -> EntityVisuals.sendToWatchers(any(), any()), never());
        }
    }

    @Test
    void clearingTheReachSendsNoOverlay() {
        ServerPlayer player = mock(ServerPlayer.class);
        try (MockedStatic<EntityVisuals> visuals = mockStatic(EntityVisuals.class)) {
            Telekinesis.clear(player);

            visuals.verify(() -> EntityVisuals.sendToWatchers(any(), any()), never());
        }
    }
}
