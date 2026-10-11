package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A drink's initializer is one square per picked block, Pulser's square in
 * bright green, flying from the glove to the block's near face over the pick's
 * nine ticks and growing from Pulser's hand size to the face's own
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkSquaresTest {

    private static final double DELTA = 1e-9;
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final BlockPos BLOCK = new BlockPos(7, 2, 3);
    private static final long PICKED = 100;
    private static final long START = 109;
    private static final double HALF_WAY = 0.5;

    @Test
    void theSquareLandsOnThePointOfTheBlockNearestTheGlove() {
        assertEquals(new Vec3(7, 2.2, 3.1), DrinkSquares.nearFaceOf(BLOCK, GLOVE));
        assertEquals(new Vec3(8, 3, 4), DrinkSquares.nearFaceOf(BLOCK, new Vec3(20, 30, 40)));
    }

    @Test
    void theSquareFliesOverTheNineTicksFromThePickToTheStart() {
        assertEquals(0, DrinkSquares.shareOf(PICKED, START, PICKED), DELTA);
        assertEquals(HALF_WAY, DrinkSquares.shareOf(PICKED, START, PICKED + HALF_WAY * (START - PICKED)), DELTA);
        assertEquals(1, DrinkSquares.shareOf(PICKED, START, START), DELTA);
        assertEquals(1, DrinkSquares.shareOf(PICKED, START, START + 1), DELTA, "a landed square flies no further");
    }

    @Test
    void theSquareLeavesTheHandAtPulsersSizeAndLandsAtTheFaces() {
        assertEquals(SignalRings.HAND_RADIUS, DrinkSquares.radiusAt(0), DELTA);
        assertEquals(DrinkBody.MOUTH, DrinkSquares.radiusAt(1), DELTA);
        assertEquals((SignalRings.HAND_RADIUS + DrinkBody.MOUTH) / 2, DrinkSquares.radiusAt(HALF_WAY), DELTA);
    }

    @Test
    void theSquareRidesTheLineFromTheGloveToTheFaceFacingAlongIt() {
        Vec3 face = DrinkSquares.nearFaceOf(BLOCK, GLOVE);
        DrinkSquares.Flight flight = DrinkSquares.flightOf(GLOVE, face, HALF_WAY);

        assertEquals(GLOVE.lerp(face, HALF_WAY), flight.center());
        assertEquals(face.subtract(GLOVE).normalize(), flight.axis());
        assertEquals(DrinkSquares.radiusAt(HALF_WAY), flight.radius(), DELTA);
        assertEquals(new Vec3(0, 1, 0), DrinkSquares.flightOf(GLOVE, GLOVE, 0).axis(), "a face on the glove faces up");
    }
}
