package com.mercuriusxeno.goo.client.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the rule turning a cell and the cells of its goo around it into
 * its slime core's boxes, in fifteenths of a block: two layers of goo stand
 * between the core and wherever the goo ends, and a core runs through that
 * padding to a neighbour whose core it would otherwise be parted from by
 * padding alone.
 */
class SlimeCoreBoxTest {

    private static final int WHOLE = 15;
    private static final int PADDING = 2;

    private static SameGooCells cell(int layers) {
        return new SameGooCells().at(0, 0, 0, layers);
    }

    @Nested
    class LoneCell {

        @Test
        void fourLayersHoldNoCore() {
            assertEquals(Optional.empty(), SlimeCoreBox.of(4));
        }

        @Test
        void fiveLayersHoldACoreOneLayerThick() {
            assertEquals(Optional.of(new SlimeCoreBox(2, 2, 2, 13, 3, 13)), SlimeCoreBox.of(5));
        }

        @Test
        void eachLayerPastFiveAddsALayerToTheCore() {
            assertEquals(Optional.of(new SlimeCoreBox(2, 2, 2, 13, 6, 13)), SlimeCoreBox.of(8));
        }

        @Test
        void aWholeBlockHoldsACoreTwoLayersInFromEveryFace() {
            assertEquals(Optional.of(new SlimeCoreBox(2, 2, 2, 13, 13, 13)), SlimeCoreBox.of(15));
        }
    }

    /**
     * Toward the east side, the face at x 15.
     */
    @Nested
    class SideJoin {

        @Test
        void aWholeBlockBesideAWholeBlockReachesTheSharedFaceOverItsWholeCore() {
            List<SlimeCoreBox> boxes = SlimeCoreBox.boxesOf(cell(15).at(1, 0, 0, 15));
            assertEquals(List.of(new Span(2, 13)), eastReach(boxes));
        }

        @Test
        void aWholeBlockBesideEightLayersReachesTheFaceOverTheSharedHeightAndStandsClearAbove() {
            List<SlimeCoreBox> boxes = SlimeCoreBox.boxesOf(cell(15).at(1, 0, 0, 8));
            assertEquals(List.of(new Span(2, 6)), eastReach(boxes));
            assertTrue(boxes.stream().filter(box -> box.maxY() > 6).allMatch(box -> box.maxX() <= WHOLE - PADDING),
                    "the core stands 2/15 clear of the face above the shared height");
        }

        @Test
        void aCoreBesideFourLayersStandsClearAtEveryHeight() {
            assertStandsClearEast(SlimeCoreBox.boxesOf(cell(15).at(1, 0, 0, 4)));
        }

        @Test
        void aCoreBesideACellOfAnotherGooOrOpenAirStandsClearAtEveryHeight() {
            // the model reads a cell of another goo, any other block and open air alike as no cell of its goo
            assertStandsClearEast(SlimeCoreBox.boxesOf(cell(15)));
        }

        @Test
        void eachJoinedSideReachesItsOwnFace() {
            List<SlimeCoreBox> boxes = SlimeCoreBox.boxesOf(cell(15).at(0, 0, -1, 15).at(-1, 0, 0, 15));
            assertTrue(boxes.stream().anyMatch(box -> box.minZ() == 0), "reaches the north face");
            assertTrue(boxes.stream().anyMatch(box -> box.minX() == 0), "reaches the west face");
            assertStandsClearEast(boxes);
        }

        @Test
        void theCornerFillsWhereBothSidesAndTheDiagonalHoldCores() {
            List<SlimeCoreBox> boxes = SlimeCoreBox.boxesOf(cell(15).at(1, 0, 0, 15).at(0, 0, -1, 15)
                    .at(1, 0, -1, 15));
            assertTrue(boxes.contains(new SlimeCoreBox(13, 2, 0, 15, 13, 2)));
        }

        @Test
        void theCornerStaysPaddedWhereTheDiagonalHoldsNoCore() {
            List<SlimeCoreBox> boxes = SlimeCoreBox.boxesOf(cell(15).at(1, 0, 0, 15).at(0, 0, -1, 15));
            assertTrue(boxes.stream().noneMatch(box -> box.maxX() == WHOLE && box.minZ() == 0));
        }
    }

    /**
     * A height range in fifteenths.
     *
     * @param floor the bottom
     * @param top   the top
     */
    record Span(int floor, int top) {
    }

    private static List<Span> eastReach(List<SlimeCoreBox> boxes) {
        return boxes.stream().filter(box -> box.maxX() == WHOLE && box.minZ() == PADDING)
                .map(box -> new Span(box.minY(), box.maxY())).toList();
    }

    private static void assertStandsClearEast(List<SlimeCoreBox> boxes) {
        assertTrue(boxes.stream().allMatch(box -> box.maxX() <= WHOLE - PADDING),
                "the core stands 2/15 clear of the east face");
    }
}
