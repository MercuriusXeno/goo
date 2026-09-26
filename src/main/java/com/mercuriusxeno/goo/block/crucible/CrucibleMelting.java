package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Map;

/**
 * Static helpers for the crucible melting pipeline: per-tick heat and drain,
 * ignition spray, boiling effects, and LIT blockstate management. Keeps framework overrides in CrucibleBlockEntity.
 */
final class CrucibleMelting {

    /**
     * Block update flags: notify neighbors + send to clients.
     */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    private CrucibleMelting() {
    }

    /**
     * Instance-level server tick dispatcher.
     *
     * @param be    the crucible block entity
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     */
    static void serverTick(CrucibleBlockEntity be, Level level, BlockPos pos, BlockState state) {
        tickIgnitionSpray(be);
        handleMeltingTick(be, level, pos);
        handleBoilingEffects(be, level, pos);
        be.gasketPusher.tick();

        boolean lit = be.isEnabled() && be.canHeat();
        if (lit != state.getValue(CrucibleBlock.LIT)) {
            if (lit) {
                beginIgnitionSpray(be);
            }
            level.setBlock(pos, state.setValue(CrucibleBlock.LIT, lit), BLOCK_UPDATE_FLAGS);
        }
    }

    /**
     * Per-tick melting: burns one heat tick and drains the pool at the heat's melt rate.
     * Skips if disabled, and burns nothing without a meltable item or heat to buy.
     *
     * @param be    the crucible block entity
     * @param level the current level
     * @param pos   the block position
     */
    private static void handleMeltingTick(CrucibleBlockEntity be, Level level, BlockPos pos) {
        if (!be.isEnabled()) {
            return;
        }
        int meltRate = be.heat.burnMeltTick(hasMeltableItem(be), FuelGrade.configured(),
                GooConfig.COMBO_DRAIN_PER_TICK.get(), be.fuelStock);
        if (meltRate <= 0) {
            return;
        }
        processMeltCycle(be, level, pos, meltRate);
    }

    /**
     * Runs one melt cycle: drain, effects, and cleanup.
     *
     * @param be       the crucible block entity
     * @param level    the current level
     * @param pos      the block position
     * @param meltRate the mB to melt this tick
     */
    private static void processMeltCycle(CrucibleBlockEntity be, Level level, BlockPos pos, int meltRate) {
        drainFromPool(be, meltRate);
        spawnActiveEffects(be, level, pos);
        clearFinishedMeltingItem(be);
        be.syncToClients();
    }

    /**
     * Spawns boiling bubbles, or embers in an empty basin, whenever the crucible can heat,
     * regardless of whether there is an item being melted. This lets players enable
     * boiling at will by pouring fuel goo into goo-filled basins.
     *
     * @param be    the crucible block entity
     * @param level the current level
     * @param pos   the block position
     */
    private static void handleBoilingEffects(CrucibleBlockEntity be, Level level, BlockPos pos) {
        if (!be.isEnabled()) {
            return;
        }
        if (!be.canHeat()) {
            return;
        }
        if (hasMeltableItem(be)) {
            return;
        }
        spawnActiveEffects(be, level, pos);
    }

    /**
     * Begins a sustained single-spark spray when the crucible lights.
     *
     * @param be the crucible block entity
     */
    static void beginIgnitionSpray(CrucibleBlockEntity be) {
        Level level = be.getLevel();
        be.ignitionSprayTicks = CrucibleBlockEntity.IGNITION_BASE_TICKS
                + (level != null ? level.getRandom().nextInt(CrucibleBlockEntity.IGNITION_RANDOM_TICKS) : 0);
    }

    /**
     * Spawns four cardinal sparks per tick while the ignition spray is active.
     *
     * @param be the crucible block entity
     */
    private static void tickIgnitionSpray(CrucibleBlockEntity be) {
        int ticks = be.ignitionSprayTicks;
        if (ticks <= 0) {
            return;
        }
        be.ignitionSprayTicks = ticks - 1;
        Level level = be.getLevel();
        if (level instanceof ServerLevel serverLevel && be.holdsNoGoo()) {
            CrucibleParticleHelper.spawnIgnitionSparks(serverLevel, be.getBlockPos());
        }
    }

