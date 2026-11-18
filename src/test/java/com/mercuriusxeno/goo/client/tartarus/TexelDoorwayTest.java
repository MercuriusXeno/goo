package com.mercuriusxeno.goo.client.tartarus;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Tartarus' texel doorway as round two of the artifact draft drew it: its texels appear scattered across its
 * face rather than in an order that moves, and its motes fade out as it goes rather than all at once
 * (decision tartarus-draws-its-own-portal-with-the-nether-portal-texture).
 */
class TexelDoorwayTest {

    private static final double TIME = 12.5;

    @Test
    void aWholeDoorwayShowsEveryTexel() {
        assertEquals(TexelDoorway.ACROSS * TexelDoorway.UP, shownOf(new TexelDoorway(1, 0, TIME)));
    }

    @Test
    void aHalfAppearedDoorwayShowsTexelsScatteredOverItsWholeFace() {
        TexelDoorway half = new TexelDoorway(0.3, 0, TIME);
        int shown = shownOf(half);
        boolean lowHalf = false;
        boolean highHalf = false;
        for (int up = 0; up < TexelDoorway.UP; up++) {
            for (int across = 0; across < TexelDoorway.ACROSS; across++) {
                lowHalf |= half.shows(across, up) && up < TexelDoorway.UP / 2;
                highHalf |= half.shows(across, up) && up >= TexelDoorway.UP / 2;
            }
        }

        assertTrue(shown > 0 && shown < TexelDoorway.ACROSS * TexelDoorway.UP, "part shows: " + shown);
        assertTrue(lowHalf && highHalf, "scattered over both halves");
    }

    @Test
    void theMotesDimAsTheDoorwayGoes() {
        double whole = brightest(0);
        double half = brightest(0.5);
        double late = brightest(0.9);

        assertTrue(half < whole, half + " dims below " + whole);
        assertTrue(late < half / 2, late + " dims well below " + half);
    }

    private static int shownOf(TexelDoorway doorway) {
        int shown = 0;
        for (int up = 0; up < TexelDoorway.UP; up++) {
            for (int across = 0; across < TexelDoorway.ACROSS; across++) {
                shown += doorway.shows(across, up) ? 1 : 0;
            }
        }
        return shown;
    }

    private static double brightest(double close) {
        List<TexelDoorway.Mote> motes = new ArrayList<>();
        new TexelDoorway(1, close, TIME).motes(motes::add);
        return motes.stream().mapToDouble(mote -> (mote.color() >>> 24) / 255.0).max().orElse(0);
    }
}
