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

    @ParameterizedTest
    @EnumSource(Direction.class)
    void aSpitLeavesOutOfTheFaceItMeets(Direction face) {
        for (double spread : new double[] {-1, 0, 1}) {
            Vec3 velocity = BoreContactSpray.spitVelocity(face, spread, -spread, spread);
            assertTrue(velocity.dot(Vec3.atLowerCornerOf(face.getUnitVec3i())) > 0,
                    "a spit off " + face + " runs " + velocity);
        }
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
    void itSpitsOneParticleATick() {
        assertEquals(1, BoreContactSpray.SPITS_PER_TICK);
    }

    @Test
    void theSpitSpreadsAcrossTheVortexsWidthAtTheContactsDepth() {
        assertEquals(NEAR, BoreContactSpray.widthAt(0, REACH, NEAR, FAR), SLACK);
        assertEquals(FAR, BoreContactSpray.widthAt(REACH, REACH, NEAR, FAR), SLACK);
        assertEquals((NEAR + FAR) / 2, BoreContactSpray.widthAt(REACH / 2, REACH, NEAR, FAR), SLACK);
    }
}