    /**
     * Spawns embers while the crucible holds no goo, and goo bubbles once it does.
     *
     * @param be    the crucible block entity
     * @param level the current level
     * @param pos   the block position
     */
    private static void spawnActiveEffects(CrucibleBlockEntity be, Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (be.holdsNoGoo()) {
            CrucibleParticleHelper.spawnEmbers(serverLevel, pos, level.getRandom());
        }
        spawnBubblesIfGooPresent(be, serverLevel, pos);
    }

    /**
     * Spawns bubbles in the reservoir's largest type from the surface the renderer
     * draws, none while the reservoir is empty (decision reservoir-volume-drives-fill).
     *
     * @param be          the crucible block entity
     * @param serverLevel the server level
     * @param pos         the block position
     */
    private static void spawnBubblesIfGooPresent(CrucibleBlockEntity be, ServerLevel serverLevel, BlockPos pos) {
        CrucibleBasin.DrawnSurface surface = CrucibleBasin.drawnSurface(be.basinVolumes());
        ResourceKey<GooTypeDefinition> dominant = be.reservoir.largestType();
        if (surface == null || dominant == null) {
            return;
        }
        CrucibleParticleHelper.spawnGooBubbles(
                serverLevel, pos, surface, GooColors.get(serverLevel.registryAccess(), dominant),
                serverLevel.getRandom(), be.bubbleHistory);
    }

    /**
     * Returns true if a PMI with remaining goo is loaded.
     *
     * @param be the crucible block entity
     * @return true if meltable item
     */
    static boolean hasMeltableItem(CrucibleBlockEntity be) {
        return !be.meltingItem.isEmpty()
                && !PartiallyMeltedItem.isFullyMelted(be.meltingItem);
    }

    /**
     * Drains the melt rate in mB from the PMI pool, distributed proportionally
     * across all goo types present. Each type receives at least 1 mB per tick
     * (or its remaining volume if less).
     *
     * @param be       the crucible block entity
     * @param meltRate the burning fuel's melt rate in mB/tick
     */
    private static void drainFromPool(CrucibleBlockEntity be, int meltRate) {
        GooContents pmiContents = PartiallyMeltedItem.getContents(be.meltingItem);
        long totalRemaining = pmiContents.totalVolume();
        if (totalRemaining <= 0) {
            return;
        }

        int rate = CrucibleMath.extractionRate(totalRemaining, meltRate);
        Map<ResourceKey<GooTypeDefinition>, Integer> shares = CrucibleMath.computeDrainShares(pmiContents, rate);
        applyDrainShares(be, shares);
    }

    /**
     * Drains each goo type's share from the PMI into the reservoir, taking from
     * the PMI only what the reservoir accepted, so a full type stays in the pool
     * (decision crucible-refuses-past-two-billion).
     *
     * @param be     the crucible block entity
     * @param shares the per-type drain amounts
     */
    private static void applyDrainShares(CrucibleBlockEntity be, Map<ResourceKey<GooTypeDefinition>, Integer> shares) {
        GooContents before = PartiallyMeltedItem.getContents(be.meltingItem);
        GooContents drained = CrucibleCapacity.drainAccepted(before, shares,
                (type, amount) -> be.reservoir.insertGoo(type, amount, false));
        PartiallyMeltedItem.setContents(be.meltingItem, drained);
        be.meltQueue.charge(before.totalVolume() - drained.totalVolume());
    }

    /**
     * Clears the melting item when all goo has been fully drained.
     *
     * @param be the crucible block entity
     */
    private static void clearFinishedMeltingItem(CrucibleBlockEntity be) {
        if (be.meltingItem.isEmpty()) {
            return;
        }
        if (PartiallyMeltedItem.isFullyMelted(be.meltingItem)) {
            be.meltingItem = ItemStack.EMPTY;
            be.meltQueue.clear();
        }
    }
}
