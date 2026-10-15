package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.frost.BurningLevel;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * A glacial prism pulses its frost ring as its combo starts and every few
 * seconds after, and not between; its freeze pass puts out the fire in its
 * radius.
 */
class GlacialStepTest {

    private static final Vec3 PRISM = new Vec3(0.5, 64.5, 0.5);
    private static final BlockPos FIRE_IN_RADIUS = new BlockPos(-3, 64, 0);
    private static final double RADIUS = 5;

    @Test
    void theRingPulsesAsTheComboStarts() {
        assertTrue(GlacialStep.pulsesOn(0));
    }

    @Test
    void theRingPulsesEveryFewSeconds() {
        assertTrue(GlacialStep.pulsesOn(GlacialStep.PULSE_EVERY_TICKS));
        assertTrue(GlacialStep.pulsesOn(2 * GlacialStep.PULSE_EVERY_TICKS));
    }

    @Test
    void theRingRestsBetweenPulses() {
        assertFalse(GlacialStep.pulsesOn(1));
        assertFalse(GlacialStep.pulsesOn(GlacialStep.PULSE_EVERY_TICKS - 1));
    }

    // frost-reach-extinguishes-fire-and-burning-mobs
    @Test
    void theFreezePassPutsOutTheFireInTheRadius() {
        try (BurningLevel world = new BurningLevel(Set.of(FIRE_IN_RADIUS), List.of());
             MockedStatic<GooServerState> state = mockStatic(GooServerState.class);
             MockedStatic<EntityVisuals> visuals = mockStatic(EntityVisuals.class)) {
            state.when(() -> GooServerState.of((MinecraftServer) any()))
                    .thenReturn(mock(GooServerState.class, RETURNS_DEEP_STUBS));

            FrostFireHosts.tickOnce(new GlacialStep(RADIUS), FrostFireHosts.frostingFrom(world.level(), PRISM));

            world.assertPutOut(FIRE_IN_RADIUS);
        }
    }
}
