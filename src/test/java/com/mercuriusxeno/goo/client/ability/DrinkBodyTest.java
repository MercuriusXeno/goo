package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block's lump flows into its stream from the first tick: its back stands
 * where the cube stood and advances as it empties, its front is the stream's
 * entry, and a point of the cube is carried between them, lofted from the
 * cube's shape to the stream's ring (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkBodyTest {

    private static final double DELTA = 1e-6;
    private static final Vec3 CENTER = new Vec3(7.5, 2.5, 3.5);
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final long SEED = 42;
    private static final double NOW = 100;
    private static final double RADIUS = 0.05;
    private static final DrinkStream.Path PATH = new DrinkStream.Path(DrinkLayout.farSideOf(CENTER, GLOVE), GLOVE,
            SEED);
    private static final DrinkBody.RingAt RINGS = share -> DrinkStream.ring(PATH, share, NOW, RADIUS, 0, share);
    private static final DrinkBody.RingAt NO_RINGS = share -> DrinkStream.ring(PATH, share, NOW, 0, 0, share);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double HALFWAY = 0.5;

    private static Vec3 along() {
        return GLOVE.subtract(PATH.from()).normalize();
    }

    /**
     * @return a point of the cube's far side off the path's middle, square to the path
     */
    private static Vec3 ofTheBack() {
        Vec3 side = along().cross(UP).normalize();
        Vec3 across = along().cross(side);
        return PATH.from().add(side.scale(0.4)).add(across.scale(0.3));
    }

    @Test
    void theLumpIsTheWholeCubeAtTheStartAndGoneAtTheEnd() {
        DrinkBody.Lump whole = new DrinkBody.Lump(PATH, 0);
        DrinkBody.Lump gone = new DrinkBody.Lump(PATH, 1);

        assertEquals(0, whole.back(), DELTA);
        assertEquals(DrinkStream.BLOCK_SPAN, whole.length(), DELTA);
        assertEquals(1, whole.size(), DELTA);
        assertEquals(DrinkStream.BLOCK_SPAN, gone.back(), DELTA);
        assertEquals(0, gone.length(), DELTA);
        assertEquals(0, gone.size(), DELTA);
    }

    @Test
    void theBackAdvancesAndShrinksAsTheLumpEmpties() {
        DrinkBody.Lump half = new DrinkBody.Lump(PATH, HALFWAY);

        assertEquals(HALFWAY * DrinkStream.BLOCK_SPAN, half.back(), DELTA);
        assertEquals(HALFWAY * DrinkStream.BLOCK_SPAN, half.length(), DELTA);
        assertTrue(half.size() < 1 && half.size() > HALFWAY);
        assertEquals(HALFWAY * DrinkStream.BLOCK_SPAN, half.distanceOf(PATH.from()), DELTA);
        assertEquals(DrinkStream.BLOCK_SPAN, half.distanceOf(PATH.from().add(along())), DELTA);
    }

    @Test
    void aPointOfTheBackStandsWhereItStoodAtTheStart() {
        Vec3 point = ofTheBack();
        DrinkBody.Lump whole = new DrinkBody.Lump(PATH, 0);

        DrinkBody.Place place = DrinkBody.placeOf(point, UP, whole, RINGS);

        assertEquals(0, place.point().distanceTo(point), DELTA);
        assertEquals(0, place.normal().distanceTo(UP), DELTA);
    }

    @Test
    void aPointOfTheFrontLiesOnTheStreamsRingAtTheEntry() {
        Vec3 point = ofTheBack().add(along());
        DrinkBody.Lump whole = new DrinkBody.Lump(PATH, 0);
        DrinkStream.Ring entry = RINGS.at(DrinkStream.BLOCK_SPAN / PATH.length());

        DrinkBody.Place place = DrinkBody.placeOf(point, UP, whole, RINGS);

        assertEquals(RADIUS, place.point().distanceTo(entry.center()), DELTA);
        assertEquals(1, place.normal().length(), DELTA);
    }

    @Test
    void theWholeLumpHasFlowedIntoTheEntryAtTheEnd() {
        Vec3 point = ofTheBack();
        DrinkBody.Lump gone = new DrinkBody.Lump(PATH, 1);
        DrinkStream.Ring entry = NO_RINGS.at(DrinkStream.BLOCK_SPAN / PATH.length());

        DrinkBody.Place place = DrinkBody.placeOf(point, UP, gone, NO_RINGS);

        assertEquals(0, place.point().distanceTo(entry.center()), DELTA);
    }
}
