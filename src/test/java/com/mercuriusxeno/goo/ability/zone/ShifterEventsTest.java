package com.mercuriusxeno.goo.ability.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * The shifter's shimmer refreshes every twenty ticks for forty while its held
 * effect stands, and clears the tick it ends, so its fade starts at the end
 * (decision brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends).
 */
class ShifterEventsTest {

    private static final int PLAYER_ID = 7;
    private static final int REFRESH_TICKS = 20;
    private static final int SHIMMER_TICKS = 40;
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
    void aRefreshTickSendsTheShimmerForItsFortyTicks() {
        try (MockedStatic<EntityVisuals> visuals = mockStatic(EntityVisuals.class)) {
            ShifterEvents.keepShimmer(player, ShifterEvents.SHIMMER_REFRESH_TICKS);

            visuals.verify(() -> EntityVisuals.sendToWatchers(player,
                    new AilmentPayload(PLAYER_ID, AilmentKind.SHIFTER, SHIMMER_TICKS)));
        }
    }

    @Test
    void theShimmerRefreshesEveryTwentyTicks() {
        assertEquals(REFRESH_TICKS, ShifterEvents.SHIMMER_REFRESH_TICKS);
    }
}
