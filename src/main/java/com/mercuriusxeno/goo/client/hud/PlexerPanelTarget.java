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
    /**
     * The panel's bottom edge on the front face, in blocks: low enough that a one-row
     * panel ends under the cutaway's floor at a half block, high enough to clear the hatch.
     */
    private static final double PANEL_BOTTOM = 3.0 / 16.0;

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
     * @return the panel's anchor, its bottom center, on the front face's horizontal middle
     */
    static Vec3 anchor(BlockPos pos, Direction facing) {
        Direction front = front(facing);
        return new Vec3(pos.getX() + BLOCK_CENTER + front.getStepX() * BLOCK_CENTER,
                pos.getY() + PANEL_BOTTOM,
                pos.getZ() + BLOCK_CENTER + front.getStepZ() * BLOCK_CENTER);
    }
}
