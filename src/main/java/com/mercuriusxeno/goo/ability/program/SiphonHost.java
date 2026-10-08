package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * A host whose held channel drinks blocks into a soup (capability
 * {@link HostCapability#SIPHON}): the face under its cursor, the goo each
 * block holds, the unstable goo it burns, and the soup the blocks stream into.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public interface SiphonHost extends StepHost {

    /**
     * Marks the soup held this tick, starting it on the hold's first tick and
     * moving its ball before the player.
     */
    void holdSoup();

    /**
     * @return whether the soup may start siphoning another block this tick
     */
    boolean readyToSiphon();

    /**
     * The blocks on the face under the cursor, a square about the aimed block
     * one deep, the outer ring first and the aimed block last.
     *
     * @param radius how far the square reaches from the aimed block, 1 for a 3x3
     * @return the standing blocks in reach, in siphoning order; none off a face
     */
    List<BlockPos> siphonFace(int radius);

    /**
     * @param pos a block on the face
     * @return the goo the block holds, or null for one the soup cannot drink
     */
    @Nullable GooValue siphonValue(BlockPos pos);

    /**
     * Burns unstable goo from the player's sources, all of it or none.
     *
     * @param amount the mB to burn
     * @return true when the player held enough and it burned
     */
    boolean burnUnstable(int amount);

    /**
     * Starts a block siphoning into the soup: it goes until it is done.
     *
     * @param pos       the block
     * @param goo       the goo it streams into the soup
     * @param ticks     the ticks it takes
     * @param nextStart the ticks before the soup may start another
     */
    void siphon(BlockPos pos, GooContents goo, int ticks, int nextStart);

    /**
     * @return the unstable crucible's melt exponent, the clock the fuel cost is read on
     */
    default double meltExponent() {
        return GooConfig.UNSTABLE_MELT_EXPONENT.get();
    }

    /**
     * @return the heat ticks one mB of unstable goo buys in the crucible
     */
    default int ticksPerMb() {
        return GooConfig.UNSTABLE_TICKS_PER_MB.get();
    }
}
