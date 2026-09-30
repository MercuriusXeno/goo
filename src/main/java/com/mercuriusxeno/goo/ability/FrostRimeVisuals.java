package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/**
 * Frost visuals, following rock's ring-then-dust pattern (decision
 * themed-ring-before-every-layer): goo's swirling ring in frost's
 * white-blue in front of each layer as it is previewed, then a burst of
 * snowflakes and cold cloud across the layer, scaled by the blocks it froze.
 * A frost tunnel plays no burnout explosion, so these carry its moment in
 * place, layer by layer.
 */
final class FrostRimeVisuals implements LayerVisuals {

    static final FrostRimeVisuals INSTANCE = new FrostRimeVisuals();

    /** Frost's white-blue, the fog color of frost_explosion.fsh. */
    static final int RING_COLOR = 0xCCE6F5;

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
                        int stepIndex, int stackCount, float reach) {
        LayerRing.send(level, origin, placedFace, stepIndex, reach, RING_COLOR);
    }

    @Override
    public void onLayerStruck(ServerLevel level, BlockPos origin, Direction placedFace,
                              int stepIndex, int destroyed) {
        if (destroyed <= 0) {
            return;
        }
        LayerBurst burst = LayerBurst.at(origin, placedFace, stepIndex, RIME_PERP_SPREAD, RIME_ALONG_SPREAD);
        burst.send(level, ParticleTypes.SNOWFLAKE, STRUCK_FLAKES + FLAKES_PER_FROZEN_BLOCK * destroyed, FLAKE_SPEED);
        burst.send(level, ParticleTypes.CLOUD, STRUCK_CLOUD, CLOUD_SPEED);
    }
}
