package com.mercuriusxeno.goo.ability.oculus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Each level keeps its own list of oculi, which Blink reads at any distance
 * (decision oculus-prism-becomes-a-hovering-eye).
 */
class OculusRegistryTest {

    private static final BlockPos FAR = new BlockPos(900, 64, 0);
    private static final Vec3 EYE = new Vec3(0.5, 65.5, 0.5);
    private static final double REACH = 1024;
    private static final double SHORT_REACH = 48;

    @Test
    void anOculusListedFarAwayIsInReachOfTheLongLock() {
        Level level = mock(Level.class);
        OculusRegistry.add(level, FAR);
        assertEquals(List.of(FAR), OculusNodes.oculiNear(level, EYE, REACH));
        assertTrue(OculusNodes.oculiNear(level, EYE, SHORT_REACH).isEmpty());
    }

    @Test
    void anUnlistedOculusIsGone() {
        Level level = mock(Level.class);
        OculusRegistry.add(level, FAR);
        OculusRegistry.remove(level, FAR);
        assertEquals(Set.of(), OculusRegistry.in(level));
    }

    @Test
    void eachLevelKeepsItsOwnList() {
        Level one = mock(Level.class);
        Level other = mock(Level.class);
        OculusRegistry.add(one, FAR);
        assertTrue(OculusRegistry.in(other).isEmpty());
    }
}
