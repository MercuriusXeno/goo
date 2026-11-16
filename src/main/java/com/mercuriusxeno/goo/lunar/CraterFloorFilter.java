package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooPlacementModifiers;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

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
    /** The noise lunar's {@code goo:craters} density function rolls its cells from. */
    private static final ResourceKey<NormalNoise.NoiseParameters> CRATER_NOISE = ResourceKey.create(Registries.NOISE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_craters"));

    private CraterFloorFilter() {
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
        return craterField(context.getLevel().getLevel()).isFloorAt(origin.getX(), origin.getZ());
    }

    private static CraterField craterField(ServerLevel level) {
        NormalNoise noise = level.getChunkSource().randomState().getOrCreateNoise(CRATER_NOISE);
        return new CraterField(new DensityFunction.NoiseHolder(
                level.registryAccess().lookupOrThrow(Registries.NOISE).getOrThrow(CRATER_NOISE), noise));
    }

    @Override
    public PlacementModifierType<?> type() {
        return GooPlacementModifiers.CRATER_FLOOR.get();
    }
}
