package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where the crystal panel stands: beside the crystal at its mid height, off to the
 * camera's right of the crystal, turned toward the player (decision crystal-panel-stands-beside-crystal).
 */
final class CrystallizerHudAnchor {

    private static final double PIXELS_PER_BLOCK = 16.0;
    private static final double BLOCK_TOP = 1.0;
    private static final double HALF = 0.5;
    /** The gap between the crystal's side and the panel's near edge, in blocks. */
    private static final double MARGIN = 2.0 / PIXELS_PER_BLOCK;
    /** Horizontal distances below this read as a camera straight above the crystal. */
    private static final double OVERHEAD_EPSILON = 1e-6;

    private CrystallizerHudAnchor() {
    }

    /**
     * The panel's anchor, its bottom center: the crystal's half width, a margin and
     * the panel's half width to the camera's right of the crystal, at the block's
     * top plus half the crystal's height.
     *
     * @param pos            the crystallizer's position
     * @param facing         the face its dial sits on
     * @param crystallized   the crystallized volume, in mB
     * @param camera         the camera's world position
     * @param panelHalfWidth half the panel's width, in blocks
     * @return the anchor in world coordinates
     */
    static Vec3 besideCrystal(BlockPos pos, Direction facing, long crystallized, Vec3 camera,
                              double panelHalfWidth) {
        double[] spot = CrystallizerLayout.modelToWorld(facing, CrystalCluster.BASE_X, CrystalCluster.BASE_Z);
        double[] reach = CrystalCluster.reach(crystallized);
        double centerX = pos.getX() + spot[0] / PIXELS_PER_BLOCK;
        double centerZ = pos.getZ() + spot[1] / PIXELS_PER_BLOCK;
        double lineX = centerX - camera.x;
        double lineZ = centerZ - camera.z;
        double length = Math.sqrt(lineX * lineX + lineZ * lineZ);
        double rightX = length < OVERHEAD_EPSILON ? 1 : -lineZ / length;
        double rightZ = length < OVERHEAD_EPSILON ? 0 : lineX / length;
        double offset = reach[0] / PIXELS_PER_BLOCK + MARGIN + panelHalfWidth;
        double y = pos.getY() + BLOCK_TOP + reach[1] / PIXELS_PER_BLOCK * HALF;
        return new Vec3(centerX + rightX * offset, y, centerZ + rightZ * offset);
    }

    /**
     * The crystal panel's placement, billboarded toward the camera as the crucible's rim panel.
     *
     * @param anchor  the anchor {@link #besideCrystal} answered
     * @param pitch   the animator's billboard pitch
     * @param opacity the animator's fade
     * @return the placement
     */
    static PanelPlacement placement(Vec3 anchor, float pitch, float opacity) {
        return PanelPlacement.onRim(anchor, pitch, opacity);
    }
}
