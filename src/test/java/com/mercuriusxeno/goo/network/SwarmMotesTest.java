package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decay's gnat motes fly from the glove into the block under the crosshair
 * while the swarm bites no mob (decision decay-gnats-degrade-each-block-once);
 * each flight is checked by whether the segment from the glove to where the
 * mote comes to rest meets the block's cell.
 */
class SwarmMotesTest {

    private static final Vec3 GLOVE = new Vec3(0.3, 1.2, 0.5);
    private static final int MOTES = 64;
    private static final long SEED = 7L;
    /** A hair past each mote's resting point, so a mote resting on the cell's face meets it. */
    private static final double PAST_THE_REST = 1.001;
    private static final BlockPos CROSSHAIR_STONE = new BlockPos(5, 1, 0);
    private static final BlockHitResult ON_THE_STONE =
            new BlockHitResult(new Vec3(5, 1.5, 0.5), Direction.WEST, CROSSHAIR_STONE, false);

    @Test
    void motesFlyIntoTheCrosshairBlockWhenNoMobIsBitten() {
        AABB cell = new AABB(CROSSHAIR_STONE);
        AABB target = SwarmMotes.moteTarget(ON_THE_STONE);
        for (Vec3 flight : SwarmMotes.moteFlights(GLOVE, target, MOTES, RandomSource.create(SEED))) {
            assertTrue(cell.clip(GLOVE, GLOVE.add(flight.scale(PAST_THE_REST))).isPresent(),
                    () -> "a mote resting at " + GLOVE.add(flight) + " misses " + cell);
        }
    }
}
