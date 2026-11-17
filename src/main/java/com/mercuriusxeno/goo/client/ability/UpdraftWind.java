package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.LiftStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.UpdraftStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.block.ability.CellBinding;
import com.mercuriusxeno.goo.block.ability.UpdraftCellBlock;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Updraft's and Lift's look: frost's wind lines without snowflakes rise from
 * each Updraft cell the client draws up through its column, as tall as the
 * cell holds layers and as wide as the updraft step names, and a lift
 * prism's wind spirals out around it from its base (LiftWind); each blows
 * one line a tick however many frames draw it.
 * updraft-blob-stands-a-column-of-wind
 * updraft-goo-lifts-one-block-per-layer
 * lift-prism-levitates-the-block-above
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class UpdraftWind {

    /** Every variable an updraft's sizes might read is zero on the client. */
    private static final Variables NONE = name -> OptionalDouble.of(0);
    /** The game time each standing column last blew a line, so a tick drawn over many frames blows once. */
    private static final Map<BlockPos, Long> BLOWN_AT = new ConcurrentHashMap<>();
    /** Ticks a column goes unseen before its record is dropped. */
    private static final long FORGET_AFTER_TICKS = 40;
    /** The Updraft cells the client has drawn, each blowing up its column every tick it still stands. */
    private static final Set<BlockPos> CELLS = ConcurrentHashMap.newKeySet();
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
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        CELLS.removeIf(cell -> !(level.getBlockState(cell).getBlock() instanceof UpdraftCellBlock)
                || camera.distanceToSqr(Vec3.atCenterOf(cell)) > BLOWS_WITHIN_SQUARED);
        AbilitySyncHandler.ClientAbility ability = AbilitySyncHandler.findAbility(
                CellBinding.UPDRAFT.layingAbility().toString());
        Optional<UpdraftStep> updraft = ability == null ? Optional.empty() : UpdraftStep.in(ability.behaviors());
        updraft.ifPresent(step -> CELLS.forEach(cell -> blowUp(level, cell, step)));
    }

    /**
     * Blows one line up a cell's column from its top, the first time the
     * cell is seen this tick.
     *
     * @param level the client level
     * @param cell  the Updraft cell
     * @param step  the updraft step sizing its column
     */
    private static void blowUp(ClientLevel level, BlockPos cell, UpdraftStep step) {
        long now = level.getGameTime();
        if (!blowsNow(cell, now)) {
            return;
        }
        BlockState state = level.getBlockState(cell);
        int layers = VolumeCellBlock.layersOf(state);
        Column column = columnOf(step, layers);
        Vec3 top = Vec3.atBottomCenterOf(cell).add(0, (double) layers / VolumeCellBlock.MAX_LAYERS, 0);
        WindLines.CLIENT.rise(level.getRandom(), top, column.radius(), column.height(), now);
    }

    /**
     * Notes a lift prism as the renderer reads it: it blows this tick's line
     * up the shaft above it, as tall as the shaft stands.
     * lift-prism-levitates-the-block-above
     *
     * @param prism    the prism's block
     * @param combo    the prism's combo, its ability's id
     * @param level    the world the shaft stands in
     * @param gameTime the game time
     */
    public static void seeLift(BlockPos prism, String combo, BlockGetter level, long gameTime) {
        AbilitySyncHandler.ClientAbility ability = AbilitySyncHandler.findAbility(combo);
        Optional<LiftStep> lift = ability == null ? Optional.empty() : liftOf(ability.comboSteps());
        if (lift.isEmpty() || !blowsNow(prism.immutable(), gameTime)) {
            return;
        }
        int height = LiftStep.shaftHeightAbove(level, prism, lift.get().cap().evaluateInt(NONE));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            LiftWind.blow(prism, height, mc.level.getRandom(), gameTime);
        }
    }

    /**
     * The lift step a combo's program runs.
     *
     * @param behaviors the combo's program
     * @return the lift step, or empty for any other combo
     */
    static Optional<LiftStep> liftOf(List<Step> behaviors) {
        return behaviors.stream().flatMap(UpdraftWind::withDescendants).filter(LiftStep.class::isInstance)
                .map(LiftStep.class::cast).findFirst();
    }

    /**
     * Whether a column blows its line on this tick: the first time it is
     * seen on a tick, never again on the same tick.
     *
     * @param pos      the marker's block
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

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(UpdraftWind::withDescendants));
    }

    /** Drops every column, as a disconnect does. */
    public static void clear() {
        BLOWN_AT.clear();
        CELLS.clear();
    }
}
