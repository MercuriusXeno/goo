package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.BoreDrillShape;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where Bore's drill makes contact with a breakable block, particles of the
 * block spit out across the drill's cross section at the crosshair's plane,
 * the ellipse the break picks with, flying back toward the holder and out
 * from their place on its rim
 * (decisions bore-shows-a-big-drill-from-the-glove, bore-drill-dimensions-drive-the-visuals-and-the-break).
 */
class BoreContactSprayTest {

    private static final double SLACK = 1e-9;
    private static final Vec3 LOOK_SOUTH = new Vec3(0, 0, 1);

    private static double widest(List<Vec3> spread) {
        return spread.stream().mapToDouble(Vec3::length).max().orElseThrow();
    }

    private static double narrowest(List<Vec3> spread) {
        return spread.stream().mapToDouble(Vec3::length).min().orElseThrow();
    }

    /** The spew flies back from the drill's contact toward the holder, never back into the block. */
    @ParameterizedTest
    @EnumSource(Direction.class)
    void theSpewFliesBackTowardTheHolder(Direction backward) {
        Vec3 towardHolder = Vec3.atLowerCornerOf(backward.getUnitVec3i());
        Vec3 square = Math.abs(towardHolder.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        for (Vec3 outward : List.of(square, square.reverse(), towardHolder.cross(square))) {
            Vec3 velocity = BoreContactSpray.spewVelocity(towardHolder, outward, 1);
            assertTrue(velocity.dot(towardHolder) > 0, "a spew toward " + backward + " runs " + velocity);
        }
    }

    /** The spew fans out round the axis: particles from opposite sides of the rim fly opposite ways. */
    @Test
    void theSpewFansOutRoundTheAxis() {
        Vec3 towardHolder = new Vec3(0, 0, -1);
        Vec3 one = BoreContactSpray.spewVelocity(towardHolder, new Vec3(1, 0, 0), 1);
        Vec3 opposite = BoreContactSpray.spewVelocity(towardHolder, new Vec3(-1, 0, 0), 1);
        Vec3 sideOne = one.subtract(towardHolder.scale(one.dot(towardHolder)));
        Vec3 sideOpposite = opposite.subtract(towardHolder.scale(opposite.dot(towardHolder)));
        assertTrue(sideOne.length() > 0, "the spew flies straight back with no fan");
        assertTrue(sideOne.dot(sideOpposite) < 0, "opposite sides of the rim fly the same way");
    }

    /** decision bore-drill-dimensions-drive-the-visuals-and-the-break */
    @Test
    void theSpreadIsTheCrossSectionTheBreakPicksWith() {
        BoreDrillShape shipped = BoreDrillShape.SHIPPED;
        List<Vec3> spread = BoreContactSpray.spreadOf(shipped, LOOK_SOUTH, true);
        assertEquals(shipped.longHalf(), widest(spread), SLACK);
        assertEquals(shipped.shortHalf(), narrowest(spread), SLACK);
        for (Vec3 offset : spread) {
            assertEquals(0, offset.dot(LOOK_SOUTH), SLACK);
        }
    }

    @Test
    void aShorterPastLengthNarrowsTheSpread() {
        List<Vec3> shipped = BoreContactSpray.spreadOf(BoreDrillShape.SHIPPED, LOOK_SOUTH, true);
        BoreDrillShape shorter = new BoreDrillShape(0.7, 2, 15, 0.5);
        List<Vec3> narrower = BoreContactSpray.spreadOf(shorter, LOOK_SOUTH, true);
        assertEquals(shorter.longHalf(), widest(narrower), SLACK);
        assertTrue(widest(narrower) < widest(shipped), "the spread reaches " + widest(narrower));
    }

    /** Each particle starts on the ellipse's rim, a little back toward the holder, and flies out from its place. */
    @Test
    void theSpewStartsOnTheRimAndFliesOutFromIt() {
        Vec3 center = new Vec3(0.5, 65.0, 3.0);
        Vec3 towardHolder = new Vec3(0, 0, -1);
        for (Vec3 offset : BoreContactSpray.spreadOf(BoreDrillShape.SHIPPED, towardHolder.reverse(), true)) {
            Vec3 start = BoreContactSpray.spewOrigin(center, towardHolder, offset);
            Vec3 fromAxis = start.subtract(center).subtract(towardHolder.scale(start.subtract(center).dot(towardHolder)));
            assertEquals(0, fromAxis.distanceTo(offset), 1e-6);
            Vec3 velocity = BoreContactSpray.spewVelocity(towardHolder, offset, 1);
            assertTrue(velocity.dot(fromAxis) > 0, "a particle at " + offset + " flies inward, not out from its place");
        }
    }

    @Test
    void itSpewsEightParticlesATick() {
        assertEquals(8, BoreContactSpray.SPITS_PER_TICK);
    }
}
