package com.mercuriusxeno.goo.client.outsider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how a section's voidstone faces merge before they draw: a row of tops
 * runs east as one quad, and identical rows merge south into one rectangle, so
 * a whole floor section draws as one quad each way; a row that differs keeps
 * its own quad. Faces pack as x, then y, z and direction four bits apiece,
 * then the run east and the run south less one.
 */
class VoidstoneFacesTest {

    private static final int UP = 1;
    private static final int DOWN = 0;
    private static final int WHOLE_ROW = 15;
    private static final int SECTION = 16;

    @Test
    void aFloorSectionsTopsMergeIntoOneQuadAndItsBottomsIntoAnother() {
        int[] rows = new int[2 * SECTION];
        int count = 0;
        for (int z = 0; z < SECTION; z++) {
            rows[count++] = row(0, z, DOWN, WHOLE_ROW);
            rows[count++] = row(0, z, UP, WHOLE_ROW);
        }

        int[] merged = VoidstoneFaces.mergeRows(rows, count);

        assertArrayEquals(new int[] {row(0, 0, DOWN, WHOLE_ROW) | WHOLE_ROW << 19,
            row(0, 0, UP, WHOLE_ROW) | WHOLE_ROW << 19}, merged);
    }

    @Test
    void aFloorSectionTheClientLacksDrawsItsFloorLayerWhole() {
        int floorSection = Math.floorDiv(com.mercuriusxeno.goo.ability.outsider.OutsiderRooms.ROOM_FLOOR_Y, SECTION);
        int layer = com.mercuriusxeno.goo.ability.outsider.OutsiderRooms.ROOM_FLOOR_Y - floorSection * SECTION;

        assertArrayEquals(new int[] {(layer << 4 | DOWN << 12 | WHOLE_ROW << 15) | WHOLE_ROW << 19,
            (layer << 4 | UP << 12 | WHOLE_ROW << 15) | WHOLE_ROW << 19}, VoidstoneFaces.wholeFloorOf(floorSection),
                "the floor layer's bottom and top, each across the whole section");
        assertEquals(0, VoidstoneFaces.wholeFloorOf(floorSection + 1).length, "a section above holds no floor");
    }

    @Test
    void aRowCutShortKeepsItsOwnQuad() {
        int[] rows = {row(0, 0, UP, WHOLE_ROW), row(0, 1, UP, 7), row(0, 2, UP, WHOLE_ROW)};

        int[] merged = VoidstoneFaces.mergeRows(rows, rows.length);

        assertEquals(3, merged.length, "no row should merge across the short one");
    }

    @Test
    void aTopContinuesTheRunJustWestOfItInItsRow() {
        int run = row(0, 3, UP, 2);

        assertTrue(VoidstoneFaces.extendsRun(run, face(3, 3, UP)), "the block just past the run's end");
        assertTrue(!VoidstoneFaces.extendsRun(run, face(4, 3, UP)), "a block past a gap");
        assertTrue(!VoidstoneFaces.extendsRun(run, face(3, 4, UP)), "a block in the next row");
    }

    private static int row(int x, int z, int direction, int runLessOne) {
        return face(x, z, direction) | runLessOne << 15;
    }

    private static int face(int x, int z, int direction) {
        return x | z << 8 | direction << 12;
    }
}
