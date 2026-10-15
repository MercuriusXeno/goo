package com.mercuriusxeno.goo.ability.program;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * A landing that can melt the block it struck into lava (capability
 * {@link HostCapability#MELT}).
 * melt-blob-absorbs-into-lava
 */
public interface MeltHost extends StepHost {

    /**
     * Melts the struck block into a lava source where the tag names it, the
     * blob sinking into it as the stone mingles into the lava.
     *
     * @param meltable the blocks that melt
     * @return true when the block melted
     */
    boolean melt(TagKey<Block> meltable);
}
