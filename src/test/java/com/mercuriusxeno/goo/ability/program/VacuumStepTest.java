package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.throwing.ConeShape;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.network.HoldMarks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Vacuum's drain and reach: every residual cell in the cone loses one layer
 * each time the ticks its distance sets have run, 3 at the eye rising to 7 at
 * the reach's far end, and the hold reaches every residual inside its cone
 * that the eye sees past other residuals
 * (decisions vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave,
 * vacuum-breaks-a-cell-the-tick-the-wave-reaches-it).
 */
class VacuumStepTest {

    private static final double SHIPPED_RANGE = 9;
    /** The cone's apex angle, 30 degrees each side of the look. */
    private static final double CONE = 60;
    /** The eye, looking east along x across the reach. */
    private static final Vec3 EYE = new Vec3(0.5, 2.5, 0.5);
    private static final Vec3 LOOK_EAST = new Vec3(SHIPPED_RANGE, 0, 0);
    /** Looking north, which turns the cone off every cell east of the eye. */
    private static final Vec3 LOOK_NORTH = new Vec3(0, 0, -SHIPPED_RANGE);

    @Nested
    class Shipped {

        // vacuum-sounds-a-suction-wind-and-rising-slurps
        @Test
        void aFartherCellSlurpsHigherFromTheNearestToTheReach() {
            assertTrue(VacuumStep.slurpPitch(0, SHIPPED_RANGE) < VacuumStep.slurpPitch(4, SHIPPED_RANGE));
            assertTrue(VacuumStep.slurpPitch(4, SHIPPED_RANGE) < VacuumStep.slurpPitch(SHIPPED_RANGE, SHIPPED_RANGE));
        }

        // vacuum-breaks-a-cell-the-tick-the-wave-reaches-it: the pace and the memory are the step's data
        @Test
        void theShippedVacuumBehaviorNamesThePaceAndTheMemory() {
            JsonObject behavior = shippedVacuumBehavior();

            assertEquals(3, behavior.get("near_layer_ticks").getAsInt());
            assertEquals(7, behavior.get("far_layer_ticks").getAsInt());
            assertEquals(20, behavior.get("count_memory_ticks").getAsInt());
            assertEquals(new VacuumStep(3, 7, 20), shippedVacuum());
        }

        // vacuum-breaks-a-cell-the-tick-the-wave-reaches-it
        @Test
        void aLayerTakesThreeTicksAtTheEyeSevenAtTheReachAndFiveHalfWay() {
            VacuumStep vacuum = shippedVacuum();

            assertEquals(3, vacuum.layerTicks(0, SHIPPED_RANGE));
            assertEquals(7, vacuum.layerTicks(SHIPPED_RANGE, SHIPPED_RANGE));
            assertEquals(5, vacuum.layerTicks(SHIPPED_RANGE / 2, SHIPPED_RANGE));
        }

        // vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
        @Test
        void theShippedVacuumReachesNineBlocksInASixtyDegreeCone() {
            AbilityDefinition shipped = AbilityJson.decode("void_vacuum");

            assertEquals(SHIPPED_RANGE, shipped.delivery().range());
            assertEquals(CONE, shipped.delivery().coneDegrees());
        }

    }

    /** The shipped vacuum step, decoded from {@code void_vacuum.json} through the step's codec. */
    private static VacuumStep shippedVacuum() {
        AbilityDefinition vacuum = AbilityJson.decode("void_vacuum");
        return vacuum.behaviors().stream().filter(VacuumStep.class::isInstance).map(VacuumStep.class::cast)
                .findFirst().orElseThrow();
    }

    private static JsonObject shippedVacuumBehavior() {
        return JsonParser.parseString(AbilityJson.read("void_vacuum")).getAsJsonObject()
                .getAsJsonArray("behaviors").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(step -> "vacuum".equals(step.get("type").getAsString())).findFirst().orElseThrow();
    }

