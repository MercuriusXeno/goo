package com.mercuriusxeno.goo.block.ability;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How many layers a Molten cell spends melting the block below it into a
 * lava source: three, a fifth of a block, where the cell holds at least
 * three and the block is one the meltable tag names; none otherwise, so the
 * goo stays pooled.
 * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
 * molten-spends-three-layers-per-stone-block-priced-at-lava-minus-mundane-block
 */
public final class MoltenMelt {

    /** The layers melting one block spends, a fifth of a block of molten goo. */
    public static final int LAYERS_PER_BLOCK = 3;

    private MoltenMelt() {
    }

    /**
     * @param layers   the layers the cell holds
     * @param below    the block under the cell
     * @param meltable the blocks that melt
     * @return the layers the melt spends, 0 where nothing melts
     */
    public static int layersSpent(int layers, BlockState below, TagKey<Block> meltable) {
        return layers >= LAYERS_PER_BLOCK && below.is(meltable) ? LAYERS_PER_BLOCK : 0;
    }
}
