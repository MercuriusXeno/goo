package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * A host whose held channel drinks blocks into the glove (capability
 * {@link HostCapability#SIPHON}): the cone before its eye, the goo each
 * block holds, the unstable goo it burns, and the drink the blocks stream into.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public interface SiphonHost extends StepHost {

    /**
     * Marks the drink held this tick, starting it on the hold's first tick.
     */
    void holdDrink();

    /**
     * The standing blocks inside the cone from the eye along the aim, one
     * block wide at the eye and half a block wider each side than the radius
     * names at mid range, nearest first.
     *
     * @param radius how far past half a block the cone reaches from its axis at mid range, 0 for one block wide
     * @return the blocks in the cone; none outside a held channel
     */
    List<BlockPos> siphonCone(double radius);

    /**
     * @param pos a block in the cone
     * @return the goo the block holds, or null for one the drink cannot take
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
     * Starts a block streaming into the glove: it goes until it is done.
     *
     * @param pos   the block
     * @param goo   the goo it gives
     * @param ticks the ticks it takes, the unstable crucible's time for it over the drink's speed
     */
    void siphon(BlockPos pos, GooContents goo, int ticks);

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
