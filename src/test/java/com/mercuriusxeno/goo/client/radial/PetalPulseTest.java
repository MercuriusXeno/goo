package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.fade.Afterglow;
import com.mercuriusxeno.goo.client.fade.Afterglows;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** An active petal pulses between the resting and hovered opacity on game time, and an inactive one rests (decision wheel-pulses-the-active-effect). */
class PetalPulseTest {

    private static final float QUARTER_PERIOD = PetalPulse.PERIOD_TICKS / 4f;
    private static final int ACTIVE_SWING = RadialWheelRenderer.HOVER_ALPHA - RadialWheelRenderer.NORMAL_ALPHA;

    @Test
    void anActivePetalSwingsFromRestingToHovered() {
        assertEquals(RadialWheelRenderer.NORMAL_ALPHA, PetalPulse.alpha(1f, 0f));
        assertEquals(RadialWheelRenderer.HOVER_ALPHA, PetalPulse.alpha(1f, QUARTER_PERIOD));
    }

    @Test
    void anInactivePetalRests() {
        assertEquals(RadialWheelRenderer.NORMAL_ALPHA, PetalPulse.alpha(0f, 0f));
        assertEquals(RadialWheelRenderer.NORMAL_ALPHA, PetalPulse.alpha(0f, QUARTER_PERIOD));
    }

    /** A petal whose effect ended settles to rest over the second after (decision brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends). */
    @Nested
    class AfterTheEffectEnds {

        private static final Identifier KINDLE = Identifier.fromNamespaceAndPath(Goo.MODID, "blaze_kindle");
        private static final float ENDED_AT = 400f;
        private final Afterglows<Identifier, Identifier> glows = new Afterglows<>();

        @BeforeEach
        void endKindleNow() {
            PetalPulse.pulses(glows, Set.of(KINDLE), ENDED_AT - 0.05f);
            PetalPulse.pulses(glows, Set.of(), ENDED_AT);
        }

        @Test
        void halfASecondAfterThePetalSwingsAtHalfTheActiveSwing() {
            Map<Identifier, Float> pulses = PetalPulse.pulses(glows, Set.of(), ENDED_AT + Afterglow.FADE_TICKS / 2f);
            assertEquals(RadialWheelRenderer.NORMAL_ALPHA + Math.round(ACTIVE_SWING / 2f),
                    PetalPulse.alpha(pulses.getOrDefault(KINDLE, 0f), QUARTER_PERIOD));
        }

        @Test
        void aSecondAfterThePetalRests() {
            Map<Identifier, Float> pulses = PetalPulse.pulses(glows, Set.of(), ENDED_AT + Afterglow.FADE_TICKS);
            assertEquals(RadialWheelRenderer.NORMAL_ALPHA,
                    PetalPulse.alpha(pulses.getOrDefault(KINDLE, 0f), QUARTER_PERIOD));
        }
    }
}
