package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.registry.GooRingParticleOptions;
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

    private static final double BLOCK_CENTER_OFFSET = 0.5;
    private static final int DUST_PARTICLES_PER_BLOCK = 4;
    private static final double DUST_PERP_SPREAD = 0.45;
    private static final double DUST_ALONG_SPREAD = 0.12;
    private static final double DUST_PARTICLE_SPEED = 0.02;

    private RockDustVisuals() {
    }

    @Override
    public void preview(ServerLevel level, BlockPos origin, Direction placedFace,
                        int stepIndex, int stackCount, float reach) {
        BlockPos layerCenter = LayerGeometry.layerCenter(origin, placedFace, stepIndex);
        BlockPos particlePos = layerCenter.relative(placedFace);
        double cx = particlePos.getX() + BLOCK_CENTER_OFFSET;
        double cy = particlePos.getY() + BLOCK_CENTER_OFFSET;
        double cz = particlePos.getZ() + BLOCK_CENTER_OFFSET;
        level.sendParticles(new GooRingParticleOptions(placedFace.getOpposite(), reach, RING_COLOR),
                cx, cy, cz, 1, 0.0, 0.0, 0.0, 0.0);
    }

    @Override
    public void onLayerStruck(ServerLevel level, BlockPos origin, Direction placedFace,
                              int stepIndex, int destroyed) {
        if (destroyed <= 0) {
            return;
        }
        BlockPos layerCenter = LayerGeometry.layerCenter(origin, placedFace, stepIndex);
        Direction.Axis blastAxis = placedFace.getOpposite().getAxis();
        double cx = layerCenter.getX() + BLOCK_CENTER_OFFSET;
        double cy = layerCenter.getY() + BLOCK_CENTER_OFFSET;
        double cz = layerCenter.getZ() + BLOCK_CENTER_OFFSET;
        int count = DUST_PARTICLES_PER_BLOCK * destroyed;
        double spreadX = blastAxis == Direction.Axis.X ? DUST_ALONG_SPREAD : DUST_PERP_SPREAD;
        double spreadY = blastAxis == Direction.Axis.Y ? DUST_ALONG_SPREAD : DUST_PERP_SPREAD;
        double spreadZ = blastAxis == Direction.Axis.Z ? DUST_ALONG_SPREAD : DUST_PERP_SPREAD;
        level.sendParticles(ParticleTypes.DUST_PLUME,
                cx, cy, cz, count, spreadX, spreadY, spreadZ, DUST_PARTICLE_SPEED);
    }
}
