package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A Molten cell spends three layers melting the block below it where it
 * holds three or more and the block is meltable, and none otherwise
 * (decisions molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava,
 * molten-spends-three-layers-per-stone-block-priced-at-lava-minus-mundane-block).
 */
class MoltenMeltTest {

    private static final TagKey<Block> MELTABLE = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath("goo", "meltable"));

    @Test
    void aCellSpendsThreeLayersOnlyOverAMeltableBlockWhereItHoldsThree() {
        BlockState stone = mock(BlockState.class);
        when(stone.is(MELTABLE)).thenReturn(true);
        BlockState dirt = mock(BlockState.class);

        assertEquals(List.of(3, 3, 0, 0), List.of(MoltenMelt.layersSpent(3, stone, MELTABLE),
                MoltenMelt.layersSpent(15, stone, MELTABLE), MoltenMelt.layersSpent(2, stone, MELTABLE),
                MoltenMelt.layersSpent(15, dirt, MELTABLE)));
    }
}
