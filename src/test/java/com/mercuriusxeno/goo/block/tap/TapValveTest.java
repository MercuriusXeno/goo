package com.mercuriusxeno.goo.block.tap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rate panel's anchor sits above the top center of the valve on every
 * facing.
 */
class TapValveTest {

    private static final BlockPos TAP_POS = new BlockPos(10, 64, -3);
    private static final double EPSILON = 1e-9;

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
    void panelAnchorSitsAboveTheValvesTopCenter(Direction facing) {
        AABB valve = TapValve.shape(facing).bounds().move(TAP_POS);

        Vec3 anchor = TapValve.panelAnchor(TAP_POS, facing);

        assertEquals(valve.getCenter().x, anchor.x, EPSILON);
        assertEquals(valve.getCenter().z, anchor.z, EPSILON);
        assertTrue(anchor.y > valve.maxY, "anchor above the valve top on " + facing);
        assertTrue(anchor.y < TAP_POS.getY() + 1, "anchor inside the tap's block on " + facing);
    }
}
