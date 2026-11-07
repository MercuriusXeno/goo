package com.mercuriusxeno.goo.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.held.LaidState;
import com.mercuriusxeno.goo.ability.held.HeldEffects;
import com.mercuriusxeno.goo.client.fade.Afterglow;
import com.mercuriusxeno.goo.client.fade.Afterglows;
import com.mercuriusxeno.goo.type.GooTypes;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A prepaid brew's clock keeps drawing after its effect ends, fading over a second (decision brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends). */
class BrewClockHudTest {

    private static final float ENDED_AT = 1_000f;
    private static final long EXPIRES_AT = 1_000L;
    private static final int ALPHA_SHIFT = 24;
    private static final int OPAQUE_ALPHA = 0xFF;
    private static final HeldEffects.Held BREWED = new HeldEffects.Held(
            Identifier.fromNamespaceAndPath(Goo.MODID, "blaze_kindle"), GooTypes.BLAZE, 1,
            Set.of(LaidState.HEART_OVERLAY), 0L, EXPIRES_AT);
    private final Afterglows<Identifier, HeldEffects.Held> glows = new Afterglows<>();

    @BeforeEach
    void endTheBrewAtItsExpiry() {
        BrewClockHud.clocks(glows, List.of(BREWED), ENDED_AT - 0.05f);
        BrewClockHud.clocks(glows, List.of(), ENDED_AT);
    }

    @Test
    void halfASecondAfterTheEndTheClockDrawsAtHalfAlpha() {
        float halfway = ENDED_AT + Afterglow.FADE_TICKS / 2f;
        List<Afterglow.Glow<HeldEffects.Held>> clocks = BrewClockHud.clocks(glows, List.of(), halfway);
        assertEquals(List.of(BREWED), clocks.stream().map(Afterglow.Glow::state).toList());
        int color = BrewClockHud.clockColor(clocks.get(0), EXPIRES_AT + 10L, halfway);
        assertEquals(Math.round(0.5f * OPAQUE_ALPHA), color >>> ALPHA_SHIFT);
    }

    @Test
    void aSecondAfterTheEndTheClockIsGone() {
        assertTrue(BrewClockHud.clocks(glows, List.of(), ENDED_AT + Afterglow.FADE_TICKS).isEmpty());
    }

    @Test
    void aGloveEffectShowsNoClock() {
        HeldEffects.Held glove = new HeldEffects.Held(Identifier.fromNamespaceAndPath(Goo.MODID, "blaze_kindle"),
                GooTypes.BLAZE, 1, Set.of(), 0L);
        assertTrue(BrewClockHud.clocks(new Afterglows<>(), List.of(glove), ENDED_AT).isEmpty());
    }
}
