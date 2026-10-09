package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block's lump flows into its stream like taffy from the first tick: its
 * near face leaves the entry at the flow's pace, its back creeps to the entry
 * over the drain, a point of the cube keeps the cube's cross-section and is
 * carried along, and past the entry the matter narrows and rounds from the
 * cube's width to the stream's over the funnel
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkBodyTest {

    private static final double DELTA = 1e-6;
    private static final Vec3 CENTER = new Vec3(7.5, 2.5, 3.5);
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final long SEED = 42;
    private static final DrinkStream.Path PATH = new DrinkStream.Path(DrinkLayout.farSideOf(CENTER, GLOVE), GLOVE,
            SEED);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double HALFWAY = 0.5;
    private static final double FLOWED = 1.3;
    private static final double STREAM = 0.05;

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
    void theNearFaceLeavesTheEntryAtOnceAndTheBackCreepsToItOverTheDrain() {
        DrinkBody.Lump start = new DrinkBody.Lump(PATH, 0, 0);
        DrinkBody.Lump later = new DrinkBody.Lump(PATH, HALFWAY, FLOWED);
        DrinkBody.Lump drained = new DrinkBody.Lump(PATH, 1, 2 * FLOWED);

        assertEquals(0, start.back(), DELTA);
        assertEquals(DrinkStream.BLOCK_SPAN, start.front(), DELTA);
        assertEquals(HALFWAY * DrinkStream.BLOCK_SPAN, later.back(), DELTA);
        assertEquals(DrinkStream.BLOCK_SPAN + FLOWED, later.front(), DELTA);
        assertEquals(DrinkStream.BLOCK_SPAN, drained.back(), DELTA);
    }

    @Test
    void theSlicesBetweenStretchEvenly() {
        DrinkBody.Lump later = new DrinkBody.Lump(PATH, HALFWAY, FLOWED);

        assertEquals(later.back(), later.distanceOf(0), DELTA);
        assertEquals((later.back() + later.front()) / 2, later.distanceOf(HALFWAY), DELTA);
        assertEquals(later.front(), later.distanceOf(1), DELTA);
    }

    @Test
    void aPointOfTheBackStandsWhereItStoodAtTheStartAndKeepsItsCrossSection() {
        Vec3 point = ofTheBack();
        DrinkBody.Lump start = new DrinkBody.Lump(PATH, 0, 0);
        DrinkBody.Lump later = new DrinkBody.Lump(PATH, HALFWAY, FLOWED);

        DrinkBody.Place stood = DrinkBody.placeOf(point, UP, start);
        DrinkBody.Place moved = DrinkBody.placeOf(point, UP, later);

        assertEquals(0, stood.point().distanceTo(point), DELTA);
        assertEquals(UP, stood.normal());
        assertEquals(HALFWAY * DrinkStream.BLOCK_SPAN, moved.point().distanceTo(point), DELTA);
        assertEquals(point.distanceTo(PATH.from()), moved.point().distanceTo(PATH.spineAt(later.back()
                / PATH.length())), DELTA);
    }

    @Test
    void aSlicePastTheEntryStandsAtTheEntryForTheStreamToDraw() {
        Vec3 point = ofTheBack().add(along());
        DrinkBody.Lump later = new DrinkBody.Lump(PATH, HALFWAY, FLOWED);

        DrinkBody.Place place = DrinkBody.placeOf(point, UP, later);

        assertEquals(ofTheBack().distanceTo(PATH.from()), place.point().distanceTo(PATH.spineAt(
                DrinkStream.BLOCK_SPAN / PATH.length())), DELTA);
    }

    @Test
    void theMatterNarrowsAndRoundsFromTheCubesWidthToTheStreamsOverTheFunnel() {
        double entry = DrinkStream.BLOCK_SPAN;
        double past = entry + DrinkBody.FUNNEL;

        assertEquals(DrinkBody.MOUTH, DrinkBody.widthAt(entry, STREAM), DELTA);
        assertEquals(DrinkBody.MOUTH, DrinkBody.widthAt(0, STREAM), DELTA);
        assertEquals(STREAM, DrinkBody.widthAt(past, STREAM), DELTA);
        assertEquals(STREAM, DrinkBody.widthAt(past + 1, STREAM), DELTA);
        double between = DrinkBody.widthAt(entry + DrinkBody.FUNNEL / 2, STREAM);
        assertTrue(between < DrinkBody.MOUTH && between > STREAM);
        assertEquals(0, DrinkBody.roundnessAt(entry), DELTA);
        assertEquals(1, DrinkBody.roundnessAt(past), DELTA);
    }
}
