package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/**
 * Rock visuals: goo's swirling ring in rock's tan one block in front of the
 * layer, turned to the blast direction and sized to the layer's reach
 * (decision goo-swirl-ring-particle), then a dust-plume on struck, selected
 * by name for the progressive area step.
 */
final class RockDustVisuals implements LayerVisuals {

    static final RockDustVisuals INSTANCE = new RockDustVisuals();

    /** Rock's tan, the rock explosion's dust color C2A868; the ring shader lifts its highlights toward EAD090. */
    static final int RING_COLOR = 0xC2A868;

    private static final int DUST_PARTICLES_PER_BLOCK = 4;
    private static final double DUST_PERP_SPREAD = 0.45;
    private static final double DUST_ALONG_SPREAD = 0.12;
    private static final double DUST_PARTICLE_SPEED = 0.02;

    private RockDustVisuals() {
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
        LayerBurst.at(origin, placedFace, stepIndex, DUST_PERP_SPREAD, DUST_ALONG_SPREAD)
                .send(level, ParticleTypes.DUST_PLUME, DUST_PARTICLES_PER_BLOCK * destroyed, DUST_PARTICLE_SPEED);
    }
}
