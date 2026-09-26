package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A due drip at 1:1 pours the stream and sends no particle; at 4:1 it sends
 * one drip particle and pours no stream.
 */
class TapDripReleaseTest {

    private static final double SURFACE_Y = 61.0;
    private static final Vec3 SPIGOT = new Vec3(0.5, 64.125, 0.5);
    private static final TapStream POUR = new TapStream(GooTypes.BLAZE, SURFACE_Y);

    private final List<ColorParticleOption> sent = new ArrayList<>();
    private final TapDrip.ParticleSink sink = (option, at, velocity) -> sent.add(option);
    private final ColorParticleOption particle = mock(ColorParticleOption.class);

    @Test
    void oneToOnePoursTheStreamAndSendsNoParticle() {
        TapStream stream = TapDrip.release(TapDripGrade.ONE_PER_TICK, POUR, sink, particle, SPIGOT);

        assertTrue(sent.isEmpty());
        assertEquals(GooTypes.BLAZE, stream.type());
        assertEquals(SURFACE_Y, stream.surfaceY());
    }

    @Test
    void fourToOneSendsOneDripParticlePerDripAndPoursNoStream() {
        int drips = 3;
        for (int i = 0; i < drips; i++) {
            assertNull(TapDrip.release(TapDripGrade.ONE_PER_4_TICKS, POUR, sink, particle, SPIGOT));
        }

        assertEquals(drips, sent.size());
        assertTrue(sent.stream().allMatch(option -> option == particle));
    }

    @Test
    void onlyTheOneToOneGradePours() {
        for (TapDripGrade grade : TapDripGrade.values()) {
            assertEquals(grade == TapDripGrade.ONE_PER_TICK, grade.pours(), grade.name());
        }
    }
}
