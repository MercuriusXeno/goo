package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.Sight;
import com.mercuriusxeno.goo.ability.program.SightStep;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.junit.jupiter.api.Test;
import java.util.List;
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

    // decision sight-lengthens-shift-and-outlines-fungus
    @Test
    void endingSightClearsTheSight() {
        ServerPlayer player = mock(ServerPlayer.class);

        HeldEffectsEvents.clearMindStates(player, Set.of(LaidState.SIGHT));

        verify(player).setData(GooAttachments.SIGHT, Sight.NONE);
    }

    @Test
    void aSightStepLaysTheSight() {
        assertEquals(Set.of(LaidState.SIGHT), LaidState.laidBy(List.of(new SightStep(Expr.literal(2)))));
    }

    @Test
    void noSightLengthensAShiftByNothing() {
        assertEquals(1f, Sight.NONE.factorAt(0L));
    }
}
