package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.program.DripsStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.TickBlockStep;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static com.mercuriusxeno.goo.client.overlay.TickSplashes.SPLASH_TICKS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Tick tap drip's splash is whole as it lands and fades out over
 * SPLASH_TICKS, and the splash reads its march from the tick_block under
 * the tap's drip count (decision tick-drip-splashes-a-small-tick-effect).
 */
class TickSplashesTest {

    private static final long LAND = 500;
    private static final Vec3 AT = new Vec3(0.5, 65, 0.5);
    private static final int EXTRA_TICKS = 40;
    private static final float EPSILON = 1e-6f;

    @Test
    void aSplashFadesFromWholeToNothing() {
        TickSplashes splashes = new TickSplashes();
        splashes.splash(AT, EXTRA_TICKS, LAND);
        TickSplashes.Splash splash = splashes.live(LAND).get(0);

        assertEquals(1f, splash.strength(LAND), EPSILON);
        assertEquals(0.5f, splash.strength(LAND + SPLASH_TICKS / 2f), EPSILON);
        assertTrue(splashes.live(LAND + SPLASH_TICKS).isEmpty());
    }

    @Test
    void theTapsTickIsFoundUnderItsDripCount() {
        TickBlockStep tick = new TickBlockStep(EXTRA_TICKS);
        List<Step> tap = List.of(new DripsStep(4, List.of(tick)));

        assertEquals(Optional.of(tick), TickFaceOverlay.tickStepOf(tap));
    }
}
