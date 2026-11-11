package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * A glove effect shows the ticks its goo pays for up to 999 hours, and past
 * that shows as endless (decision brew-runs-the-crawl-prepaid-on-a-shown-clock);
 * and an ended effect clears the player state its step laid.
 */
class HeldEffectsEventsTest {

    // decision eldritch-sight-reveals-the-out-of-phase
    @Test
    void endingEldritchSightEndsTheEldritchState() {
        ServerPlayer player = mock(ServerPlayer.class);

        HeldEffectsEvents.clearSensesAndReach(player, Set.of(LaidState.ELDRITCH));

        verify(player).removeData(GooAttachments.ELDRITCH);
    }

    @Test
    void upTo999HoursTheTimeShowsAsItIs() {
        assertEquals(1200, HeldEffectsEvents.shownDuration(1200));
        assertEquals(HeldEffectsEvents.LONGEST_SHOWN_TICKS,
                HeldEffectsEvents.shownDuration(HeldEffectsEvents.LONGEST_SHOWN_TICKS));
    }

    @Test
    void pastThatItShowsAsEndless() {
        assertEquals(MobEffectInstance.INFINITE_DURATION,
                HeldEffectsEvents.shownDuration(HeldEffectsEvents.LONGEST_SHOWN_TICKS + 1));
    }
}
