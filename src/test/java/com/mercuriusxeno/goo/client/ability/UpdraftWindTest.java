package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.LingerStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.WindStep;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Updraft's rising wind: the column is sized from the ability's updraft step,
 * a column blows once a tick however many frames draw it, and its lines leave
 * the floor inside its width and rise straight up through its height
 * (decision updraft-blob-stands-a-column-of-wind).
 */
class UpdraftWindTest {

    private static final double EPSILON = 1e-9;
    private static final Vec3 BASE = new Vec3(2.5, 1, 2.5);

    @Test
    void theColumnIsSizedFromTheShippedUpdraftStep() {
        UpdraftWind.Column column = UpdraftWind.columnOf(AbilityJson.decode("typhoon_updraft").behaviors())
                .orElseThrow();

        assertEquals(1, column.radius(), EPSILON);
        assertEquals(8, column.height(), EPSILON);
    }

    @Test
    void anAbilityWithNoUpdraftStandsNoColumn() {
        List<Step> program = List.of(new LingerStep(List.of(new WindStep(false))));

        assertTrue(UpdraftWind.columnOf(program).isEmpty());
    }

    @Test
    void aColumnBlowsOnceATickHoweverManyFramesSeeIt() {
        BlockPos pos = new BlockPos(40, 70, -12);

        assertTrue(UpdraftWind.blowsNow(pos, 100L));
        assertFalse(UpdraftWind.blowsNow(pos, 100L));
        assertTrue(UpdraftWind.blowsNow(pos, 101L));
        UpdraftWind.clear();
    }

    @Test
    void aRisingLineLeavesTheFloorInsideTheWidthAndRisesTheColumnsHeight() {
        WindLines.Gust gust = WindLines.riseGust(BASE, 1, 8, 1, -1);

        assertEquals(BASE.x + WindLines.RISE_INSET, gust.origin().x, EPSILON);
        assertEquals(BASE.z - WindLines.RISE_INSET, gust.origin().z, EPSILON);
        assertEquals(BASE.y, gust.origin().y, EPSILON);
        assertEquals(new Vec3(0, 1, 0), gust.axis());
        assertEquals(8, gust.range(), EPSILON);
        assertFalse(gust.carried());
    }
}
