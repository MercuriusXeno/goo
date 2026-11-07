package com.mercuriusxeno.goo.ability.zone;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** The shifter's shimmer clears the tick its held effect ends, so its fade starts at the end (decision brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends). */
class ShifterEventsTest {

    private static final int PLAYER_ID = 7;
    private ServerPlayer player;

    @BeforeEach
    void standUpThePlayer() {
        player = mock(ServerPlayer.class);
        when(player.getId()).thenReturn(PLAYER_ID);
    }

    @Test
    void endingTheShifterSendsTheShimmerClear() {
        try (MockedStatic<EntityVisuals> visuals = mockStatic(EntityVisuals.class)) {
            ShifterEvents.clearShimmer(player);

            visuals.verify(() -> EntityVisuals.sendToWatchers(player,
                    new AilmentPayload(PLAYER_ID, AilmentKind.SHIFTER, 0)));
        }
    }

    @Test
    void aStandingTickRefreshesTheShimmerAndClearsNothing() {
        try (MockedStatic<EntityVisuals> visuals = mockStatic(EntityVisuals.class)) {
            ShifterEvents.keepShimmer(player, ShifterEvents.SHIMMER_REFRESH_TICKS);

            visuals.verify(() -> EntityVisuals.sendToWatchers(player,
                    new AilmentPayload(PLAYER_ID, AilmentKind.SHIFTER, ShifterEvents.SHIMMER_TICKS)));
            visuals.verify(() -> EntityVisuals.sendToWatchers(player,
                    new AilmentPayload(PLAYER_ID, AilmentKind.SHIFTER, 0)), never());
        }
    }

    @Test
    void aTickBetweenRefreshesSendsNothing() {
        try (MockedStatic<EntityVisuals> visuals = mockStatic(EntityVisuals.class)) {
            ShifterEvents.keepShimmer(player, ShifterEvents.SHIMMER_REFRESH_TICKS + 1L);

            visuals.verify(() -> EntityVisuals.sendToWatchers(any(), any()), never());
        }
    }
}
