package com.mercuriusxeno.goo.lunar;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.synth.Noise;

/**
 * The {@code goo:crater_floor} placement filter: it passes a position whose column lies on a crater's floor, read
 * from the same seeded crater noise lunar's terrain sinks its craters from, so a feature it gates lands in the bowls
 * the world drew, the giant craters' among them.
 * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
 */
public final class CraterFloorFilter implements PlacementFilter {

    private static final CraterFloorFilter INSTANCE = new CraterFloorFilter();
    /** The data codec of the filter, which takes no fields. */
    public static final MapCodec<CraterFloorFilter> CODEC = MapCodec.unit(() -> INSTANCE);

    private CraterFloorFilter() {
    }

    @Override
    public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
        ServerLevel level = context.getLevel().getLevel();
        Noise noise = level.getChunkSource().randomState().getOrCreateNoise(CraterField.NOISE);
        return CraterField.seeded(noise, level.getChunkSource().randomState()).isFloorAt(origin.getX(), origin.getZ())
                || GiantCraterField.seeded(noise, level.getChunkSource().randomState()).reachesBandAt(origin.getX(), origin.getZ(), 0.0,
                CraterField.FLOOR_REACH, 0.0);
    }

    @Override
    public MapCodec<CraterFloorFilter> codec() {
        return CODEC;
    }
}
