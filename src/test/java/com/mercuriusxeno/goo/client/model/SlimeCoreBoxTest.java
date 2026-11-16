package com.mercuriusxeno.goo.client.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the rule turning a cell and the cells of its goo around it into
 * its slime core's boxes, in 120ths of a block: the core keeps a margin of
 * one size at every fill, 2 px at the sides and 0.4 px under and over it,
 * and runs through that margin to a neighbour whose core it would otherwise
 * be parted from by margin alone.
 */
class SlimeCoreBoxTest {

    private static final int BLOCK = 120;
    private static final int UNITS_PER_LAYER = 8;
    private static final int SIDE = 15;
    /** 0.4 px, the margin under and over every core. */
    private static final int VERTICAL = 3;
    private static final float VERTICAL_PIXELS = 0.4f;
    private static final float PIXELS_PER_UNIT = 16f / BLOCK;
    private static final float PIXELS_PER_LAYER = 16f / 15;
    private static final float EPSILON = 1e-4f;

    private static SameGooCells cell(int layers) {
        return new SameGooCells().at(0, 0, 0, layers);
    }

    private static float px(int units) {
        return units * PIXELS_PER_UNIT;
    }

    @Nested
    class LoneCell {

        /** residual-always-draws-a-core-inside-a-one-eighth-margin */
        @Test
        void oneLayerHoldsACoreTwelvePixelsWideFromFourTenthsOfAPixelUpToFourTenthsUnderItsSurface() {
            SlimeCoreBox core = SlimeCoreBox.of(1).orElseThrow();

            assertEquals(2f, px(core.minX()), EPSILON);
            assertEquals(14f, px(core.maxX()), EPSILON);
            assertEquals(2f, px(core.minZ()), EPSILON);
            assertEquals(14f, px(core.maxZ()), EPSILON);
            assertEquals(VERTICAL_PIXELS, px(core.minY()), EPSILON);
            assertEquals(PIXELS_PER_LAYER - VERTICAL_PIXELS, px(core.maxY()), EPSILON);
        }

        @Test
        void everyFillKeepsTwoPixelsAtTheSidesAndFourTenthsOfAPixelUnderAndOver() {
            for (int layers = 1; layers <= 15; layers++) {
                SlimeCoreBox core = SlimeCoreBox.of(layers).orElseThrow();
                float height = layers * PIXELS_PER_LAYER;

                assertEquals(new SlimeCoreBox(SIDE, core.minY(), SIDE, BLOCK - SIDE, core.maxY(), BLOCK - SIDE), core,
                        "the sides at " + layers + " layers");
                assertEquals(VERTICAL_PIXELS, px(core.minY()), EPSILON, "the goo under the core at " + layers);
                assertEquals(height - VERTICAL_PIXELS, px(core.maxY()), EPSILON, "the goo over the core at " + layers);
            }
        }

        @Test
        void aWholeBlockHoldsACoreTwoPixelsInFromEverySideAndFourTenthsFromItsFloorAndTop() {
            assertEquals(Optional.of(new SlimeCoreBox(SIDE, VERTICAL, SIDE, BLOCK - SIDE, BLOCK - VERTICAL,
                    BLOCK - SIDE)), SlimeCoreBox.of(15));
        }
    }

    /**
     * Toward the east side, the face at x 120.
     */
    @Nested
    class SideJoin {

        @Test
        void aWholeBlockBesideAWholeBlockReachesTheSharedFaceOverItsWholeCore() {
            List<SlimeCoreBox> boxes = SlimeCoreBox.boxesOf(cell(15).at(1, 0, 0, 15));
            assertEquals(List.of(new Span(VERTICAL, BLOCK - VERTICAL)), eastReach(boxes));
        }

        @Test
        void aWholeBlockBesideEightLayersReachesTheFaceOverTheSharedHeightAndStandsClearAbove() {
            int eightLayerTop = 8 * UNITS_PER_LAYER - VERTICAL;
            List<SlimeCoreBox> boxes = SlimeCoreBox.boxesOf(cell(15).at(1, 0, 0, 8));
            assertEquals(List.of(new Span(VERTICAL, eightLayerTop)), eastReach(boxes));
            assertTrue(boxes.stream().filter(box -> box.maxY() > eightLayerTop)
                    .allMatch(box -> box.maxX() <= BLOCK - SIDE),
                    "the core stands 2 px clear of the face above the shared height");
        }

        /** residual-always-draws-a-core-inside-a-one-eighth-margin */
        @Test
        void everyPairOfFillsSideBySideJoinsItsCoresAtTheSharedFace() {
            List<String> parted = new ArrayList<>();
            for (int west = 1; west <= 15; west++) {
                for (int east = 1; east <= 15; east++) {
                    SameGooCells around = cell(west).at(1, 0, 0, east);
                    if (SlimeCoreBox.boxesOf(around).stream().noneMatch(box -> box.maxX() == BLOCK)) {
                        parted.add(west + " beside " + east);
                    }
                }
            }
            assertEquals(List.of(), parted, "these fills side by side draw cores that never meet");
        }

        @Test
        void aCoreBesideACellOfAnotherGooOrOpenAirStandsClearAtEveryHeight() {
            // the model reads a cell of another goo, any other block and open air alike as no cell of its goo
            assertStandsClearEast(SlimeCoreBox.boxesOf(cell(15)));
        }

