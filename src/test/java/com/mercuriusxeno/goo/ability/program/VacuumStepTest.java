package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.network.HoldMarks;
import net.minecraft.core.BlockPos;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Vacuum's break and reach: a residual cell cracks from the first tick of a
 * hold and breaks whole once the ticks its distance from the eye sets have
 * run, 4 at the eye to 8 at the reach's far end, and the hold reaches every
 * residual inside its cone that the eye sees past other residuals
 * (decisions vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave,
 * vacuum-breaks-a-cell-the-tick-the-wave-reaches-it).
 */
class VacuumStepTest {

    private static final double SHIPPED_RANGE = 10;
    /** The cone's apex angle, 30 degrees each side of the look. */
    private static final double CONE = 60;
    private static final double SHIPPED_NEAR_BREAK_TICKS = 4;
    private static final double SHIPPED_FAR_BREAK_TICKS = 8;
    /** The eye, looking east along x across the reach. */
    private static final Vec3 EYE = new Vec3(0.5, 2.5, 0.5);
    private static final Vec3 LOOK_EAST = new Vec3(SHIPPED_RANGE, 0, 0);
    /** Looking north, which turns the cone off every cell east of the eye. */
    private static final Vec3 LOOK_NORTH = new Vec3(0, 0, -SHIPPED_RANGE);

    @Nested
    class Pace {

        // vacuum-sounds-a-suction-wind-and-rising-slurps
        @Test
        void aFartherCellSlurpsHigherFromTheNearestToTheReach() {
            assertTrue(VacuumStep.slurpPitch(0, SHIPPED_RANGE) < VacuumStep.slurpPitch(4, SHIPPED_RANGE));
            assertTrue(VacuumStep.slurpPitch(4, SHIPPED_RANGE) < VacuumStep.slurpPitch(SHIPPED_RANGE, SHIPPED_RANGE));
        }

        // vacuum-breaks-a-cell-the-tick-the-wave-reaches-it: the break ticks at the near and far ends are the step's params
        @Test
        void theShippedVacuumBehaviorNamesTheNearAndFarBreakTicksAlone() {
            JsonObject behavior = shippedVacuumBehavior();

            assertEquals(Set.of("type", "near_break_ticks", "far_break_ticks"), behavior.keySet());
            assertNotNull(shippedVacuum());
        }

