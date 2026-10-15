package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.frost.BurningLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.mockito.Mockito.verify;

/**
 * Orb's freeze_blocks puts out the fire blocks and burning mobs in its sphere
 * as it freezes the water and lava there
 * (decision frost-reach-extinguishes-fire-and-burning-mobs). The freezes
 * themselves reach Blocks, which the unit suite cannot build.
 */
class FreezeBlocksStepTest {

    private static final Vec3 CENTER = new Vec3(0.5, 64.5, 0.5);
    private static final BlockPos FIRE_IN_SPHERE = new BlockPos(0, 65, 1);
    private static final double RADIUS = 2;

    @Test
    void theFreezePutsOutTheFireAndTheBurningMobsInItsSphere() {
        Entity burning = BurningLevel.burningAt(1);
        try (BurningLevel world = new BurningLevel(Set.of(FIRE_IN_SPHERE), List.of(burning))) {
            FrostFireHosts.tickOnce(new FreezeBlocksStep(Expr.literal(RADIUS), false),
                    FrostFireHosts.frostingFrom(world.level(), CENTER));

            world.assertPutOut(FIRE_IN_SPHERE);
            verify(burning).clearFire();
        }
    }
}
