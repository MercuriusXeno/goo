package com.mercuriusxeno.goo.ability.held;

import net.minecraft.world.effect.MobEffectInstance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A glove effect shows the ticks its goo pays for up to 999 hours, and past
 * that shows as endless (decision brew-runs-the-crawl-prepaid-on-a-shown-clock).
 */
class HeldEffectsEventsTest {

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