    /**
     * A reached residual loses one layer each time the ticks its distance
     * sets have run, the first once they have run after the cone reaches it,
     * with no crack drawn; each layer taken throws three break particles, and
     * the break sound and the slurp play once, on the tick the last layer
     * goes; the cone turned off a cell leaves it at the layers it has left and
     * keeps its count for 20 ticks
     * (decisions vacuum-breaks-a-cell-the-tick-the-wave-reaches-it,
     * vacuum-throws-three-break-particles-a-layer,
     * vacuum-sounds-a-suction-wind-and-rising-slurps).
     */
    @Nested
    class Drain {

        /** Four blocks east of the eye, on the look, where a layer takes 4 ticks. */
        private static final BlockPos CELL = new BlockPos(4, 2, 0);
        private static final int CELL_PACE = 4;
        /** Two blocks east of the eye, on the look, where a layer takes 3 ticks. */
        private static final BlockPos NEAR = new BlockPos(2, 2, 0);
        private static final int NEAR_PACE = 3;
        /** Eight blocks east of the eye, on the look, near the reach's far end, where a layer takes 6 ticks. */
        private static final BlockPos FAR = new BlockPos(8, 2, 0);
        private static final int FAR_PACE = 6;
        /** A full cell's layers. */
        private static final int FULL = VolumeCellBlock.MAX_LAYERS;
        /** A shallow cell's layers. */
        private static final int SHALLOW = 3;
        /** The layers the single cell tests lay. */
        private static final int DEEP = 10;
        private static final int PARTICLES_PER_LAYER = 3;
        /** The ticks the cone stays off a cell inside its count's 20 ticks of memory. */
        private static final int OFF_INSIDE_THE_MEMORY = 10;
        /** The ticks the cone stays off a cell past its count's 20 ticks of memory. */
        private static final int OFF_PAST_THE_MEMORY = 25;
        private final VacuumStep vacuum = shippedVacuum();
        private final AtomicInteger held = new AtomicInteger();
        private final Map<BlockPos, BlockState> cells = new HashMap<>();
        private final Map<BlockPos, Integer> emptiedOn = new LinkedHashMap<>();
        private final VacuumCounts counts = new VacuumCounts();

        @Test
        void aReachedCellLosesALayerEachTimeTheTicksItsDistanceSetsHaveRun() {
            ChannelHost host = drainingHost(Map.of(NEAR, WorldGooHosts.worldGoo(FULL),
                    FAR, WorldGooHosts.worldGoo(SHALLOW)), () -> LOOK_EAST);
            List<String> wrong = new ArrayList<>();

            for (int tick = 1; tick <= FULL * NEAR_PACE; tick++) {
                holdFor(host, 1);
                int near = Math.max(0, FULL - tick / NEAR_PACE);
                int far = Math.max(0, SHALLOW - tick / FAR_PACE);
                if (layersAt(NEAR) != near || layersAt(FAR) != far) {
                    wrong.add(tick + ": near " + layersAt(NEAR) + " owed " + near + ", far " + layersAt(FAR)
                            + " owed " + far);
                }
            }

            assertEquals(List.of(), wrong);
            assertEquals(Map.of(FAR, SHALLOW * FAR_PACE, NEAR, FULL * NEAR_PACE), emptiedOn);
        }

        @Test
        void noCellReachesTheGradualBreakAndNoneCracks() {
            ChannelHost host = drainingHost(Map.of(CELL, WorldGooHosts.worldGoo(DEEP)), () -> LOOK_EAST);
            GradualBreak gradualBreak = mock(GradualBreak.class);
            when(host.gradualBreak()).thenReturn(gradualBreak);
            List<Integer> cracked = new ArrayList<>();
            doAnswer(call -> cracked.add(held.get())).when(host).crackBlock(any(), anyInt());

            holdFor(host, DEEP * CELL_PACE);

            assertEquals(DEEP * CELL_PACE, emptiedOn.get(CELL));
            verifyNoInteractions(gradualBreak);
            assertEquals(List.of(), cracked);
        }

        @Test
        void anEmptiedCellLeavesNoCountInTheStore() {
            ChannelHost host = drainingHost(Map.of(CELL, WorldGooHosts.worldGoo(SHALLOW)), () -> LOOK_EAST);

            holdFor(host, SHALLOW * CELL_PACE);

            assertEquals(SHALLOW * CELL_PACE, emptiedOn.get(CELL));
            assertFalse(counts.holds(WorldGooHosts.DIMENSION, CELL));
        }

