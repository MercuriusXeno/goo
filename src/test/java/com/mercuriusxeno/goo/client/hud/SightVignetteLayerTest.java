package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.ability.program.Sight;
import com.mercuriusxeno.goo.client.fade.Afterglow;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sight's vignette rises over its first second, stands full through the last
 * tick the sight stands, and falls over the second after the sight ends,
 * however it ends (decisions sight-lengthens-shift-and-outlines-fungus,
 * brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends).
 */
class SightVignetteLayerTest {

    private static final float EPSILON = 1e-6f;
    private static final long EXPIRES_AT = 1_000L;
    private static final long PRESSED_AT = 300L;
    private static final float FACTOR = 0.5f;
    private static final Sight BREWED = new Sight(FACTOR, EXPIRES_AT);
    private static final Sight HELD = new Sight(FACTOR, Sight.NEVER_EXPIRES);
    private static final float HALF_FADE = Afterglow.FADE_TICKS / 2f;

    private static float read(SightVignetteLayer.VignetteFade fade, Sight sight, long tick) {
        return fade.read(sight, tick, tick);
    }

    @Test
    void itRisesOverItsFirstSecond() {
        SightVignetteLayer.VignetteFade fade = new SightVignetteLayer.VignetteFade();
        assertEquals(0f, read(fade, BREWED, 0L), EPSILON);
        assertEquals(0.5f, fade.read(BREWED, 10L, SightVignetteLayer.FADE_IN_TICKS / 2), EPSILON);
    }

    @Test
    void aBrewedSightStandsFullThroughItsLastTickThenFallsOverTheSecondAfter() {
        SightVignetteLayer.VignetteFade fade = new SightVignetteLayer.VignetteFade();
        read(fade, BREWED, 0L);
        assertEquals(1f, read(fade, BREWED, EXPIRES_AT - 1), EPSILON);
        read(fade, BREWED, EXPIRES_AT);
        assertEquals(0.5f, fade.read(BREWED, EXPIRES_AT + 10, EXPIRES_AT + HALF_FADE), EPSILON);
        assertEquals(0f, read(fade, Sight.NONE, EXPIRES_AT + Afterglow.FADE_TICKS), EPSILON);
    }

    @Test
    void aSightEndedByTheGloveFallsOverTheSameSecondFromThatTick() {
        SightVignetteLayer.VignetteFade fade = new SightVignetteLayer.VignetteFade();
        read(fade, HELD, 0L);
        assertEquals(1f, read(fade, HELD, PRESSED_AT - 1), EPSILON);
        read(fade, Sight.NONE, PRESSED_AT);
        assertEquals(0.5f, fade.read(Sight.NONE, PRESSED_AT + 10, PRESSED_AT + HALF_FADE), EPSILON);
        assertEquals(0f, read(fade, Sight.NONE, PRESSED_AT + Afterglow.FADE_TICKS), EPSILON);
    }

    @Test
    void aSightStartingAgainMidFadeRisesFromWhereTheFadeStood() {
        SightVignetteLayer.VignetteFade fade = new SightVignetteLayer.VignetteFade();
        read(fade, HELD, 0L);
        read(fade, HELD, PRESSED_AT - 1);
        read(fade, Sight.NONE, PRESSED_AT);
        fade.read(Sight.NONE, PRESSED_AT + 10, PRESSED_AT + HALF_FADE);
        assertEquals(0.5f, fade.read(HELD, PRESSED_AT + 10, PRESSED_AT + HALF_FADE), EPSILON);
    }
}