        /** residual-always-draws-a-core-inside-a-one-eighth-margin */
        @Test
        void twoOneLayerCellsSideBySideJoinOverTheirCoresAndStandClearOfOpenSides() {
            List<SlimeCoreBox> boxes = SlimeCoreBox.boxesOf(cell(1).at(1, 0, 0, 1));

            assertEquals(List.of(new Span(VERTICAL, UNITS_PER_LAYER - VERTICAL)), eastReach(boxes));
            assertTrue(boxes.stream().allMatch(box -> box.minX() >= SIDE && box.minZ() >= SIDE
                    && box.maxZ() <= BLOCK - SIDE), "the core keeps 2 px toward the open sides");
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
            assertTrue(boxes.contains(new SlimeCoreBox(BLOCK - SIDE, VERTICAL, 0, BLOCK, BLOCK - VERTICAL, SIDE)));
        }

        @Test
        void theCornerStaysPaddedWhereTheDiagonalHoldsNoCore() {
            List<SlimeCoreBox> boxes = SlimeCoreBox.boxesOf(cell(15).at(1, 0, 0, 15).at(0, 0, -1, 15));
            assertTrue(boxes.stream().noneMatch(box -> box.maxX() == BLOCK && box.minZ() == 0));
        }
    }

    /**
     * A column of cells: a full cell at the origin with the goo standing on it.
     */
    @Nested
    class Stack {

        private static Optional<Span> ownSpan(LayersAround around) {
            return SlimeCoreBox.boxesOf(around).stream()
                    .filter(box -> box.minX() == SIDE && box.minZ() == SIDE)
                    .map(box -> new Span(box.minY(), box.maxY())).findFirst();
        }

        /** stacked-goo-cells-share-one-slime-core */
        @Test
        void aTwoHighColumnKeepsFourTenthsOfAPixelUnderItAndUnderItsSurfaceAtEveryUpperFill() {
            for (int upper = 1; upper <= 15; upper++) {
                Span lower = ownSpan(cell(15).at(0, 1, 0, upper)).orElseThrow();
                Optional<Span> top = ownSpan(cell(upper).at(0, -1, 0, 15));
                int columnTop = top.map(span -> BLOCK + span.top()).orElse(lower.top());

                assertEquals(VERTICAL, lower.floor(), "the core's floor under " + upper + " layers");
                assertEquals(BLOCK + upper * UNITS_PER_LAYER - VERTICAL, columnTop, "the core's top under the surface of "
                        + upper + " layers");
                top.ifPresent(span -> {
                    assertEquals(0, span.floor(), "the upper core's floor at the shared face");
                    assertEquals(BLOCK, lower.top(), "the lower core's top at the shared face");
                });
            }
        }

        @Test
        void oneLayerOnAFullCellHoldsTheColumnsCoreTop() {
            assertEquals(Optional.of(new Span(VERTICAL, BLOCK)), ownSpan(cell(15).at(0, 1, 0, 1)));
            assertEquals(Optional.of(new Span(0, UNITS_PER_LAYER - VERTICAL)), ownSpan(cell(1).at(0, -1, 0, 15)));
        }

        @Test
        void theMiddleOfAThreeHighStackIsCoreFromFloorToTop() {
            assertEquals(Optional.of(new Span(0, BLOCK)), ownSpan(cell(15).at(0, -1, 0, 15).at(0, 1, 0, 15)));
        }

        @Test
        void aCellOnABlockOfAnotherKindKeepsItsMarginBelow() {
            assertEquals(Optional.of(new Span(VERTICAL, BLOCK - VERTICAL)), ownSpan(cell(15)));
        }

        @Test
        void theMiddleOfAStackJoinsALoneEightLayerCellOverItsCore() {
            SameGooCells around = cell(15).at(0, -1, 0, 15).at(0, 1, 0, 15).at(1, 0, 0, 8);
            assertEquals(List.of(new Span(VERTICAL, 8 * UNITS_PER_LAYER - VERTICAL)),
                    eastReach(SlimeCoreBox.boxesOf(around)));
        }

        @Test
        void theMiddlesOfTwoStacksJoinOverTheirWholeHeight() {
            SameGooCells around = cell(15).at(0, -1, 0, 15).at(0, 1, 0, 15)
                    .at(1, 0, 0, 15).at(1, -1, 0, 15).at(1, 1, 0, 15);
            assertEquals(List.of(new Span(0, BLOCK)), eastReach(SlimeCoreBox.boxesOf(around)));
        }
    }

    /**
     * A height range in 120ths of a block.
     *
     * @param floor the bottom
     * @param top   the top
     */
    record Span(int floor, int top) {
    }

    private static List<Span> eastReach(List<SlimeCoreBox> boxes) {
        return boxes.stream().filter(box -> box.maxX() == BLOCK && box.minZ() == SIDE)
                .map(box -> new Span(box.minY(), box.maxY())).toList();
    }

    private static void assertStandsClearEast(List<SlimeCoreBox> boxes) {
        assertTrue(boxes.stream().allMatch(box -> box.maxX() <= BLOCK - SIDE),
                "the core stands 2 px clear of the east face");
    }
}