        @Test
        void eachLayerTakenThrowsThreeBreakParticles() {
            ChannelHost host = drainingHost(Map.of(CELL, WorldGooHosts.worldGoo(DEEP)), () -> LOOK_EAST);

            for (int tick = 1; tick <= DEEP * CELL_PACE; tick++) {
                BlockState before = cells.get(CELL);
                int layers = layersAt(CELL);
                holdFor(host, 1);
                if (tick % CELL_PACE == 0) {
                    verify(host).scatterBreakParticles(CELL, before, PARTICLES_PER_LAYER,
                            (double) layers / VolumeCellBlock.MAX_LAYERS);
                } else {
                    verify(host, never()).scatterBreakParticles(any(), any(), anyInt(), anyDouble());
                }
            }
        }

        @Test
        void theBreakSoundAndTheSlurpPlayOnceOnTheTickTheLastLayerGoes() {
            ChannelHost host = drainingHost(Map.of(CELL, WorldGooHosts.worldGoo(DEEP)), () -> LOOK_EAST);

            for (int tick = 1; tick < DEEP * CELL_PACE; tick++) {
                holdFor(host, 1);
                verify(host, never()).playBreakSound(any(), any());
                verify(host, never()).slurpAt(any(), anyFloat());
            }
            BlockState last = cells.get(CELL);
            holdFor(host, 1);

            verify(host).playBreakSound(CELL, last);
            verify(host).slurpAt(CELL, VacuumStep.slurpPitch(4, SHIPPED_RANGE));
        }

        @Test
        void aCellTheConeTurnsOffInsideTheMemoryTakesItsNextLayerOnceTheTicksLeftHaveRun() {
            AtomicReference<Vec3> look = new AtomicReference<>(LOOK_EAST);
            ChannelHost host = drainingHost(Map.of(CELL, WorldGooHosts.worldGoo(DEEP)), look::get);
            int counted = CELL_PACE + 2;

            holdFor(host, counted);
            look.set(LOOK_NORTH);
            holdFor(host, OFF_INSIDE_THE_MEMORY);

            assertEquals(DEEP - 1, layersAt(CELL));

            look.set(LOOK_EAST);
            holdFor(host, CELL_PACE - 2 - 1);
            assertEquals(DEEP - 1, layersAt(CELL), "the ticks left of the pace have not run");
            holdFor(host, 1);
            assertEquals(DEEP - 2, layersAt(CELL), "the kept count reached the pace");
        }

        @Test
        void aCellTheConeTurnsOffPastTheMemoryCountsFromZeroWhenReachedAgain() {
            AtomicReference<Vec3> look = new AtomicReference<>(LOOK_EAST);
            ChannelHost host = drainingHost(Map.of(CELL, WorldGooHosts.worldGoo(DEEP)), look::get);

            holdFor(host, CELL_PACE + 2);
            look.set(LOOK_NORTH);
            holdFor(host, OFF_PAST_THE_MEMORY);
            look.set(LOOK_EAST);
            holdFor(host, CELL_PACE - 1);

            assertEquals(DEEP - 1, layersAt(CELL), "a count past its memory starts over");
            holdFor(host, 1);
            assertEquals(DEEP - 2, layersAt(CELL));
        }

        /** Ticks the hold on from the tick it last read, as many ticks as named; a verify reads the last tick's calls. */
        private void holdFor(ChannelHost host, int ticks) {
            for (int i = 0; i < ticks; i++) {
                held.incrementAndGet();
                WorldGooHosts.tick(vacuum, host);
            }
        }

        private int layersAt(BlockPos cell) {
            return cells.containsKey(cell) ? VolumeCellBlock.layersOf(cells.get(cell)) : 0;
        }

