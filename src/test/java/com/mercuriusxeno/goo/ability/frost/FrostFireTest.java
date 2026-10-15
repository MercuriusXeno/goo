package com.mercuriusxeno.goo.ability.frost;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Frost leeching a sphere puts out the fire blocks and burning entities
 * within its radius with the extinguish hiss and smoke, and leaves the fire
 * beyond it burning (decision frost-reach-extinguishes-fire-and-burning-mobs).
 */
class FrostFireTest {

    private static final Vec3 CENTER = new Vec3(0.5, 64.5, 0.5);
    private static final double RADIUS = 3;
    private static final BlockPos INSIDE = new BlockPos(2, 64, 0);
    private static final BlockPos OUTSIDE = new BlockPos(4, 64, 0);
    private static final double WITHIN = 4;
    private static final double BEYOND = 16;

    @Test
    void fireWithinTheRadiusGoesOutWithAHissAndSmokeAndFireBeyondItBurns() {
        try (BurningLevel world = new BurningLevel(Set.of(INSIDE, OUTSIDE), List.of())) {
            FrostFire.putOutWithin(world.level(), CENTER, RADIUS);

            world.assertPutOut(INSIDE);
            world.assertStillBurning(OUTSIDE);
        }
    }

    @Test
    void aBurningEntityWithinTheRadiusStopsBurningAndOneBeyondItBurns() {
        Entity near = BurningLevel.burningAt(WITHIN);
        Entity far = BurningLevel.burningAt(BEYOND);
        try (BurningLevel world = new BurningLevel(Set.of(), List.of(near, far))) {
            FrostFire.putOutWithin(world.level(), CENTER, RADIUS);

            verify(near).clearFire();
            verify(far, never()).clearFire();
        }
    }
}
