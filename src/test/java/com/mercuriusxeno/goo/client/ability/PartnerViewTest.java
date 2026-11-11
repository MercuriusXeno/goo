package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.PartnerChunkPayload;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.Strategy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The client keeps a streamed far-side chunk in a second level view beside
 * the ClientLevel, reads its blocks back and drops it on the leave message;
 * the portal pass draws a partner's side from that view where the ClientLevel
 * lacks the partner's chunk (decision linked-partner-chunks-stream-to-any-viewer).
 */
class PartnerViewTest {

    private static final int SECTIONS = 4;
    private static final int MIN_SECTION_Y = 0;
    /** A chunk two thousand blocks east, far outside any ClientLevel ring. */
    private static final ChunkPos FAR = new ChunkPos(125, 0);
    /** A block in its second section. */
    private static final BlockPos AT = new BlockPos(FAR.getMinBlockX() + 3, 21, FAR.getMinBlockZ() + 5);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Containers keyed to air, stone and one stand-in biome, all a section's
     * palette needs here: the game's own block-state ids are assigned only
     * as it loads, past a unit test's reach.
     */
    private static PalettedContainerFactory factory() {
        IdMapper<BlockState> states = new IdMapper<>();
        states.addMapping(Blocks.AIR.defaultBlockState(), 0);
        states.addMapping(Blocks.STONE.defaultBlockState(), 1);
        Holder<Biome> plains = Holder.direct(null);
        IdMapper<Holder<Biome>> biomes = new IdMapper<>();
        biomes.addMapping(plains, 0);
        return new PalettedContainerFactory(Strategy.createForBlockStates(states),
                Blocks.AIR.defaultBlockState(), null, Strategy.createForBiomes(biomes), plains, null);
    }

    private static PartnerChunkPayload streamedWithStoneAt(PalettedContainerFactory factory) {
        LevelChunkSection[] sections = new LevelChunkSection[SECTIONS];
        for (int index = 0; index < SECTIONS; index++) {
            sections[index] = new LevelChunkSection(factory);
        }
        sections[1].setBlockState(AT.getX() & 15, AT.getY() & 15, AT.getZ() & 15, Blocks.STONE.defaultBlockState());
        return PartnerChunkPayload.pack(FAR, sections, List.of(), List.of());
    }

    @Test
    void aStreamedChunkIsHeldAndReadBackThenDropped() {
        PalettedContainerFactory factory = factory();
        PartnerView view = new PartnerView();
        view.hold(streamedWithStoneAt(factory), factory, MIN_SECTION_Y, SECTIONS);
        assertEquals(Blocks.STONE.defaultBlockState(), view.getBlockState(AT));
        view.drop(FAR);
        assertEquals(Blocks.AIR.defaultBlockState(), view.getBlockState(AT));
    }

    @Test
    void thePassDrawsFromTheStreamedViewOnlyWhereTheClientLevelLacksThePartner() {
        assertEquals(PartnerView.Source.STREAMED, PartnerView.sourceFor(false, true));
        assertEquals(PartnerView.Source.CLIENT_LEVEL, PartnerView.sourceFor(true, true));
        assertEquals(PartnerView.Source.NONE, PartnerView.sourceFor(false, false));
    }
}