        /**
         * A host over the cells whose layer take leaves a cell one layer
         * fewer, and empties a cell on its last layer, recording the hold
         * tick it emptied on.
         */
        private ChannelHost drainingHost(Map<BlockPos, BlockState> laid, Supplier<Vec3> look) {
            cells.putAll(laid);
            ChannelHost host = WorldGooHosts.holding(cells, held::get, look, counts);
            doAnswer(call -> {
                BlockPos cell = call.getArgument(0);
                int left = layersAt(cell) - 1;
                if (left > 0) {
                    cells.put(cell, WorldGooHosts.worldGoo(left));
                } else {
                    cells.remove(cell);
                    emptiedOn.put(cell, held.get());
                }
                return null;
            }).when(host).takeLayer(any());
            return host;
        }
    }

    /**
     * Vacuum takes every goo laid on the volume cell, whatever goo type the
     * cell is bound to, with no list of blocks kept by hand, and passes over
     * every other block
     * (decision vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave).
     */
    @Nested
    class Membership {

        private static final BlockPos WORLD_GOO = new BlockPos(4, 2, 0);
        /** Inside the cone beside the line to the world goo, so it blocks no line. */
        private static final BlockPos STONE = new BlockPos(4, 2, 1);
        private final VacuumStep vacuum = shippedVacuum();

        /** The ticks a layer of the world goo four blocks out takes. */
        private static final int PACE = 4;

        @Test
        void aWorldGooOnTheVolumeCellLosesALayerAndStoneStands() {
            AtomicInteger held = new AtomicInteger();
            ChannelHost host = WorldGooHosts.holding(Map.of(WORLD_GOO, WorldGooHosts.worldGoo(WorldGooHosts.LAYERS),
                    STONE, WorldGooHosts.stone()), held::get, () -> LOOK_EAST, new VacuumCounts());
            List<BlockPos> taken = new ArrayList<>();
            doAnswer(call -> taken.add(call.getArgument(0))).when(host).takeLayer(any());

            for (int tick = 1; tick <= PACE; tick++) {
                held.set(tick);
                WorldGooHosts.tick(vacuum, host);
            }

            assertEquals(List.of(WORLD_GOO), taken);
        }
    }

    /**
     * Vacuum's description names every goo in the world as what it clears
     * (decision vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave).
     */
    @Nested
    class Description {

        private static final String LANG = "/assets/goo/lang/en_us.json";
        private static final String DESCRIPTION = "goo.ability.void.vacuum.description";

        @Test
        void theDescriptionNamesEveryGooInTheWorld() throws IOException {
            String description = lang().get(DESCRIPTION).getAsString();

            assertTrue(description.contains("every goo in the world"), description);
        }

