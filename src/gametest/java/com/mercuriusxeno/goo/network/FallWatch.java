package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.throwing.DripFall;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

/**
 * Watches a layer fall: each tick of a watch it reads the layers every
 * watched cell holds and every layer standing in the test, so a test reads
 * the tick a layer leaves its cell, the tick it lands, and that the goo's
 * layers came through the fall whole.
 * falling-layer-lands-when-its-drip-arrives
 * goo-moving-downward-falls-as-drips
 */
final class FallWatch {

    private static final String LANDED_EARLY = "The landing cell %s should hold %d layers until the fall arrives on"
            + " watch tick %d and %d on it, read %s";
    private static final String LEFT_LATE = "The giving cell %s should change on watch tick %d, read %s";
    private static final String LAYERS_LOST = "The test's layers should stand at %d once the fall lands, read %s";

    private final GameTestHelper helper;
    private final List<BlockPos> cells;
    /** Each watched tick's layers, the watched cells' first and every layer standing in the test last. */
    private final List<List<Integer>> ticks = new ArrayList<>();

    private FallWatch(GameTestHelper helper, List<BlockPos> cells) {
        this.helper = helper;
        this.cells = cells;
    }

    /**
     * Starts reading the cells now, then each tick after for a span of ticks.
     *
     * @param helper the gametest helper
     * @param span   the ticks to read after this one
     * @param cells  the cells to read, relative to the test
     * @return the watch
     */
    static FallWatch over(GameTestHelper helper, int span, BlockPos... cells) {
        FallWatch watch = new FallWatch(helper, List.of(cells));
        watch.read();
        for (int tick = 1; tick <= span; tick++) {
            helper.runAfterDelay(tick, watch::read);
        }
        return watch;
    }

    /**
     * The ticks a layer falls from a height to a surface, as the server and the drawn drip reckon it.
     *
     * @param fromY    the height the layer leaves at
     * @param landingY the surface it lands on
     * @return the fall's ticks
     */
    static int ticksBetween(double fromY, double landingY) {
        return DripFall.ticksToFall(fromY - landingY);
    }

    /**
     * Asserts a watched cell first changed on a tick.
     *
     * @param cell the watched cell's place in the watch
     * @param tick the watch tick it should change on
     */
    void assertChangesOn(int cell, int tick) {
        helper.assertTrue(firstChange(cell) == tick, String.format(LEFT_LATE, cells.get(cell), tick, ticks));
    }

    /**
     * Asserts a watched cell holds its first reading until a tick and one layer more on it.
     *
     * @param cell the watched cell's place in the watch
     * @param tick the watch tick the fall should land on
     */
    void assertLandsOn(int cell, int tick) {
        int before = ticks.getFirst().get(cell);
        boolean held = ticks.subList(0, tick).stream().allMatch(read -> read.get(cell) == before);
        boolean landed = ticks.size() > tick && ticks.get(tick).get(cell) == before + 1;
        helper.assertTrue(held && landed, String.format(LANDED_EARLY, cells.get(cell), before, tick, before + 1,
                ticks));
    }

    /**
     * Asserts the layers standing in the test at the watch's end equal those at its start.
     */
    void assertLayersKept() {
        int start = ticks.getFirst().getLast();
        helper.assertTrue(ticks.getLast().getLast() == start, String.format(LAYERS_LOST, start, ticks));
    }

    /**
     * The first watch tick a watched cell's layers differ from its first reading.
     *
     * @param cell the watched cell's place in the watch
     * @return the tick, -1 where it never changed
     */
    int firstChange(int cell) {
        int before = ticks.getFirst().get(cell);
        for (int tick = 1; tick < ticks.size(); tick++) {
            if (ticks.get(tick).get(cell) != before) {
                return tick;
            }
        }
        return -1;
    }

    private void read() {
        List<Integer> read = new ArrayList<>(cells.stream().map(cell -> layersAt(helper.getBlockState(cell))).toList());
        read.add(layersStanding());
        ticks.add(read);
    }

    private int layersStanding() {
        AABB bounds = helper.getBounds();
        int layers = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX - 1, bounds.maxY - 1, bounds.maxZ - 1))) {
            layers += layersAt(helper.getLevel().getBlockState(pos));
        }
        return layers;
    }

    private static int layersAt(BlockState state) {
        return state.getBlock() instanceof VolumeCellBlock ? VolumeCellBlock.layersHeld(state) : 0;
    }
}
