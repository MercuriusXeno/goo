package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooPlacementModifiers;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * The {@code goo:crater_floor} placement filter: it passes a position whose column lies on a crater's floor, read
 * from the same seeded crater noise lunar's terrain sinks its craters from, so a feature it gates lands in the bowls
 * the world drew.
 * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
 */
public final class CraterFloorFilter extends PlacementFilter {

    private static final CraterFloorFilter INSTANCE = new CraterFloorFilter();
    /** The data codec of the filter, which takes no fields. */
    public static final MapCodec<CraterFloorFilter> CODEC = MapCodec.unit(() -> INSTANCE);

    private CraterFloorFilter() {
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
        ServerLevel level = context.getLevel().getLevel();
        return CraterField.seeded(level.getChunkSource().randomState().getOrCreateNoise(CraterField.NOISE))
                .isFloorAt(origin.getX(), origin.getZ());
    }

    @Override
    public PlacementModifierType<?> type() {
        return GooPlacementModifiers.CRATER_FLOOR.get();
    }
}
