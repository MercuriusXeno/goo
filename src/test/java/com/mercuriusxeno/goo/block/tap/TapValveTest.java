package com.mercuriusxeno.goo.block.tap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rate panel stands above the valve and in front of it and of a slotted
 * canister: from a camera on any side or above, on every facing, every
 * corner of the valve and the canister lies behind the camera-facing panel.
 */
class TapValveTest {

    private static final BlockPos TAP_POS = new BlockPos(10, 64, -3);
    private static final double CAMERA_DISTANCE = 3;
    private static final double EYE_RISE = 0.6;

    /**
     * @return cameras around the tap: level with the valve on four sides, and above on the diagonals
     */
    private static List<Vec3> cameras() {
        Vec3 center = Vec3.atCenterOf(TAP_POS);
        return List.of(
                center.add(CAMERA_DISTANCE, 0, 0), center.add(-CAMERA_DISTANCE, 0, 0),
                center.add(0, 0, CAMERA_DISTANCE), center.add(0, 0, -CAMERA_DISTANCE),
                center.add(CAMERA_DISTANCE, EYE_RISE * CAMERA_DISTANCE, CAMERA_DISTANCE),
                center.add(-CAMERA_DISTANCE, EYE_RISE * CAMERA_DISTANCE, -CAMERA_DISTANCE),
                center.add(0, CAMERA_DISTANCE, 0));
    }

    private static void assertBehindThePanel(AABB part, Vec3 anchor, Vec3 camera, String what) {
        Vec3 view = camera.subtract(anchor).normalize();
        double depth = TapValve.cornerDepth(part, anchor, view);
        assertTrue(depth < 0, what + " pokes " + depth + " in front of the panel, camera at " + camera);
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
    void theValveAndASlottedCanisterLieBehindThePanelFromEverySide(Direction facing) {
        AABB valve = TapValve.shape(facing).bounds().move(TAP_POS);
        AABB canister = TapHitRegion.canisterSlotShape(facing).bounds().move(TAP_POS);
        for (Vec3 camera : cameras()) {
            Vec3 anchor = TapValve.panelAnchor(TAP_POS, facing, camera, true);

            assertBehindThePanel(valve, anchor, camera, "valve on " + facing);
            assertBehindThePanel(canister, anchor, camera, "canister on " + facing);
        }
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
    void thePanelStandsAboveTheValveTop(Direction facing) {
        AABB valve = TapValve.shape(facing).bounds().move(TAP_POS);
        for (Vec3 camera : cameras()) {
            assertTrue(TapValve.panelAnchor(TAP_POS, facing, camera, false).y > valve.maxY,
                    "anchor above the valve top on " + facing + ", camera at " + camera);
        }
    }
}
