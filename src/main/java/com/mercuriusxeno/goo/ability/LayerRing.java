package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.registry.GooRingParticleOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Sends goo's swirling ring one block in front of a layer about to break,
 * turned to the blast direction and sized to the layer's reach, in a goo
 * type's theme color (decisions goo-swirl-ring-particle,
 * themed-ring-before-every-layer).
 */
final class LayerRing {

    private static final double BLOCK_CENTER_OFFSET = 0.5;

    private LayerRing() {
    }

    /**
     * Sends the ring for one layer.
     *
     * @param level      the server level
     * @param origin     the marker block position
     * @param placedFace the face the marker was attached to
     * @param stepIndex  zero-based layer offset along the blast direction
     * @param reach      the layer's reach in blocks
     * @param color      the theme color as packed RGB
     */
    static void send(ServerLevel level, BlockPos origin, Direction placedFace, int stepIndex, float reach,
                     int color) {
        BlockPos inFront = LayerGeometry.layerCenter(origin, placedFace, stepIndex).relative(placedFace);
        level.sendParticles(new GooRingParticleOptions(placedFace.getOpposite(), reach, color),
                inFront.getX() + BLOCK_CENTER_OFFSET, inFront.getY() + BLOCK_CENTER_OFFSET,
                inFront.getZ() + BLOCK_CENTER_OFFSET, 1, 0.0, 0.0, 0.0, 0.0);
    }
}
