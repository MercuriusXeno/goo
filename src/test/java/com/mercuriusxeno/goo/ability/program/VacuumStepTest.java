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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Vacuum's wave and reach: a residual cell breaks on a tick its distance from
 * the eye sets, the reach's far end about 10 ticks in, and the hold reaches
 * every residual inside its cone that the eye sees past other residuals
 * (decision vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave).
 */
class VacuumStepTest {

    private static final double SHIPPED_RANGE = 8;
    private static final double CONE = 40;
    /** The eye, looking east along x across the reach. */
    private static final Vec3 EYE = new Vec3(0.5, 2.5, 0.5);
    private static final Vec3 LOOK_EAST = new Vec3(SHIPPED_RANGE, 0, 0);

    @Nested
    class Wave {

        private final double ticksPerBlock = shippedVacuum().ticksPerBlock();

        @Test
        void aFartherCellBreaksOnALaterTick() {
            assertTrue(VacuumStep.breaksOn(2, ticksPerBlock) < VacuumStep.breaksOn(5, ticksPerBlock));
            assertTrue(VacuumStep.breaksOn(5, ticksPerBlock) < VacuumStep.breaksOn(SHIPPED_RANGE, ticksPerBlock));
        }

        // vacuum-sounds-a-suction-wind-and-rising-slurps
        @Test
        void aFartherCellSlurpsHigherFromTheNearestToTheReach() {
            assertTrue(VacuumStep.slurpPitch(0, SHIPPED_RANGE) < VacuumStep.slurpPitch(4, SHIPPED_RANGE));
            assertTrue(VacuumStep.slurpPitch(4, SHIPPED_RANGE) < VacuumStep.slurpPitch(SHIPPED_RANGE, SHIPPED_RANGE));
        }

        // vacuum-breaks-a-cell-the-tick-the-wave-reaches-it: the wave's ticks a block is the step's one param
        @Test
        void theShippedVacuumBehaviorNamesTheWavesTicksABlockAlone() {
            JsonObject behavior = JsonParser.parseString(AbilityJson.read("void_vacuum")).getAsJsonObject()
                    .getAsJsonArray("behaviors").asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(step -> "vacuum".equals(step.get("type").getAsString())).findFirst().orElseThrow();

            assertEquals(Set.of("type", "ticks_per_block"), behavior.keySet());
            assertEquals(behavior.get("ticks_per_block").getAsDouble(), ticksPerBlock);
        }

        private static VacuumStep shippedVacuum() {
            AbilityDefinition vacuum = AbilityJson.decode("void_vacuum");
            return vacuum.behaviors().stream().filter(VacuumStep.class::isInstance).map(VacuumStep.class::cast)
                    .findFirst().orElseThrow();
        }
    }

    /**
     * A reached residual breaks whole on the hold tick its distance from the
     * eye sets, nearest first, with no exposure before it
     * (decision vacuum-breaks-a-cell-the-tick-the-wave-reaches-it), throwing
     * three break particles a layer
     * (decision vacuum-throws-three-break-particles-a-layer).
     */
    @Nested
    class Break {

        private static final double TICKS_PER_BLOCK = 1.25;
        private static final BlockPos CELL = new BlockPos(4, 2, 0);
        /** Two blocks east of the eye, on the look. */
        private static final BlockPos NEAR = new BlockPos(2, 2, 0);
        /** Six blocks east of the eye, on the look. */
        private static final BlockPos FAR = new BlockPos(6, 2, 0);
        private final VacuumStep vacuum = new VacuumStep(TICKS_PER_BLOCK);

