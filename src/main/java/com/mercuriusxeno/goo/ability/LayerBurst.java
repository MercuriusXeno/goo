package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;

/**
 * Where a struck layer's particles burst: the layer's center, spread wide
 * across the layer's plane and thin along the blast axis, the pattern each
 * type's strike shares (decision themed-ring-before-every-layer).
 *
 * @param x       the burst center's X
 * @param y       the burst center's Y
 * @param z       the burst center's Z
 * @param spreadX the spread along X
 * @param spreadY the spread along Y
 * @param spreadZ the spread along Z
 */
record LayerBurst(double x, double y, double z, double spreadX, double spreadY, double spreadZ) {

    private static final double BLOCK_CENTER_OFFSET = 0.5;

    /**
     * The burst for one layer.
     *
     * @param origin      the marker block position
     * @param placedFace  the face the marker was attached to
     * @param stepIndex   zero-based layer offset along the blast direction
     * @param perpSpread  the spread across the layer's plane
     * @param alongSpread the spread along the blast axis
     * @return the burst
     */
    static LayerBurst at(BlockPos origin, Direction placedFace, int stepIndex, double perpSpread,
                         double alongSpread) {
        BlockPos center = LayerGeometry.layerCenter(origin, placedFace, stepIndex);
        Direction.Axis blastAxis = placedFace.getAxis();
        return new LayerBurst(center.getX() + BLOCK_CENTER_OFFSET, center.getY() + BLOCK_CENTER_OFFSET,
                center.getZ() + BLOCK_CENTER_OFFSET,
                blastAxis == Direction.Axis.X ? alongSpread : perpSpread,
                blastAxis == Direction.Axis.Y ? alongSpread : perpSpread,
                blastAxis == Direction.Axis.Z ? alongSpread : perpSpread);
    }

    /**
     * Sends one particle kind across the burst.
     *
     * @param level    the server level
     * @param particle the particle
     * @param count    how many to send
     * @param speed    their speed
     */
    void send(ServerLevel level, ParticleOptions particle, int count, double speed) {
        level.sendParticles(particle, x, y, z, count, spreadX, spreadY, spreadZ, speed);
    }
}
