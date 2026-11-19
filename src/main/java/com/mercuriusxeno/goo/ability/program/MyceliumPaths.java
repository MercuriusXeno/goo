package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.Collection;

/**
 * The connectivity seam Transmit asks: is this block joined to that one
 * through the level's mycelium. The level's stored mycelium network answers it.
 * transmit-reaches-only-connected-mycelial-blocks-you-touch
 * mycelium-networks-are-cached-dags-joined-by-chunk-connectors
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
     * @param range       how far from the destination, in blocks, a chunk the path crosses may stand
     * @return true where a path joins them
     */
    public static boolean connects(Level level, Collection<BlockPos> origins, BlockPos destination, double range) {
        return MyceliumNetworks.of(level).connects(origins, destination, range);
    }
}
