package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.DripFall;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A drip lands on the first block with a collision shape below the tap,
 * after the ticks the drip particle takes to fall there, and a bottomless
 * drop answers no landing.
 */
class TapDripFallTest {

    private static final BlockPos TAP_POS = new BlockPos(0, 10, 0);
    private static final int MIN_Y = -64;
    private static final double HALF = 0.5;

    /** How far below the spigot the client's hanging drop starts: a 2-pixel drop and its 0.02 surface margin. */
    private static final double CLIENT_HANGING_DROP = 2.0 / 16.0 + 0.02;

    /**
     * Ticks the client drip particle takes to pass a distance, ticked the way
     * TrailDripParticle ticks: gravity, move, drag.
     */
    private static int particleTicksToPass(double distance, double leaveSpeed) {
        double y = 0;
        double yd = -leaveSpeed;
        int ticks = 0;
        while (y > -distance) {
            yd -= DripFall.GRAVITY;
            y += yd;
            yd *= DripFall.DRAG;
            ticks++;
        }
        return ticks;
    }

    /**
     * Ticks the client tap-drip takes from leaving the spigot to meeting a
     * surface a distance below it, modeled on the particle the tree spawns:
     * where the 2-pixel drop and its 0.02 margin fit under the spigot, its
     * collision box starts that far down, holds still for the hang ticks,
     * then falls in particle order until a move meets the surface; where they
     * do not fit, the drop splats as its hang ends.
     */
    private static int particleTicksToArrive(double spigotToSurface, double leaveSpeed) {
        if (spigotToSurface < CLIENT_HANGING_DROP) {
            return DripFall.HANG_TICKS;
        }
        double y = -CLIENT_HANGING_DROP;
        double yd = -leaveSpeed;
        int ticks = DripFall.HANG_TICKS;
        do {
            yd -= DripFall.GRAVITY;
            y = Math.max(-spigotToSurface, y + yd);
            yd *= DripFall.DRAG;
            ticks++;
        } while (y > -spigotToSurface);
        return ticks;
    }

    private static Function<BlockPos, VoxelShape> solidAt(int y, VoxelShape shape) {
        return pos -> pos.getY() == y ? shape : Shapes.empty();
    }

    @Test
    void zeroDistanceArrivesAtOnce() {
        assertEquals(0, DripFall.fallTicks(0, 0));
        assertEquals(0, DripFall.fallTicks(0, TapDrip.DRIP_LEAVE_SPEED));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.1, 1.0, 1.875, 2.875, 10.0, 64.0, 300.0})
    void fallTicksMatchTheDripParticle(double distance) {
        assertEquals(particleTicksToPass(distance, 0), DripFall.fallTicks(distance, 0));
        assertEquals(particleTicksToPass(distance, -TapDrip.DRIP_LEAVE_SPEED),
                DripFall.fallTicks(distance, -TapDrip.DRIP_LEAVE_SPEED));
    }

    @Test
    void theHangLastsTheFastestParticleGradesInterval() {
        assertEquals(TapDripGrade.ONE_PER_4_TICKS.intervalTicks(), DripFall.HANG_TICKS);
    }

    /**
     * From a tap on a full block (0.125 below the spigot, no room to fall) up
     * through the drop's exact fit and on to long falls.
     */
    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.1, 0.125, 0.144, 0.145, 0.2, 0.875, 1.125, 1.875, 2.875, 5.0, 10.0, 64.0, 300.0})
    void releaseQueuesTheLandingOnTheTickTheClientDropMeetsTheSurface(double spigotToSurface) {
        int releaseTick = 1200;
        double spigotY = 64.125;

        assertEquals(releaseTick + particleTicksToArrive(spigotToSurface, -TapDrip.DRIP_LEAVE_SPEED),
                TapDrip.landingTick(releaseTick, spigotY, spigotY - spigotToSurface),
                "spigot to surface " + spigotToSurface);
    }

    @Test
    void theLandingMeetsTheClientDropAtEverySpigotHeightInASweep() {
        double spigotY = 64.125;
        for (int step = 0; step <= 1000; step++) {
            double spigotToSurface = step * 0.01;
            assertEquals(particleTicksToArrive(spigotToSurface, -TapDrip.DRIP_LEAVE_SPEED),
                    TapDrip.landingTick(0, spigotY, spigotY - spigotToSurface),
                    "spigot to surface " + spigotToSurface);
        }
    }

    @Test
    void fallTicksRiseWithDistance() {
        int previous = 0;
        for (int blocks = 1; blocks <= 100; blocks++) {
            int ticks = DripFall.fallTicks(blocks, 0);
            assertTrue(ticks >= previous, "fall ticks never drop as the distance grows, at " + blocks);
            previous = ticks;
        }
        assertTrue(DripFall.fallTicks(100, 0) > DripFall.fallTicks(1, 0));
    }

    @Test
    void landsOnStoneBelowThreeAirBlocks() {
        int stoneY = TAP_POS.getY() - 4;
        TapDripLanding landing = TapDripLanding.scan(TAP_POS, MIN_Y, solidAt(stoneY, Shapes.block()));

        assertNotNull(landing);
        assertEquals(new BlockPos(0, stoneY, 0), landing.pos());
        assertEquals(stoneY + 1.0, landing.surfaceY());
    }

    @Test
    void landsOnTheCollisionTopOfAPartialBlock() {
        int slabY = TAP_POS.getY() - 2;
        VoxelShape slab = Shapes.box(0, 0, 0, 1, HALF, 1);
        TapDripLanding landing = TapDripLanding.scan(TAP_POS, MIN_Y, solidAt(slabY, slab));

        assertNotNull(landing);
        assertEquals(slabY + HALF, landing.surfaceY());
    }

    @Test
    void landsOnTheLowestBlockItReaches() {
        TapDripLanding landing = TapDripLanding.scan(TAP_POS, MIN_Y, solidAt(MIN_Y, Shapes.block()));

        assertNotNull(landing);
        assertEquals(MIN_Y, landing.pos().getY());
    }

    @Test
    void bottomlessDropHasNoLanding() {
        assertNull(TapDripLanding.scan(TAP_POS, MIN_Y, pos -> Shapes.empty()));
    }

    @Test
    void theTapsOwnBlockIsNotALanding() {
        assertNull(TapDripLanding.scan(TAP_POS, MIN_Y, solidAt(TAP_POS.getY(), Shapes.block())));
    }
}
