package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/**
 * Blaze visuals: goo's swirling ring in blaze's heat orange in front of the
 * layer (decision themed-ring-before-every-layer), then flame + lava +
 * ember on struck, selected by name for the progressive area step.
 */
final class BlazeFlameVisuals implements LayerVisuals {

    static final BlazeFlameVisuals INSTANCE = new BlazeFlameVisuals();

    /** Blaze's heat orange, the body color of blaze_explosion.fsh. */
    static final int RING_COLOR = 0xFF8E28;

    private static final double FLAME_PARTICLE_SPEED = 0.05;
    private static final double EMBER_PARTICLE_SPEED = 0.03;
    private static final int FLAME_PARTICLES_PER_BLOCK = 6;
    private static final int LAVA_PARTICLES_PER_BLOCK = 2;
    private static final int EMBER_PARTICLES_PER_BLOCK = 4;
    private static final double FLAME_PERP_SPREAD = 0.45;
    private static final double FLAME_ALONG_SPREAD = 0.12;

    private BlazeFlameVisuals() {
    }

    @Override
    public void preview(ServerLevel level, BlockPos origin, Direction placedFace,
                        int stepIndex, int stackCount, float reach) {
        LayerRing.send(level, origin, placedFace, stepIndex, reach, RING_COLOR);
    }

    @Override
    public void onLayerStruck(ServerLevel level, BlockPos origin, Direction placedFace,
                              int stepIndex, int destroyed) {
        if (destroyed <= 0) {
            return;
        }
        LayerBurst burst = LayerBurst.at(origin, placedFace, stepIndex, FLAME_PERP_SPREAD, FLAME_ALONG_SPREAD);
        burst.send(level, ParticleTypes.FLAME, FLAME_PARTICLES_PER_BLOCK * destroyed, FLAME_PARTICLE_SPEED);
        burst.send(level, ParticleTypes.LAVA, LAVA_PARTICLES_PER_BLOCK * destroyed, 0.0);
        burst.send(level, ParticleTypes.SMALL_FLAME, EMBER_PARTICLES_PER_BLOCK * destroyed, EMBER_PARTICLE_SPEED);
    }
}