        @Test
        void eachReachedCellBreaksOnItsWaveTickNearestFirstAndNoneIsExposed() {
            Map<BlockPos, BlockState> cells = new HashMap<>(Map.of(NEAR, WorldGooHosts.worldGoo(),
                    FAR, WorldGooHosts.worldGoo()));
            AtomicInteger held = new AtomicInteger();
            ChannelHost host = WorldGooHosts.holding(cells, held::get);
            Map<BlockPos, Integer> brokeOn = new LinkedHashMap<>();
            doAnswer(call -> {
                BlockPos cell = call.getArgument(0);
                cells.remove(cell);
                brokeOn.put(cell, held.get());
                return null;
            }).when(host).breakBlockQuietly(any());
            int nearTick = VacuumStep.breaksOn(2, TICKS_PER_BLOCK);
            int farTick = VacuumStep.breaksOn(6, TICKS_PER_BLOCK);

            for (int tick = ChannelAim.FIRST_TICK; tick <= farTick; tick++) {
                held.set(tick);
                vacuum.tick(new StepContext(host, 0, 0));
            }

            assertEquals(List.of(NEAR, FAR), List.copyOf(brokeOn.keySet()));
            assertEquals(nearTick, brokeOn.get(NEAR));
            assertEquals(farTick, brokeOn.get(FAR));
            verify(host, never()).exposeBlock(any(), any(), anyFloat(), anyInt());
        }

        @Test
        void aBrokenResidualThrowsThreeBreakParticlesALayer() {
            ChannelHost host = residualHost();
            BlockState residual = host.stateAt(CELL);

            vacuum.tick(new StepContext(host, 0, 0));

            verify(host).scatterBreakParticles(CELL, residual, WorldGooHosts.LAYERS * 3,
                    (double) WorldGooHosts.LAYERS / VolumeCellBlock.MAX_LAYERS);
        }

        // vacuum-sounds-a-suction-wind-and-rising-slurps
        @Test
        void aTakenResidualSlurpsAtItsCellPitchedByItsDistance() {
            ChannelHost host = residualHost();

            vacuum.tick(new StepContext(host, 0, 0));

            verify(host).slurpAt(CELL, VacuumStep.slurpPitch(4, SHIPPED_RANGE));
        }

        private ChannelHost residualHost() {
            return WorldGooHosts.holding(Map.of(CELL, WorldGooHosts.worldGoo()));
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
        private final VacuumStep vacuum = new VacuumStep(1.25);

        @Test
        void aWorldGooOnTheVolumeCellBreaksAndStoneStands() {
            ChannelHost host = WorldGooHosts.holding(Map.of(WORLD_GOO, WorldGooHosts.worldGoo(),
                    STONE, WorldGooHosts.stone()));

            vacuum.tick(new StepContext(host, 0, 0));

            verify(host).breakBlockQuietly(WORLD_GOO);
            verify(host, never()).breakBlockQuietly(STONE);
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
     * open air, the hold old enough to have reached them or as old as a
     * counter reads.
     */
    private static final class WorldGooHosts {

        /** The layers each world goo cell holds. */
        static final int LAYERS = 6;
        /** A hold old enough for the wave to have crossed the reach. */
        private static final int WAVE_CROSSED = 20;

        private WorldGooHosts() {
        }

        static ChannelHost holding(Map<BlockPos, BlockState> cells) {
            return holding(cells, () -> WAVE_CROSSED);
        }

        static ChannelHost holding(Map<BlockPos, BlockState> cells, IntSupplier held) {
            ChannelHost host = mock(ChannelHost.class);
            BlockState air = state(mock(Block.class), Shapes.empty());
            when(host.channelAim()).thenAnswer(call -> Optional.of(new ChannelAim(EYE.add(LOOK_EAST), null, CONE,
                    held.getAsInt(), true)));
            when(host.eye()).thenReturn(EYE);
            when(host.holdMarks()).thenReturn(new HoldMarks());
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

        private static BlockState state(Block block, VoxelShape collision) {
            BlockState state = mock(BlockState.class);
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
            assertEquals(List.of(), reached(Set.of(new BlockPos(10, 2, 0)), Set.of()));
        }

        @Test
        void aResidualOutsideTheConesAngleIsNotReached() {
            // four ahead and three aside stands near 37 degrees off the look, past the cone's 20
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
