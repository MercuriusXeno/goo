package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where Bore's vortex makes contact with a breakable block, particles of the
 * block spit out of the face it meets, across the vortex's width there
 * (decision bore-shows-a-big-drill-from-the-glove).
 */
class BoreContactSprayTest {

    /** rock_bore.json's reach and vortex radii. */
    private static final double REACH = 4;
    private static final double NEAR = 0.35;
    private static final double FAR = 0.1;
    private static final double SLACK = 1e-9;

    /** The spew flies back from the drill's point toward the holder, never back into the block. */
    @ParameterizedTest
    @EnumSource(Direction.class)
    void theSpewFliesBackTowardTheHolder(Direction backward) {
        Vec3 towardHolder = Vec3.atLowerCornerOf(backward.getUnitVec3i());
        for (double around : new double[] {0, 0.25, 0.5, 0.75}) {
            Vec3 velocity = BoreContactSpray.spewVelocity(towardHolder, around, 1);
            assertTrue(velocity.dot(towardHolder) > 0, "a spew toward " + backward + " runs " + velocity);
        }
    }

    /** The spew fans out in a ring round the drill's axis: opposite points of the ring fly opposite ways. */
    @Test
    void theSpewFansOutInARingRoundTheAxis() {
        Vec3 towardHolder = new Vec3(0, 0, -1);
        Vec3 one = BoreContactSpray.spewVelocity(towardHolder, 0, 1);
        Vec3 opposite = BoreContactSpray.spewVelocity(towardHolder, 0.5, 1);
        Vec3 sideOne = one.subtract(towardHolder.scale(one.dot(towardHolder)));
        Vec3 sideOpposite = opposite.subtract(towardHolder.scale(opposite.dot(towardHolder)));
        assertTrue(sideOne.length() > 0, "the spew flies straight back with no ring");
        assertTrue(sideOne.dot(sideOpposite) < 0, "opposite points of the ring fly the same way");
    }

    /** The spit comes from where the drawn vortex meets the block, so it traces the drawn vortex's own axis. */
    @Test
    void theContactIsTracedAlongTheDrawnVortexNotTheLineOfSight() {
        Vec3 eye = new Vec3(0.5, 65.62, 0.5);
        Vec3 look = new Vec3(0, 0, 1);
        ConeSections.Volume drawn = BoreDrill.volume(eye, look, 0f, REACH, NEAR, FAR);
        Vec3[] line = BoreContactSpray.contactLine(eye, look, 0f, REACH, NEAR, FAR);
        assertEquals(0, line[0].distanceTo(drawn.apex()), SLACK);
        assertEquals(0, line[1].distanceTo(drawn.apex().add(drawn.axis().scale(drawn.range()))), SLACK);
        assertTrue(line[0].y < eye.y, "the trace starts on the line of sight, not where the vortex is held");
    }

    @Test
    void itSpewsEightParticlesATick() {
        assertEquals(8, BoreContactSpray.SPITS_PER_TICK);
    }
}
