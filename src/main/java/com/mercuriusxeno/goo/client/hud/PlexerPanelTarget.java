package com.mercuriusxeno.goo.client.hud;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.function.Predicate;

/**
 * Which plexer the panel follows and where its panel stands: the plexer the crosshair
 * rests on, anywhere on the block, while it holds a target, with the panel over its top
 * center (decision plexer-target-shows-in-a-hud-element).
 */
final class PlexerPanelTarget {

    private static final double BLOCK_CENTER = 0.5;
    /** The block top plus a one-pixel gap under the panel's bottom edge, in blocks. */
    private static final double ABOVE_BLOCK_TOP = 1.0 + 1.0 / 16.0;

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
     * @param pos the plexer's position
     * @return the panel's anchor, its bottom center, a pixel above the block's top center
     */
    static Vec3 anchor(BlockPos pos) {
        return new Vec3(pos.getX() + BLOCK_CENTER, pos.getY() + ABOVE_BLOCK_TOP, pos.getZ() + BLOCK_CENTER);
    }
}