        private static JsonObject lang() throws IOException {
            try (InputStream in = VacuumStepTest.class.getResourceAsStream(LANG)) {
                assertNotNull(in, LANG + " should be on the classpath");
                return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
    }

    /**
     * Channel hosts holding a few cells ahead of the eye, every other cell
     * open air, the hold as old as a counter reads and aimed along a look the
     * test can turn.
     */
    private static final class WorldGooHosts {

        /** The layers each world goo cell holds. */
        static final int LAYERS = 6;
        /** The level every cell stands in. */
        static final ResourceKey<Level> DIMENSION = Level.OVERWORLD;

        private WorldGooHosts() {
        }

        /**
         * Ticks the step once on the host, first dropping the calls the host
         * recorded: each tick sweeps the reach's cube of cells, and a long
         * hold's recorded lookups would fill the heap.
         */
        static void tick(VacuumStep vacuum, ChannelHost host) {
            clearInvocations(host);
            vacuum.tick(new StepContext(host, 0, 0));
        }

        /**
         * A host over the cells whose Vacuum counts land in the store, the
         * hold's tick standing as the server's clock.
         */
        static ChannelHost holding(Map<BlockPos, BlockState> cells, IntSupplier held, Supplier<Vec3> look,
                                   VacuumCounts counts) {
            ChannelHost host = mock(ChannelHost.class);
            BlockState air = state(mock(Block.class), Shapes.empty());
            when(host.channelAim()).thenAnswer(call -> Optional.of(new ChannelAim(EYE.add(look.get()), null, CONE,
                    held.getAsInt(), true)));
            when(host.eye()).thenReturn(EYE);
            when(host.holdMarks()).thenReturn(new HoldMarks());
            when(host.stateAt(any())).thenAnswer(call -> cells.getOrDefault(call.getArgument(0), air));
            when(host.countVacuumTick(any(), anyInt())).thenAnswer(call -> counts.countTick(DIMENSION,
                    call.getArgument(0), held.getAsInt(), call.getArgument(1)));
            doAnswer(call -> {
                counts.forget(DIMENSION, call.getArgument(0));
                return null;
            }).when(host).forgetVacuumCount(any());
            return host;
        }

        /** A goo laid on the volume cell, bound to no goo type a tag names, of the layers named. */
        static BlockState worldGoo(int layers) {
            BlockState goo = state(mock(VolumeCellBlock.class), Shapes.empty());
            when(goo.getValue(VolumeCellBlock.LAYERS)).thenReturn(layers);
            return goo;
        }

        static BlockState stone() {
            return state(mock(Block.class), Shapes.block());
        }

        /** A state no test verifies, so it records none of the reach sweep's lookups. */
        private static BlockState state(Block block, VoxelShape collision) {
            BlockState state = mock(BlockState.class, withSettings().stubOnly());
            when(state.getBlock()).thenReturn(block);
            when(state.getCollisionShape(any(), any())).thenReturn(collision);
            return state;
        }
    }

    @Nested
    class Reach {

        @Test
        void aResidualAheadInsideTheConeIsReached() {
            BlockPos ahead = new BlockPos(4, 2, 0);

            assertEquals(List.of(ahead), reached(Set.of(ahead), Set.of()));
        }

        @Test
        void aResidualBehindTheEyeIsNotReached() {
            assertEquals(List.of(), reached(Set.of(new BlockPos(-3, 2, 0)), Set.of()));
        }

        @Test
        void aResidualPastTheReachIsNotReached() {
            // its middle 9.5 blocks out, just past the 9 block reach
            assertEquals(List.of(), reached(Set.of(new BlockPos(10, 2, 0)), Set.of()));
        }

        @Test
        void aResidualOutsideTheConesAngleIsNotReached() {
            // four ahead and three aside stands near 37 degrees off the look, past the cone's 30
            assertEquals(List.of(), reached(Set.of(new BlockPos(4, 2, 3)), Set.of()));
        }

        @Test
        void aResidualBehindASolidBlockIsNotReached() {
            assertEquals(List.of(), reached(Set.of(new BlockPos(6, 2, 0)), Set.of(new BlockPos(3, 2, 0))));
        }

        @Test
        void aResidualBehindAnotherResidualIsReached() {
            BlockPos near = new BlockPos(2, 2, 0);
            BlockPos far = new BlockPos(6, 2, 0);

            assertEquals(List.of(near, far), reached(Set.of(far, near), Set.of()));
        }

        @Test
        void reachedResidualsRunNearestFirst() {
            BlockPos near = new BlockPos(2, 2, 0);
            BlockPos middle = new BlockPos(4, 2, 0);
            BlockPos far = new BlockPos(7, 2, 0);

            assertEquals(List.of(near, middle, far), reached(Set.of(far, near, middle), Set.of()));
        }

        /**
         * A cone wide across and thin up and down reaches a residual off to the side and leaves one the same angle
         * above the axis (decision cone-style-abilities-are-reviewed-for-consistency).
         */
        @Test
        void aWideThinConeReachesAResidualOffToTheSideAndLeavesOneAsFarAbove() {
            // five ahead and two off the axis, about 22 degrees: inside the 30 either side, outside the 4 up
            BlockPos offToTheSide = new BlockPos(5, 2, 2);
            BlockPos asFarAbove = new BlockPos(5, 4, 0);
            assertEquals(List.of(offToTheSide), VacuumStep.residualsInCone(EYE, LOOK_EAST, new ConeShape(60, 8, false),
                    Set.of(offToTheSide, asFarAbove)::contains, pos -> true));
        }

        private static List<BlockPos> reached(Set<BlockPos> residuals, Set<BlockPos> solid) {
            Predicate<BlockPos> passable = pos -> !solid.contains(pos);
            return VacuumStep.residualsInCone(EYE, LOOK_EAST, ConeShape.round(CONE), residuals::contains, passable);
        }
    }
}
