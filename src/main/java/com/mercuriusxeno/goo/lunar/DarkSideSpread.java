package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooStructures;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import java.util.Optional;

/**
 * The {@code goo:dark_side_spread} structure placement: vanilla's random spread on each side of the moon, at the
 * spread its JSON names for that side, so a xeno landmark sits closer together on the dark side and more of them
 * place there for each chunk while the light side keeps its own spacing.
 * decision dark-side-is-an-endless-night-of-eldritch-rich-in-void-and-xeno
 */
public final class DarkSideSpread extends StructurePlacement {

    /** The data codec: vanilla's placement fields and a spread for each side. */
    public static final MapCodec<DarkSideSpread> CODEC = RecordCodecBuilder.mapCodec(i -> placementCodec(i)
            .and(Sided.mapCodec(Sided.Spread.CODEC).forGetter(DarkSideSpread::spreads))
            .apply(i, DarkSideSpread::new));

    private final Sided<Sided.Spread> spreads;
    private final Sided<RandomSpreadStructurePlacement> grids;

    // Vanilla's placement codec still reads and hands over the deprecated exclusion zone.
    @SuppressWarnings("deprecation")
    private DarkSideSpread(Vec3i locateOffset, StructurePlacement.FrequencyReductionMethod frequencyReductionMethod,
            float frequency, int salt, Optional<StructurePlacement.ExclusionZone> exclusionZone,
            Sided<Sided.Spread> spreads) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
        this.spreads = spreads;
        this.grids = new Sided<>(grid(spreads.lightSide(), salt), grid(spreads.darkSide(), salt));
    }

    private static RandomSpreadStructurePlacement grid(Sided.Spread spread, int salt) {
        return new RandomSpreadStructurePlacement(spread.spacing(), spread.separation(), RandomSpreadType.LINEAR, salt);
    }

    private Sided<Sided.Spread> spreads() {
        return spreads;
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
        ChunkPos chosen = grids.atChunk(sourceX).getPotentialStructureChunk(state.getLevelSeed(), sourceX, sourceZ);
        return chosen.x() == sourceX && chosen.z() == sourceZ;
    }

    @Override
    public StructurePlacementType<?> type() {
        return GooStructures.DARK_SIDE_SPREAD.get();
    }
}
