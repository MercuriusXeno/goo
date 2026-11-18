package com.mercuriusxeno.goo.throwing;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A projectile's flight leaves at the speed it is launched at, covering that
 * speed in its first tick, and gravity pulls it lower each tick after, the
 * speed and the gravity both inputs (decision knives-and-javelins-drop-like-an-arrow).
 */
class ProjectileFlightTest {

    private static final double DELTA = 1e-9;
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final CoatRay.BlockClip OPEN_AIR = (from, to) -> Optional.empty();
    private static final DripArc.Airspace EVERYWHERE = point -> true;

    private static DripArc.Step firstTick(double speed, ProjectileFlight.Ballistics ballistics) {
        return ProjectileFlight.step(Vec3.ZERO, ProjectileFlight.launch(SOUTH, speed), ballistics, OPEN_AIR,
                DripArc.NO_LIVING, EVERYWHERE);
    }

    @Test
    void aLevelLaunchAtAKnifesSpeedCoversItInItsFirstTick() {
        DripArc.Step first = firstTick(1.5, ProjectileFlight.ARROW);
        assertEquals(1.5, first.position().z, DELTA);
        assertEquals(0, first.position().y, DELTA);
    }

    @Test
    void aLevelLaunchAtAJavelinsSpeedCoversItInItsFirstTick() {
        assertEquals(2.5, firstTick(2.5, ProjectileFlight.ARROW).position().length(), DELTA);
    }

    @Test
    void eachTickAfterTheFirstEndsLowerThanTheOneBefore() {
        DripArc.Step step = firstTick(1.5, ProjectileFlight.ARROW);
        for (int tick = 2; tick <= 8; tick++) {
            DripArc.Step next = ProjectileFlight.step(step.position(), step.velocity(), ProjectileFlight.ARROW,
                    OPEN_AIR, DripArc.NO_LIVING, EVERYWHERE);
            assertTrue(next.position().y < step.position().y, "tick " + tick + " should end lower than the last");
            step = next;
        }
    }

    @Test
    void theGravityGivenSetsTheDrop() {
        ProjectileFlight.Ballistics heavy = new ProjectileFlight.Ballistics(0.1, 1);
        DripArc.Step first = firstTick(1.5, heavy);
        DripArc.Step second = ProjectileFlight.step(first.position(), first.velocity(), heavy, OPEN_AIR,
                DripArc.NO_LIVING, EVERYWHERE);
        assertEquals(-0.1, second.position().y, DELTA);
    }
}
