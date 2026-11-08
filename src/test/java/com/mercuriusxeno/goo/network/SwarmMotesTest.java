package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Decay's gnat motes fly from the glove into the mob its swarm bites, or into
 * the block under the crosshair where it bites none (decision
 * decay-gnats-degrade-each-block-once); each flight is checked by whether the
 * segment from the glove to where the mote comes to rest meets the target box.
 */
class SwarmMotesTest {

    private static final Vec3 GLOVE = new Vec3(0.3, 1.2, 0.5);
    private static final int MOTES = 64;
    private static final long SEED = 7L;
    /** A hair past each mote's resting point, so a mote resting on the box's face meets it. */
    private static final double PAST_THE_REST = 1.001;
    private static final BlockPos CROSSHAIR_STONE = new BlockPos(5, 1, 0);
    private static final BlockHitResult ON_THE_STONE =
            new BlockHitResult(new Vec3(5, 1.5, 0.5), Direction.WEST, CROSSHAIR_STONE, false);
    /** A zombie two blocks out, off the line to the crosshair stone. */
    private static final AABB ZOMBIE_BOX = new AABB(2.2, 1, 1.2, 2.8, 2.95, 1.8);

    @Test
    void motesFlyIntoTheBittenMob() {
        LivingEntity zombie = mock(LivingEntity.class);
        when(zombie.getBoundingBox()).thenReturn(ZOMBIE_BOX);

        assertEveryFlightMeets(ZOMBIE_BOX, SwarmMotes.moteTarget(List.of(zombie), ON_THE_STONE));
    }

    @Test
    void motesFlyIntoTheCrosshairBlockWhenNoMobIsBitten() {
        assertEveryFlightMeets(new AABB(CROSSHAIR_STONE), SwarmMotes.moteTarget(List.of(), ON_THE_STONE));
    }

    private static void assertEveryFlightMeets(AABB expected, AABB target) {
        for (Vec3 flight : SwarmMotes.moteFlights(GLOVE, target, MOTES, RandomSource.create(SEED))) {
            assertTrue(expected.clip(GLOVE, GLOVE.add(flight.scale(PAST_THE_REST))).isPresent(),
                    () -> "a mote resting at " + GLOVE.add(flight) + " misses " + expected);
        }
    }
}
