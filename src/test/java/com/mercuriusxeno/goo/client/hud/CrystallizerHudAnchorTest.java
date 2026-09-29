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

/** Covers where the crystal panel stands: centred over the crystal just above its tip, turned toward the player. */
class CrystallizerHudAnchorTest {

    private static final BlockPos POS = new BlockPos(10, 64, -3);
    private static final long CRYSTALLIZED = 1000;
    private static final double PIXELS = 16.0;
    private static final double EPSILON = 1e-9;

    @Nested
    class Anchor {

        @ParameterizedTest
        @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
        void standsCentredOverTheCrystalJustAboveItsTip(Direction facing) {
            double[] spot = CrystallizerLayout.modelToWorld(facing, CrystalCluster.BASE_X, CrystalCluster.BASE_Z);
            double tip = POS.getY() + 1.0 + CrystalCluster.reach(CRYSTALLIZED)[1] / PIXELS;

            Vec3 anchor = CrystallizerHudAnchor.aboveCrystal(POS, facing, CRYSTALLIZED);

            assertEquals(POS.getX() + spot[0] / PIXELS, anchor.x, EPSILON);
            assertEquals(POS.getZ() + spot[1] / PIXELS, anchor.z, EPSILON);
            assertTrue(anchor.y > tip && anchor.y <= tip + 2 / PIXELS);
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
