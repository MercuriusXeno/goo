package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.BoreDrillShape;
import net.minecraft.core.BlockPos;
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

    /** A survival player's block reach, and rock_bore.json's point radius. */
    private static final double REACH = 4.5;
    private static final double POINT_RADIUS = 0.1;
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

    /**
     * The spit comes from the drill's point, its far end, where the sight
     * meets the breakable block, though the drill is drawn from a blob off
     * the line of sight
     * (decision bore-drill-point-floats-along-the-crosshair-to-the-aimed-block).
     */
    @Test
    void theContactIsTheFarEndOfADrillDrawnFromABlobOffTheLineOfSight() {
        Vec3 eye = new Vec3(0.5, 65.5, 0.5);
        Vec3 look = new Vec3(0, 0, 1);
        Vec3 blob = eye.add(-0.4, -0.35, 0.6);
        BlockPos stone = new BlockPos(0, 65, 3);
        Vec3 point = BoreDrill.pointOf(new CameraEye.Sight(eye, look), REACH, BoreDrillShape.SHIPPED, true,
                cell -> !cell.equals(stone), stone::equals);
        ConeSections.Volume drawn = BoreDrill.volume(blob, point, 0f, POINT_RADIUS);
        Vec3 contact = BoreContactSpray.contactOf(drawn);
        assertEquals(0, contact.distanceTo(drawn.apex().add(drawn.axis().scale(drawn.range()))), SLACK);
        assertEquals(0, contact.distanceTo(new Vec3(0.5, 65.5, 3)), 1e-6);
    }

    /** Each particle starts on the cone's outside, round its axis at its radius there, and flies out from its place. */
    @Test
    void theSpewStartsRoundTheOutsideOfTheCone() {
        Vec3 contact = new Vec3(0.5, 65.0, 3.0);
        Vec3 towardHolder = new Vec3(0, 0, -1);
        double radius = 0.2;
        for (double around : new double[] {0, 0.25, 0.5, 0.75}) {
            Vec3 start = BoreContactSpray.spewOrigin(contact, towardHolder, around, radius);
            Vec3 offset = start.subtract(contact);
            Vec3 fromAxis = offset.subtract(towardHolder.scale(offset.dot(towardHolder)));
            assertEquals(radius, fromAxis.length(), 1e-6, "a particle at " + around + " starts off the cone's outside");
            Vec3 velocity = BoreContactSpray.spewVelocity(towardHolder, around, 1);
            assertTrue(velocity.dot(fromAxis) > 0, "a particle at " + around + " flies inward, not out from its place");
        }
    }

    @Test
    void itSpewsEightParticlesATick() {
        assertEquals(8, BoreContactSpray.SPITS_PER_TICK);
    }
}
