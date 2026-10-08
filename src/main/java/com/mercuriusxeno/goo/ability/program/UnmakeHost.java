package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * A host whose blocks an unmake works (capability {@link HostCapability#UNMAKE}):
 * the blocks it holds this run, what the crucible would make of each, how
 * long the unmake has worked each, and the acts of showing and finishing a
 * block's dissolve, and the same for the mobs it holds. A stream's channel
 * holds every block and mob in its cone, a tap's drip the block it lands on.
 * decision unmake-waves-dissolve-by-crucible-cost
 * decision unmake-drip-dissolves-the-block-below
 */
public interface UnmakeHost extends StepHost {

    /**
     * The blocks the unmake works this run.
     *
     * @return the held blocks, empty when the host holds none
     */
    List<BlockPos> unmadeBlocks();

    /**
     * The goo the crucible would melt a held block into.
     *
     * @param pos the held block
     * @return the block's goo value, or null when it holds none
     */
    @Nullable GooValue unmadeValue(BlockPos pos);

    /**
     * Counts this run's work on a held block, a tick of a stream's hold or
     * a tap's drip, and answers the work done on it so far.
     *
     * @param pos    the held block
     * @param needed the work the block takes to melt
     * @return the work done, at least 1
     */
    int countUnmakeWork(BlockPos pos, int needed);

    /**
     * Shows a held block dissolving to its viewers.
     *
     * @param pos      the held block
     * @param fraction the share dissolved, from 0 whole to 1 gone
     */
    void showUnmaking(BlockPos pos, float fraction);

    /**
     * Removes a held block and drops the goo it yields.
     *
     * @param pos   the held block
     * @param yield the goo the block leaves behind
     */
    void unmake(BlockPos pos, GooContents yield);

    /**
     * The mobs the unmake works this run; a host holding only blocks holds none.
     *
     * @return the held mobs
     */
    default List<LivingEntity> unmadeMobs() {
        return List.of();
    }

    /**
     * The loot a held mob would drop, as the crucible would melt it.
     *
     * @param mob the held mob
     * @return the loot, or null when it drops nothing of value
     */
    default UnmakeLoot.@Nullable Loot unmadeLoot(LivingEntity mob) {
        return null;
    }

    /**
     * The unstable fuel's melt exponent, which an unmake's time follows as
     * the unstable crucible's does.
     *
     * @return the exponent
     */
    default double meltExponent() {
        return GooConfig.UNSTABLE_MELT_EXPONENT.get();
    }

    /**
     * Counts this run's work on a held mob and answers the work done on it
     * without a break.
     *
     * @param mob the held mob
     * @return the work done, 1 on the first
     */
    default int countUnmakeWork(LivingEntity mob) {
        return 0;
    }

    /**
     * Shows a held mob dissolving to its viewers.
     *
     * @param mob      the held mob
     * @param fraction the share dissolved, from 0 whole to 1 gone
     */
    default void showUnmaking(LivingEntity mob, float fraction) {
    }

    /**
     * Removes a held mob, its loot never dropping, and drops the goo it yields.
     *
     * @param mob   the held mob
     * @param yield the goo the mob leaves behind
     */
    default void unmake(LivingEntity mob, GooContents yield) {
    }
}
