package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where the crystal panel stands: centred over the crystal just above its tip,
 * turned toward the player (decision crystal-panel-stands-beside-crystal).
 */
final class CrystallizerHudAnchor {

    private static final double PIXELS_PER_BLOCK = 16.0;
    private static final double BLOCK_TOP = 1.0;
    /** The gap between the crystal's tip and the panel's bottom edge, in blocks. */
    private static final double MARGIN = 1.0 / PIXELS_PER_BLOCK;

    private CrystallizerHudAnchor() {
    }

    /**
     * The panel's anchor, its bottom center: over the crystal's center, a margin above its tip.
     *
     * @param pos          the crystallizer's position
     * @param facing       the face its dial sits on
     * @param crystallized the crystallized volume, in mB
     * @return the anchor in world coordinates
     */
    static Vec3 aboveCrystal(BlockPos pos, Direction facing, long crystallized) {
        double[] spot = CrystallizerLayout.modelToWorld(facing, CrystalCluster.BASE_X, CrystalCluster.BASE_Z);
        double height = CrystalCluster.reach(crystallized)[1] / PIXELS_PER_BLOCK;
        return new Vec3(pos.getX() + spot[0] / PIXELS_PER_BLOCK, pos.getY() + BLOCK_TOP + height + MARGIN,
                pos.getZ() + spot[1] / PIXELS_PER_BLOCK);
    }

    /**
     * The crystal panel's placement, billboarded toward the camera as the crucible's rim panel.
     *
     * @param anchor  the anchor {@link #aboveCrystal} answered
     * @param pitch   the animator's billboard pitch
     * @param opacity the animator's fade
     * @return the placement
     */
    static PanelPlacement placement(Vec3 anchor, float pitch, float opacity) {
        return PanelPlacement.onRim(anchor, pitch, opacity);
    }
}