        // vacuum-breaks-a-cell-the-tick-the-wave-reaches-it
        // vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
        @Test
        void theShippedVacuumReachesTenBlocksInASixtyDegreeConeAndBreaksInFourToEightTicks() {
            AbilityDefinition shipped = AbilityJson.decode("void_vacuum");
            VacuumStep vacuum = shippedVacuum();

            assertEquals(SHIPPED_RANGE, shipped.delivery().range());
            assertEquals(CONE, shipped.delivery().coneDegrees());
            assertEquals(SHIPPED_RANGE, shipped.area().size());
            assertEquals(CONE, shipped.area().angle());
            assertEquals(SHIPPED_NEAR_BREAK_TICKS, vacuum.nearBreakTicks());
            assertEquals(SHIPPED_FAR_BREAK_TICKS, vacuum.farBreakTicks());
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
     * The hold tick a cell breaks on, counted from the tick the cone first
     * reaches it: its break ticks, scaled evenly from the near value at the
     * eye to the far value at the reach's far end, rounded up to a whole tick.
     */
    private static int breakTickAt(VacuumStep vacuum, double distance) {
        double near = vacuum.nearBreakTicks();
        return (int) Math.ceil(near + (vacuum.farBreakTicks() - near) * distance / SHIPPED_RANGE);
    }

    /**
     * A reached residual cracks from the first tick of the hold and breaks
     * whole on the tick its distance from the eye sets, nearest first, with no
     * exposure before it; the cone turned off it clears its crack and its
     * progress (decision vacuum-breaks-a-cell-the-tick-the-wave-reaches-it).
     * The break throws three break particles a layer
     * (decision vacuum-throws-three-break-particles-a-layer).
     */
    @Nested
    class Break {

        /** Four blocks east of the eye, on the look. */
        private static final BlockPos CELL = new BlockPos(4, 2, 0);
        /** Two blocks east of the eye, on the look. */
        private static final BlockPos NEAR = new BlockPos(2, 2, 0);
        /** Six blocks east of the eye, on the look. */
        private static final BlockPos FAR = new BlockPos(6, 2, 0);
        private final VacuumStep vacuum = shippedVacuum();
        private final AtomicInteger held = new AtomicInteger();
        private final Map<BlockPos, Integer> brokeOn = new LinkedHashMap<>();
        private final List<BlockPos> exposed = new ArrayList<>();

        @Test
        void eachReachedCellBreaksOnTheTickItsDistanceSetsNearestFirstAndNoneIsExposed() {
            ChannelHost host = recordingBreaks(Map.of(NEAR, WorldGooHosts.worldGoo(), FAR, WorldGooHosts.worldGoo()),
                    () -> LOOK_EAST);
            int nearTick = breakTickAt(vacuum, 2);
            int farTick = breakTickAt(vacuum, 6);

            holdFor(host, farTick);

            assertEquals(List.of(NEAR, FAR), List.copyOf(brokeOn.keySet()));
            assertEquals(nearTick, brokeOn.get(NEAR));
            assertEquals(farTick, brokeOn.get(FAR));
            assertEquals(List.of(), exposed);
        }

        @Test
        void aReachedCellCracksAtRisingStagesFromTheFirstTickAndClearsOnTheTickItBreaks() {
            ChannelHost host = recordingBreaks(Map.of(CELL, WorldGooHosts.worldGoo()), () -> LOOK_EAST);
            List<int[]> cracks = recordingCracks(host);
            int breakTick = breakTickAt(vacuum, 4);

            holdFor(host, breakTick);

            assertEquals(breakTick, brokeOn.get(CELL));
            assertEquals(ChannelAim.FIRST_TICK, cracks.getFirst()[0], "the first crack lands on the hold's first tick");
            int[] last = cracks.getLast();
            assertEquals(breakTick, last[0], "the crack clears on the break tick");
            assertEquals(GradualBreak.CLEARED, last[1]);
            List<int[]> rising = cracks.subList(0, cracks.size() - 1);
            for (int i = 0; i < rising.size(); i++) {
                assertTrue(rising.get(i)[1] >= 0 && (i == 0 || rising.get(i)[1] > rising.get(i - 1)[1]),
                        "the stages drawn before the break only rise: " + stagesOf(cracks));
            }
        }

        @Test
        void aCellTheConeTurnsOffHasItsCrackClearedAndBreaksItsWholeBreakTicksAfterItIsReachedAgain() {
            AtomicReference<Vec3> look = new AtomicReference<>(LOOK_EAST);
            ChannelHost host = recordingBreaks(Map.of(CELL, WorldGooHosts.worldGoo()), look::get);
            int breakTicks = breakTickAt(vacuum, 4);

            holdFor(host, breakTicks - 1);
            look.set(LOOK_NORTH);
            holdFor(host, 1);

            verify(host).crackBlock(CELL, GradualBreak.CLEARED);
            assertEquals(Map.of(), brokeOn);

            look.set(LOOK_EAST);
            int reachedAgainOn = held.get() + 1;
            holdFor(host, breakTicks);

            assertEquals(reachedAgainOn + breakTicks - 1, brokeOn.get(CELL));
        }

        @Test
        void aBrokenResidualThrowsThreeBreakParticlesALayer() {
            ChannelHost host = recordingBreaks(Map.of(CELL, WorldGooHosts.worldGoo()), () -> LOOK_EAST);
            BlockState residual = host.stateAt(CELL);

            holdFor(host, breakTickAt(vacuum, 4));

            verify(host).scatterBreakParticles(CELL, residual, WorldGooHosts.LAYERS * 3,
                    (double) WorldGooHosts.LAYERS / VolumeCellBlock.MAX_LAYERS);
        }

        // vacuum-sounds-a-suction-wind-and-rising-slurps
        @Test
        void aTakenResidualSlurpsAtItsCellPitchedByItsDistance() {
            ChannelHost host = recordingBreaks(Map.of(CELL, WorldGooHosts.worldGoo()), () -> LOOK_EAST);

            holdFor(host, breakTickAt(vacuum, 4));

            verify(host).slurpAt(CELL, VacuumStep.slurpPitch(4, SHIPPED_RANGE));
        }

        /** Ticks the hold on from the tick it last read, as many ticks as named; a verify reads the last tick's calls. */
        private void holdFor(ChannelHost host, int ticks) {
            for (int i = 0; i < ticks; i++) {
                held.incrementAndGet();
                WorldGooHosts.tick(vacuum, host);
            }
        }

        /**
         * A host over the cells whose quiet break empties the cell and records
         * the hold tick it broke on, and which records every cell exposed.
         */
        private ChannelHost recordingBreaks(Map<BlockPos, BlockState> laid, Supplier<Vec3> look) {
            Map<BlockPos, BlockState> cells = new HashMap<>(laid);
            ChannelHost host = WorldGooHosts.holding(cells, held::get, look);
            doAnswer(call -> {
                BlockPos cell = call.getArgument(0);
                cells.remove(cell);
                brokeOn.put(cell, held.get());
                return null;
            }).when(host).breakBlockQuietly(any());
            doAnswer(call -> {
                exposed.add(call.getArgument(0));
                return 0f;
            }).when(host).exposeBlock(any(), any(), anyFloat(), anyInt());
            return host;
        }

        /** Records each crack drawn on {@link #CELL} as the hold tick and the stage. */
        private List<int[]> recordingCracks(ChannelHost host) {
            List<int[]> cracks = new ArrayList<>();
            doAnswer(call -> {
                cracks.add(new int[] {held.get(), call.getArgument(1)});
                return null;
            }).when(host).crackBlock(eq(CELL), anyInt());
            return cracks;
        }

        private static List<String> stagesOf(List<int[]> cracks) {
            return cracks.stream().map(crack -> crack[0] + ":" + crack[1]).toList();
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

        @Test
        void aWorldGooOnTheVolumeCellBreaksAndStoneStands() {
            ChannelHost host = WorldGooHosts.holding(Map.of(WORLD_GOO, WorldGooHosts.worldGoo(),
                    STONE, WorldGooHosts.stone()));
            List<BlockPos> broken = new ArrayList<>();
            doAnswer(call -> broken.add(call.getArgument(0))).when(host).breakBlockQuietly(any());

            // a hold as long as the reach's far end takes to break, so every cell in reach breaks within it
            for (int tick = ChannelAim.FIRST_TICK; tick <= breakTickAt(vacuum, SHIPPED_RANGE); tick++) {
                WorldGooHosts.tick(vacuum, host);
            }

            assertEquals(List.of(WORLD_GOO), broken);
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
     * open air, breaking through one hold's real gradual break, the hold as
     * old as a counter reads and aimed along a look the test can turn.
     */
    private static final class WorldGooHosts {

        /** The layers each world goo cell holds. */
        static final int LAYERS = 6;

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

        static ChannelHost holding(Map<BlockPos, BlockState> cells) {
            return holding(cells, () -> ChannelAim.FIRST_TICK, () -> LOOK_EAST);
        }

        static ChannelHost holding(Map<BlockPos, BlockState> cells, IntSupplier held, Supplier<Vec3> look) {
            ChannelHost host = mock(ChannelHost.class);
            BlockState air = state(mock(Block.class), Shapes.empty());
            when(host.channelAim()).thenAnswer(call -> Optional.of(new ChannelAim(EYE.add(look.get()), null, CONE,
                    held.getAsInt(), true)));
            when(host.eye()).thenReturn(EYE);
            when(host.holdMarks()).thenReturn(new HoldMarks());
            when(host.gradualBreak()).thenReturn(new GradualBreak());
            when(host.stateAt(any())).thenAnswer(call -> cells.getOrDefault(call.getArgument(0), air));
            return host;
        }

        /** A goo laid on the volume cell, bound to no goo type a tag names, of a few layers. */
        static BlockState worldGoo() {
            BlockState goo = state(mock(VolumeCellBlock.class), Shapes.empty());
            when(goo.getValue(VolumeCellBlock.LAYERS)).thenReturn(LAYERS);
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
            assertEquals(List.of(), reached(Set.of(new BlockPos(11, 2, 0)), Set.of()));
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

        private static List<BlockPos> reached(Set<BlockPos> residuals, Set<BlockPos> solid) {
            Predicate<BlockPos> passable = pos -> !solid.contains(pos);
            return VacuumStep.residualsInCone(EYE, LOOK_EAST, CONE, residuals::contains, passable);
        }
    }
}
