package com.mercuriusxeno.goo.client.hud;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.function.Predicate;

/**
 * Which plexer the panel follows and where its panel stands: the plexer the crosshair
 * rests on, anywhere on the block, while it holds a target, with the panel flat on the
 * plexer's front, the face its cutaway opens on (decision plexer-target-shows-in-a-hud-element),
 * floating in the mouth under the cutaway.
 */
final class PlexerPanelTarget {

    private static final double BLOCK_CENTER = 0.5;
    /** How far past the front face the panel stands, in blocks, so the face never cuts it. */
    private static final double FACE_STAND_OFF = 0.01;
    /**
     * The mouth's height, in blocks: the strip of the front face from the plexer's bottom
     * (model y 0) to the cutaway's bottom (CutawayInteractionHelper's CUTAWAY_MIN_Y, 8/16).
     */
    static final double MOUTH_HEIGHT = 8.0 / 16.0;
    private static final double HALF = 2.0;

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
     * The panel's scale: the one shrink rule held to the mouth's height, so the panel shrinks
     * only when its laid height outgrows the mouth and never stretches to fill it.
     * decision plexer-panel-floats-in-the-mouth
     *
     * @param laidHeight the laid out panel's height in blocks, before any scale
     * @return the mouth's height over the laid height past it, 1 at or under it
     */
    static double scale(double laidHeight) {
        return PanelPainter.shrinkFactor(laidHeight, MOUTH_HEIGHT);
    }

    /**
     * The panel's anchor, its bottom center: a stand-off past the front face's horizontal middle, and
     * low enough that the drawn panel's vertical center stands at the mouth's center, floating between
     * the plexer's bottom and the cutaway's bottom.
     * decision plexer-panel-floats-in-the-mouth
     *
     * @param pos        the plexer's position
     * @param facing     the plexer's FACING
     * @param laidHeight the laid out panel's height in blocks, before any scale
     * @return the panel's anchor
     */
    static Vec3 anchor(BlockPos pos, Direction facing, double laidHeight) {
        Direction front = front(facing);
        double out = BLOCK_CENTER + FACE_STAND_OFF;
        double drawnHeight = laidHeight * scale(laidHeight);
        return new Vec3(pos.getX() + BLOCK_CENTER + front.getStepX() * out,
                pos.getY() + (MOUTH_HEIGHT - drawnHeight) / HALF,
                pos.getZ() + BLOCK_CENTER + front.getStepZ() * out);
    }

    /**
     * @param pos        the plexer's position
     * @param facing     the plexer's FACING
     * @param laidHeight the laid out panel's height in blocks, before any scale
     * @param pitch      the billboard pitch factor [0, 1]
     * @param opacity    the fade factor [0, 1]
     * @return where and how the panel stands on the plexer's front
     */
    static PanelPlacement placement(BlockPos pos, Direction facing, double laidHeight, float pitch, float opacity) {
        return PanelPlacement.onFace(anchor(pos, facing, laidHeight), front(facing), false, pitch, opacity);
    }
}
