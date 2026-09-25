package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.item.DepletedBlazeRodItem;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * Static helpers for the crucible melting pipeline: per-tick drain,
 * fuel conversion/consumption, ignition spray, boiling effects, and
 * LIT blockstate management. Keeps framework overrides in CrucibleBlockEntity.
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

        boolean lit = be.isEnabled() && be.hasFuel();
        if (lit != state.getValue(CrucibleBlock.LIT)) {
            level.setBlock(pos, state.setValue(CrucibleBlock.LIT, lit), BLOCK_UPDATE_FLAGS);
        }
    }

    /**
     * Per-tick melting: drains goo from the PMI pool into the reservoir.
     * Skips if disabled, no fuel, or no meltable item.
     *
     * @param be    the crucible block entity
     * @param level the current level
     * @param pos   the block position
     */
    private static void handleMeltingTick(CrucibleBlockEntity be, Level level, BlockPos pos) {
        if (!be.isEnabled()) {
            return;
        }
        if (!hasMeltableItem(be)) {
            return;
        }
        if (!be.hasFuel()) {
            return;
        }
        processMeltCycle(be, level, pos);
    }

    /**
     * Runs one melt cycle: fuel conversion, drain, effects, fuel consumption, and cleanup.
     *
     * @param be    the crucible block entity
     * @param level the current level
     * @param pos   the block position
     */
    private static void processMeltCycle(CrucibleBlockEntity be, Level level, BlockPos pos) {
        convertFreshRodToDepleted(be);
        drainFromPool(be);
        spawnActiveEffects(be, level, pos);
        consumeFuelTick(be);
        clearFinishedMeltingItem(be);
        be.syncToClients();
    }

    /**
     * Spawns boiling bubbles, or embers in an empty basin, whenever the rod is heated,
     * regardless of whether there is an item being melted. This lets players enable
     * boiling at will by inserting a fuel rod into goo-filled basins.
     *
     * @param be    the crucible block entity
     * @param level the current level
     * @param pos   the block position
     */
    private static void handleBoilingEffects(CrucibleBlockEntity be, Level level, BlockPos pos) {
        if (!be.isEnabled()) {
            return;
        }
        if (!be.hasFuel()) {
            return;
        }
        if (hasMeltableItem(be)) {
            return;
        }
        spawnActiveEffects(be, level, pos);
    }

    /**
     * Converts a vanilla blaze rod to a depleted blaze rod on its first burn tick.
     *
     * @param be the crucible block entity
     */
    private static void convertFreshRodToDepleted(CrucibleBlockEntity be) {
        if (be.fuelRod.is(Items.BLAZE_ROD)) {
            be.fuelRod = DepletedBlazeRodItem.createFresh();
            beginIgnitionSpray(be);
        }
    }

    /**
     * Consumes one fuel tick, destroying the rod when fully exhausted.
     *
     * @param be the crucible block entity
     */
    private static void consumeFuelTick(CrucibleBlockEntity be) {
        if (!DepletedBlazeRodItem.consumeTick(be.fuelRod)) {
            be.fuelRod = ItemStack.EMPTY;
        }
    }

    /**
     * Begins a sustained single-spark spray when the blaze rod first contacts the basin.
     *
     * @param be the crucible block entity
     */
    private static void beginIgnitionSpray(CrucibleBlockEntity be) {
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
     * Spawns goo-colored bubbles if there is goo in the reservoir or pool.
     *
     * @param be          the crucible block entity
     * @param serverLevel the server level
     * @param pos         the block position
     */
    private static void spawnBubblesIfGooPresent(CrucibleBlockEntity be, ServerLevel serverLevel, BlockPos pos) {
        long totalGoo = CrucibleBasin.heldVolume(be.getPoolVolume(), be.reservoir.totalVolume());
        if (totalGoo <= 0) {
            return;
        }
        ResourceKey<GooTypeDefinition> dominant = resolveDominantType(be);
        if (dominant == null) {
            return;
        }
        float surfaceY = CrucibleParticleHelper.computeSurfaceY(totalGoo);
        CrucibleParticleHelper.spawnGooBubbles(
                serverLevel, pos, surfaceY, GooColors.get(serverLevel.registryAccess(), dominant), serverLevel.getRandom(),
                be.bubbleHistory);
    }

    /**
     * Returns the dominant goo type from the reservoir, falling back to the PMI pool.
     *
     * @param be the crucible block entity
     * @return the dominant type, or null if no goo is present
     */
    private static @Nullable ResourceKey<GooTypeDefinition> resolveDominantType(CrucibleBlockEntity be) {
        ResourceKey<GooTypeDefinition> dominant = be.reservoir.largestType();
        return dominant != null ? dominant : dominantPoolType(be);
    }

    /**
     * Returns the largest goo type in the PMI pool, or null if empty.
     *
     * @param be the crucible block entity
     * @return the goo type, or null
     */
    private static @Nullable ResourceKey<GooTypeDefinition> dominantPoolType(CrucibleBlockEntity be) {
        if (be.meltingItem.isEmpty()) {
            return null;
        }
        return PartiallyMeltedItem.getContents(be.meltingItem).largestType();
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
     * Drains the crucible's melt rate in mB from the PMI pool, distributed proportionally
     * across all goo types present. Each type receives at least 1 mB per tick
     * (or its remaining volume if less).
     *
     * @param be the crucible block entity
     */
    private static void drainFromPool(CrucibleBlockEntity be) {
        GooContents pmiContents = PartiallyMeltedItem.getContents(be.meltingItem);
        long totalRemaining = pmiContents.totalVolume();
        if (totalRemaining <= 0) {
            return;
        }

        int rate = CrucibleMath.extractionRate(totalRemaining, GooConfig.BLAZE_MELT_RATE.get());
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
        GooContents drained = CrucibleCapacity.drainAccepted(
                PartiallyMeltedItem.getContents(be.meltingItem), shares,
                (type, amount) -> be.reservoir.insertGoo(type, amount, false));
        PartiallyMeltedItem.setContents(be.meltingItem, drained);
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
        }
    }
}
