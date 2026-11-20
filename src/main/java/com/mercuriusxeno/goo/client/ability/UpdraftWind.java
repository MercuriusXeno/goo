package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.UpdraftStep;
import com.mercuriusxeno.goo.block.ability.CellBinding;
import com.mercuriusxeno.goo.block.ability.UpdraftCellBlock;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Updraft's look: frost's wind lines without snowflakes rise from each
 * Updraft cell the client draws up through its column, as tall as the cell
 * holds layers and as wide as the updraft step names, about three a second,
 * never more than one line a tick however many frames draw it.
 * updraft-blob-stands-a-column-of-wind
 * updraft-goo-lifts-one-block-per-layer
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class UpdraftWind {

    /** The game time each standing column last blew a line, so a tick drawn over many frames blows once. */
    private static final Map<BlockPos, Long> BLOWN_AT = new ConcurrentHashMap<>();
    /** Ticks a column goes unseen before its record is dropped. */
    private static final long FORGET_AFTER_TICKS = 40;
    /** The Updraft cells the client has drawn, each blowing up its column every tick it still stands. */
    private static final Set<BlockPos> CELLS = ConcurrentHashMap.newKeySet();
    /**
     * The ticks between a cell's lines, about three a second.
     * operator ruling 2026-11-17: 3-4 lines a second for each cell
     */
    static final int WIND_EVERY_TICKS = 6;
    /** How far from the camera a cell's wind still blows, squared: past it the cell is forgotten until drawn again. */
    private static final double BLOWS_WITHIN_SQUARED = 48.0 * 48.0;

    private UpdraftWind() {
    }

    /**
     * A column's size.
     *
     * @param radius the column's half width in blocks
     * @param height the column's height in blocks
     */
    record Column(double radius, double height) {
    }

    /**
     * The column an Updraft cell blows its wind through: as tall in blocks
     * as the cell holds layers, and as wide as the updraft step names.
     * updraft-goo-lifts-one-block-per-layer
     *
     * @param updraft the updraft step
     * @param layers  the cell's layers
     * @return the column
     */
    static Column columnOf(UpdraftStep updraft, int layers) {
        return new Column(updraft.halfWidth(), UpdraftStep.heightOver(layers));
    }

    /**
     * Notes an Updraft cell the client draws, so its wind blows up its
     * column each tick from then on, while it stands near the camera.
     *
     * @param cell the Updraft cell
     */
    public static void seeCell(BlockPos cell) {
        CELLS.add(cell.immutable());
    }

    /**
     * Blows this tick's line up the column of every Updraft cell the client
     * has drawn, forgetting a cell no longer standing or gone far from the
     * camera.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || CELLS.isEmpty()) {
            return;
        }
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        CELLS.removeIf(cell -> !(level.getBlockState(cell).getBlock() instanceof UpdraftCellBlock)
                || camera.distanceToSqr(Vec3.atCenterOf(cell)) > BLOWS_WITHIN_SQUARED);
        AbilitySyncHandler.ClientAbility ability = AbilitySyncHandler.findAbility(
                CellBinding.UPDRAFT.layingAbility().toString());
        Optional<UpdraftStep> updraft = ability == null ? Optional.empty() : UpdraftStep.in(ability.behaviors());
        updraft.ifPresent(step -> CELLS.forEach(cell -> blowUp(level, cell, step)));
    }

    /**
     * Whether a cell blows its line on this tick: once in every
     * {@value #WIND_EVERY_TICKS} ticks, at a tick its position picks, so the
     * cells of a patch blow at their own times rather than all at once.
     * operator ruling 2026-11-17: 3-4 lines a second for each cell
     *
     * @param cell     the Updraft cell
     * @param gameTime the game time
     * @return true on the cell's tick of the cadence
     */
    static boolean blowsOnCadence(BlockPos cell, long gameTime) {
        return Math.floorMod(gameTime + cell.hashCode(), WIND_EVERY_TICKS) == 0;
    }

    /**
     * Blows one line up a cell's column from its top, on the cell's tick of
     * the cadence, the first time the cell is seen that tick.
     *
     * @param level the client level
     * @param cell  the Updraft cell
     * @param step  the updraft step sizing its column
     */
    private static void blowUp(ClientLevel level, BlockPos cell, UpdraftStep step) {
        long now = level.getGameTime();
        if (!blowsOnCadence(cell, now) || !blowsNow(cell, now)) {
            return;
        }
        BlockState state = level.getBlockState(cell);
        int layers = VolumeCellBlock.layersOf(state);
        Column column = columnOf(step, layers);
        Vec3 top = Vec3.atBottomCenterOf(cell).add(0, (double) layers / VolumeCellBlock.MAX_LAYERS, 0);
        WindLines.CLIENT.rise(level.getRandom(), top, column.radius(), column.height(), now);
    }

    /**
     * Whether a column blows its line on this tick: the first time it is
     * seen on a tick, never again on the same tick.
     *
     * @param pos      the column's block
     * @param gameTime the game time
     * @return true the first time this tick
     */
    static boolean blowsNow(BlockPos pos, long gameTime) {
        @Nullable Long last = BLOWN_AT.put(pos, gameTime);
        if (last == null || last != gameTime) {
            BLOWN_AT.values().removeIf(seen -> gameTime - seen > FORGET_AFTER_TICKS);
            return true;
        }
        return false;
    }

    /** Drops every column, as a disconnect does. */
    public static void clear() {
        BLOWN_AT.clear();
        CELLS.clear();
    }
}
