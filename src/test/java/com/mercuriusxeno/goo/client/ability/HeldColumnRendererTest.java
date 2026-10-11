package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Updraft's held column draws only for an armed world arc throw whose area is
 * a column, as wide and tall as the column it stands, brightest at its floor
 * (decision updraft-blob-stands-a-column-of-wind).
 */
class HeldColumnRendererTest {

    private static final double EPSILON = 1e-9;
    private static final Delivery ARC = Delivery.of(DeliveryKind.ARC);
    private static final AbilityArea COLUMN = new AbilityArea(AbilityArea.Shape.COLUMN, 8, 0, 1);

    @Test
    void theShippedUpdraftPreviewsItsColumn() {
        AbilityArea area = AbilityJson.decode("typhoon_updraft").area();

        assertEquals(COLUMN, area);
        assertTrue(HeldColumnRenderer.showsColumn(ARC, AbilityBadge.WORLD, area, true));
    }

    @Test
    void theColumnDrawsOnlyWhileArmedAndOnlyForAColumn() {
        assertFalse(HeldColumnRenderer.showsColumn(ARC, AbilityBadge.WORLD, COLUMN, false));
        assertFalse(HeldColumnRenderer.showsColumn(ARC, AbilityBadge.WORLD,
                new AbilityArea(AbilityArea.Shape.SPHERE, 3, 0), true));
        assertFalse(HeldColumnRenderer.showsColumn(Delivery.of(DeliveryKind.STREAM), AbilityBadge.WORLD, COLUMN,
                true));
    }

    @Test
    void aSquareSpansTheColumnsWidthAndCloses() {
        Vec3[] square = HeldColumnRenderer.square(new Vec3(2.5, 1, 2.5), COLUMN.width());

        assertEquals(square[0], square[square.length - 1]);
        assertEquals(2 * COLUMN.width(), square[1].x - square[0].x, EPSILON);
        assertEquals(2 * COLUMN.width(), square[2].z - square[1].z, EPSILON);
    }

    @Test
    void theColumnIsBrightestAtItsFloorAndFaintestAtItsTop() {
        assertEquals(HeldColumnRenderer.FLOOR_ALPHA, HeldColumnRenderer.alphaAt(0, COLUMN.size()));
        assertEquals(HeldColumnRenderer.TOP_ALPHA, HeldColumnRenderer.alphaAt(COLUMN.size(), COLUMN.size()));
    }
}
