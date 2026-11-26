package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.SortRoute;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.TranslucentVisualPathRegistrations;
import com.mercuriusxeno.goo.client.ber.BeaconBeams;
import com.mercuriusxeno.goo.client.ber.ReflectorBeams;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The glow column a Laser-converted reflector or a beacon draws: its beams
 * and core queue onto the shared path at the prism's center, the column's own
 * sort point, and the column registers ahead of them, so the path's stable far
 * to near sort draws each beam after its own column, showing in front of it
 * rather than hidden behind it.
 * decision diagnose-and-correct-the-sunbeam-prism-depth
 */
class GlowColumnTest {

    private static final Runnable NO_DRAW = () -> { };

    @Test
    void theColumnRegistersAheadOfItsBeams() {
        List<Class<?>> farToNear = TranslucentVisualPathRegistrations.visualsOn(SortRoute.FAR_TO_NEAR);
        int column = farToNear.indexOf(GlowColumn.class);
        assertTrue(column >= 0 && column < farToNear.indexOf(BeaconBeams.class)
                && column < farToNear.indexOf(ReflectorBeams.class), "registered " + farToNear);
    }

    @Test
    void drawsTiedOnDistanceKeepTheirRegisteredOrder() {
        SortedDraw column = new SortedDraw(9, NO_DRAW);
        SortedDraw beam = new SortedDraw(9, NO_DRAW);
        assertEquals(List.of(column, beam), TranslucentVisualPathRegistrations.farToNear(List.of(column, beam)));
    }
}
