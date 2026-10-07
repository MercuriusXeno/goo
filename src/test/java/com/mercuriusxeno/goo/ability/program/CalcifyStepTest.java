package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calcify reaches the blocks whose centers stand inside the stream's cone,
 * nearest the apex first (decision petrify-stone-encasement-and-calcify-map).
 */
class CalcifyStepTest {

    private static final Vec3 APEX = new Vec3(0.5, 0.5, 0.5);
    private static final Vec3 REACH = new Vec3(6.5, 0.5, 0.5);
    private static final double CONE = 30;

    @Test
    void blocksOnTheAxisAreInsideNearestFirst() {
        List<BlockPos> inside = CalcifyStep.blocksInCone(APEX, REACH, CONE);
        assertTrue(inside.contains(new BlockPos(3, 0, 0)));
        assertTrue(inside.indexOf(new BlockPos(1, 0, 0)) < inside.indexOf(new BlockPos(5, 0, 0)));
    }

    @Test
    void blocksOffTheConeOrPastTheReachAreOutside() {
        List<BlockPos> inside = CalcifyStep.blocksInCone(APEX, REACH, CONE);
        assertFalse(inside.contains(new BlockPos(3, 3, 0)));
        assertFalse(inside.contains(new BlockPos(7, 0, 0)));
        assertFalse(inside.contains(new BlockPos(-2, 0, 0)));
    }

    @Test
    void aConeWithNoReachHoldsNothing() {
        assertEquals(List.of(), CalcifyStep.blocksInCone(APEX, APEX, CONE));
    }
}
