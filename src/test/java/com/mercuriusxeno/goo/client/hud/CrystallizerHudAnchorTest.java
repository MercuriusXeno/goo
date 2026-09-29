package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers where the crystal panel stands: beside the crystal at its mid height, turned toward the player. */
class CrystallizerHudAnchorTest {

    private static final BlockPos POS = new BlockPos(10, 64, -3);
    private static final long CRYSTALLIZED = 1000;
    private static final double PIXELS = 16.0;
    private static final double EPSILON = 1e-9;
    private static final double PANEL_HALF_WIDTH = 0.4;

    private static Vec3 crystalCenter(Direction facing) {
        double[] spot = CrystallizerLayout.modelToWorld(facing, CrystalCluster.BASE_X, CrystalCluster.BASE_Z);
        return new Vec3(POS.getX() + spot[0] / PIXELS, POS.getY() + 1.0, POS.getZ() + spot[1] / PIXELS);
    }

    @Nested
    class Anchor {

        @ParameterizedTest
        @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
        void standsBesideTheCrystalAtItsMidHeightAcrossTheCameraLine(Direction facing) {
            Vec3 center = crystalCenter(facing);
            Vec3 camera = center.add(0, 0.6, 3);
            double[] reach = CrystalCluster.reach(CRYSTALLIZED);

            Vec3 anchor = CrystallizerHudAnchor.besideCrystal(POS, facing, CRYSTALLIZED, camera, PANEL_HALF_WIDTH);

            double offsetX = anchor.x - center.x;
            double offsetZ = anchor.z - center.z;
            double lineX = center.x - camera.x;
            double lineZ = center.z - camera.z;
            assertTrue(Math.hypot(offsetX, offsetZ) >= reach[0] / PIXELS + PANEL_HALF_WIDTH);
            assertEquals(POS.getY() + 1.0 + reach[1] / PIXELS / 2, anchor.y, EPSILON);
            assertEquals(0, offsetX * lineX + offsetZ * lineZ, EPSILON);
        }
    }

    @Nested
    class Placement {

        @Test
        void billboardsTowardTheCameraAtTheAnimatorsPitchAndFade() {
            PanelPlacement placement = CrystallizerHudAnchor.placement(Vec3.ZERO, 0.3f, 0.7f);

            assertEquals(PanelPlacement.Facing.RIM, placement.facing());
            assertEquals(0.3f, placement.pitch());
            assertEquals(0.7f, placement.opacity());
        }
    }
}
