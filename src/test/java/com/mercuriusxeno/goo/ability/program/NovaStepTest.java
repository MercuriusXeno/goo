package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.frost.BurningLevel;
import com.mercuriusxeno.goo.ability.frost.FrostCurve;
import com.mercuriusxeno.goo.network.EntityVisuals;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * Nova's crowd rule: each mob in the ring keeps nearly all of the freeze
 * however many stand in it; and the ring puts out the fire blocks and the
 * burning mobs within its reach.
 */
class NovaStepTest {

    private static final float CROWD = 0.05f;
    private static final float EPSILON = 1e-6f;
    private static final Vec3 CENTER = new Vec3(0.5, 64.5, 0.5);
    private static final BlockPos FIRE_IN_REACH = new BlockPos(2, 64, 0);
    private static final double REACH = 3;

    @Test
    void aLoneMobTakesTheWholeFreeze() {
        assertEquals(1f, NovaStep.crowdShare(CROWD, 1));
    }

    @Test
    void eachFurtherMobThinsTheFreezeOnlySlightly() {
        assertEquals(1f / 1.05f, NovaStep.crowdShare(CROWD, 2), EPSILON);
        assertTrue(NovaStep.crowdShare(CROWD, 5) > 0.8f);
    }

    @Test
    void anEmptyRingThinsNothing() {
        assertEquals(1f, NovaStep.crowdShare(CROWD, 0));
    }

    // frost-reach-extinguishes-fire-and-burning-mobs
    @Test
    void theRingPutsOutTheFireAndTheBurningMobsInItsReach() {
        Entity burning = BurningLevel.burningAt(1);
        try (BurningLevel world = new BurningLevel(Set.of(FIRE_IN_REACH), List.of(burning));
             MockedStatic<EntityVisuals> visuals = mockStatic(EntityVisuals.class)) {
            NovaStep nova = new NovaStep(Expr.literal(REACH), Expr.literal(1), CROWD, 0f, FrostCurve.NONE);

            FrostFireHosts.tickOnce(nova, FrostFireHosts.frostingFrom(world.level(), CENTER));

            world.assertPutOut(FIRE_IN_REACH);
            verify(burning).clearFire();
        }
    }
}
