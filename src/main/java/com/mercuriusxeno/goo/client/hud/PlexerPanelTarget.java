package com.mercuriusxeno.goo.client.hud;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.function.Predicate;

/**
 * Which plexer the panel follows and where its panel stands: the plexer the crosshair
 * rests on, anywhere on the block, while it holds a target, with the panel flat on the
 * plexer's front, the face its cutaway opens on, under the cutaway
 * (decision plexer-target-shows-in-a-hud-element).
 */
final class PlexerPanelTarget {

    private static final double BLOCK_CENTER = 0.5;
    /** How far past the front face the panel stands, in blocks, so the face never cuts it. */
    private static final double FACE_STAND_OFF = 0.01;
    /**
     * The panel's top edge on the front face, in blocks: just under the cutaway's floor at a
     * half block, so the panel hangs below it at any row count and the cutaway stays visible.
     */
    private static final double PANEL_TOP = 15.0 / 32.0;

    private PlexerPanelTarget() {
    }

    /**
     * @param hitPos            the block the crosshair rests on, or null when it rests on no block
     * @param plexerHoldsTarget whether a block holds a plexer with a target set
     * @return the hit block when it is a plexer holding a target, else null so no panel paints
     */
    static @Nullable BlockPos trackedPos(@Nullable BlockPos hitPos, Predicate<BlockPos> plexerHoldsTarget) {
        return hitPos != null && plexerHoldsTarget.test(hitPos) ? hitPos : null;
    }

    /**
     * The plexer's front: the model's cutaway opens on its north side and the
     * blockstate turns that side away from FACING.
     *
     * @param facing the plexer's FACING
     * @return the face the cutaway opens on
     */
    static Direction front(Direction facing) {
        return facing.getOpposite();
    }

    /**
     * @param pos    the plexer's position
     * @param facing the plexer's FACING
     * @return the panel's anchor, its top center, a stand-off past the front face's horizontal middle
     */
    static Vec3 anchor(BlockPos pos, Direction facing) {
        Direction front = front(facing);
        double out = BLOCK_CENTER + FACE_STAND_OFF;
        return new Vec3(pos.getX() + BLOCK_CENTER + front.getStepX() * out,
                pos.getY() + PANEL_TOP,
                pos.getZ() + BLOCK_CENTER + front.getStepZ() * out);
    }

    /**
     * @param pos     the plexer's position
     * @param facing  the plexer's FACING
     * @param pitch   the billboard pitch factor [0, 1]
     * @param opacity the fade factor [0, 1]
     * @return where and how the panel stands on the plexer's front
     */
    static PanelPlacement placement(BlockPos pos, Direction facing, float pitch, float opacity) {
        return PanelPlacement.hangingOnFace(anchor(pos, facing), front(facing), pitch, opacity);
    }
}
