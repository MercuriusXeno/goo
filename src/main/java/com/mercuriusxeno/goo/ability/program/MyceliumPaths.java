package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.Collection;

/**
 * The connectivity seam Transmit asks: is this block joined to that one
 * through the level's mycelium. A direct search answers it here; the stored
 * mycelium network answers it behind the same seam.
 * transmit-reaches-only-connected-mycelial-blocks-you-touch
 */
public final class MyceliumPaths {

    private MyceliumPaths() {
    }

    /**
     * Whether a path of mycelial blocks joins the destination to any origin.
     *
     * @param level       the level
     * @param origins     the blocks the caster touches
     * @param destination the aimed block
     * @param range       how far from the destination, in blocks, the path may run
     * @return true where a path joins them
     */
    public static boolean connects(Level level, Collection<BlockPos> origins, BlockPos destination, double range) {
        return MyceliumSearch.connects(MycelialBlocks.in(level), origins, destination, range);
    }
}
