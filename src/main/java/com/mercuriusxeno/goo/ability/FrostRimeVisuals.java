package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import java.util.List;

/**
 * Frost visuals, in the style of rock's per-layer dust (decision
 * elemental-explosion-per-type): a flurry of snowflakes across each layer's
 * footprint as it is previewed, then a burst of snowflakes and cold cloud
 * across the layer as it freezes. A frost tunnel plays no burnout explosion,
 * so these carry its moment in place, layer by layer. The strike plays
 * whether or not the layer held anything to freeze, since the chill passes
 * through stone as well.
 */
final class FrostRimeVisuals implements LayerVisuals {

    static final FrostRimeVisuals INSTANCE = new FrostRimeVisuals();

    private static final double BLOCK_CENTER_OFFSET = 0.5;
    private static final int PREVIEW_FLAKES_PER_BLOCK = 2;
    private static final double PREVIEW_SPREAD = 0.3;
    private static final double FLAKE_SPEED = 0.03;
    private static final int STRUCK_FLAKES = 12;
    private static final int STRUCK_CLOUD = 6;
    private static final int FLAKES_PER_FROZEN_BLOCK = 4;
    private static final double RIME_PERP_SPREAD = 0.45;
    private static final double RIME_ALONG_SPREAD = 0.12;
    private static final double CLOUD_SPEED = 0.01;

    private FrostRimeVisuals() {
    }

    @Override
    public void preview(ServerLevel level, BlockPos origin, Direction placedFace,
                        int stepIndex, int stackCount) {
        BlockPos layerCenter = LayerGeometry.layerCenter(origin, placedFace, stepIndex);
        Direction.Axis blastAxis = placedFace.getOpposite().getAxis();
        List<int[]> footprint = ChainFootprint.layerFootprint(stackCount);
        for (int[] offset : footprint) {
            BlockPos cell = LayerGeometry.offsetPerpendicular(layerCenter, blastAxis, offset[0], offset[1]);
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    cell.getX() + BLOCK_CENTER_OFFSET, cell.getY() + BLOCK_CENTER_OFFSET,
                    cell.getZ() + BLOCK_CENTER_OFFSET, PREVIEW_FLAKES_PER_BLOCK,
                    PREVIEW_SPREAD, PREVIEW_SPREAD, PREVIEW_SPREAD, FLAKE_SPEED);
        }
    }

    @Override
    public void onLayerStruck(ServerLevel level, BlockPos origin, Direction placedFace,
                              int stepIndex, int destroyed) {
        BlockPos layerCenter = LayerGeometry.layerCenter(origin, placedFace, stepIndex);
        Direction.Axis blastAxis = placedFace.getOpposite().getAxis();
        double cx = layerCenter.getX() + BLOCK_CENTER_OFFSET;
        double cy = layerCenter.getY() + BLOCK_CENTER_OFFSET;
        double cz = layerCenter.getZ() + BLOCK_CENTER_OFFSET;
        double spreadX = blastAxis == Direction.Axis.X ? RIME_ALONG_SPREAD : RIME_PERP_SPREAD;
        double spreadY = blastAxis == Direction.Axis.Y ? RIME_ALONG_SPREAD : RIME_PERP_SPREAD;
        double spreadZ = blastAxis == Direction.Axis.Z ? RIME_ALONG_SPREAD : RIME_PERP_SPREAD;
        level.sendParticles(ParticleTypes.SNOWFLAKE, cx, cy, cz,
                STRUCK_FLAKES + FLAKES_PER_FROZEN_BLOCK * Math.max(0, destroyed),
                spreadX, spreadY, spreadZ, FLAKE_SPEED);
        level.sendParticles(ParticleTypes.CLOUD, cx, cy, cz, STRUCK_CLOUD,
                spreadX, spreadY, spreadZ, CLOUD_SPEED);
    }
}
