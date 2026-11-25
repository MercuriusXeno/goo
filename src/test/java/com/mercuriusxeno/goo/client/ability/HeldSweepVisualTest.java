package com.mercuriusxeno.goo.client.ability;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.ability.program.HeldSweepPace;
import com.mercuriusxeno.goo.ability.program.HeldSweepPulses;
import com.mercuriusxeno.goo.ability.program.HeldSweepStep;
import com.mercuriusxeno.goo.ability.program.HeldSweepWorld;
import com.mercuriusxeno.goo.client.BufferedPass;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.type.GooColors;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HeldSweepVisual's sonar return: its fronts, the walls they light by the
 * sculker echo's rules drawn as each block's own model quads, and the box
 * face Meteo's scorch draws through it at the box's own bounds rather than
 * the cube's; and Scry's sphere, faces and glisten take the casting goo
 * type's color, read from the bundled type JSON.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 * decision scry-ships-on-astral
 */
class HeldSweepVisualTest {

    private static final AABB BOTTOM_SLAB = new AABB(0, 0, 0, 1, 0.5, 1);
    private static final AABB FENCE_POST = new AABB(0.375, 0, 0.375, 0.625, 1.5, 0.625);
    private static final float TOLERANCE = 0.01f;

    /**
     * The sonar return: every wall a front hits lights at the hit and fades
     * behind the front, fronts overlap, and a let-go front runs on to its
     * range without flicker or snapping back.
     * decision held-sweeps-share-one-fast-shell-visual
     */
    @Nested
    class Echoes {

        /** A world of air over one floor of solid blocks just under the caster's feet. */
        private static final Predicate<BlockPos> OVER_A_FLOOR = pos -> pos.getY() != -1;
        private static final Vec3 CENTER = new Vec3(0.5, 0.9, 0.5);
        private static final double SCRY_RANGE = 96;
        private static final int TINT = 0x8866FF;
        private static final int ROOM = 100_000;
        private static final double BLOCKS_A_TICK = 2.4;
        private static final long HEARD_AT = 100;
        private static final int SCRY_CROSSING_TICKS = 40;

        /**
         * A hit wall holds full brightness for three seconds, then fades
         * slowly over three more (operator ruling 2026-11-06).
         */
        @Test
        void aHitWallGlowsWholeForThreeSecondsThenFadesOverThreeMore() {
            assertEquals(1f, HeldSweepVisual.glowAt(0), 1e-6f);
            assertEquals(1f, HeldSweepVisual.glowAt(60), 1e-6f);
            assertEquals(0.5f, HeldSweepVisual.glowAt(90), 1e-6f);
            assertEquals(0f, HeldSweepVisual.glowAt(120), 0f);
        }

        @Test
        void aWallWhoseGlowReachedNothingLeavesTheRevealSet() {
            HeldSweepPulse pulse = new HeldSweepPulse(new EchoField(CENTER), SCRY_RANGE, BLOCKS_A_TICK, TINT);
            pulse.heard(12, HEARD_AT);
            pulse.advance(HEARD_AT + 1, CubeWalls.of(OVER_A_FLOOR), ROOM);
            assertTrue(!pulse.lit().isEmpty(), "the floor the front crossed lights");

            pulse.fadeOut(HEARD_AT + 1 + 120);

            assertTrue(pulse.lit().isEmpty(), "every lit wall has faded and left");
        }

        @Test
        void aLetGoFrontRunsOnToItsRangeAndNeverSnapsBack() {
            HeldSweepPulse pulse = new HeldSweepPulse(new EchoField(CENTER), SCRY_RANGE, BLOCKS_A_TICK, TINT);
            pulse.heard(24, HEARD_AT);

            assertEquals(24, pulse.drawnRadius(HEARD_AT + 1), 1e-9, "the last radius the server sent");
            assertEquals(24 + BLOCKS_A_TICK, pulse.drawnRadius(HEARD_AT + 2), 1e-9, "a tick on, unheard");
            double drawn = 0;
            for (double now = HEARD_AT; now <= HEARD_AT + 60; now += 0.25) {
                double next = pulse.drawnRadius(now);
                assertTrue(next >= drawn, "never back at " + now);
                drawn = next;
            }
            assertEquals(SCRY_RANGE, drawn, 1e-9, "on to its range");
        }

        /**
         * The shipped Scry's front, heard once as it leaves and then left
         * unheard, draws on each tick of its two second crossing the radius
         * the server strikes mobs at on that tick.
         * decision scry-ring-crosses-its-reach-in-two-seconds
         */
        @Test
        void theShippedScrysDrawnFrontKeepsToTheRadiusTheServerStrikesAt() {
            HeldSweepStep scry = AbilityJson.decode("astral_scry").behaviors().stream()
                    .filter(HeldSweepStep.class::isInstance).map(HeldSweepStep.class::cast).findFirst().orElseThrow();
            HeldSweepPace pace = scry.pace();
            LiveSweeps sweeps = new LiveSweeps();
            HeldSweepPulses.Pulse leaving = new HeldSweepPulses.Pulse(1, HeldSweepWorld.CASTER, HEARD_AT);

            sweeps.hear(scry.sweepOf(leaving, HEARD_AT), HEARD_AT, type -> TINT);
            HeldSweepPulse drawn = sweeps.pulses().get(1);

            for (int age = 1; age <= SCRY_CROSSING_TICKS; age++) {
                assertEquals(pace.radiusAt(age), drawn.drawnRadius(HEARD_AT + age), 1e-5, "age " + age);
            }
        }

        @Test
        void twoPulsesWallsDrawInOneFrameEachAtItsOwnFront() {
            EchoField field = new EchoField(CENTER);
            HeldSweepPulse first = new HeldSweepPulse(field, SCRY_RANGE, BLOCKS_A_TICK, TINT);
            HeldSweepPulse second = new HeldSweepPulse(field, SCRY_RANGE, BLOCKS_A_TICK, TINT);
            first.heard(11 * BLOCKS_A_TICK, HEARD_AT);
            second.heard(BLOCKS_A_TICK, HEARD_AT);
            double frame = HEARD_AT + 1;

            first.advance(frame, CubeWalls.of(OVER_A_FLOOR), ROOM);
            second.advance(frame, CubeWalls.of(OVER_A_FLOOR), ROOM);

            assertFront(first, frame);
            assertFront(second, frame);
        }

        /**
         * A new pulse lights the walls near the caster even while an older
         * front has lit as many walls as the sweep allows far out (the
         * operator saw Scry stop lighting near walls mid-hold).
         */
        @Test
        void aNewPulseLightsNearWallsWhileAnOlderOneHasLitItsMost() {
            EchoField field = new EchoField(CENTER);
            HeldSweepPulse older = new HeldSweepPulse(field, SCRY_RANGE, BLOCKS_A_TICK, TINT);
            HeldSweepPulse newer = new HeldSweepPulse(field, SCRY_RANGE, BLOCKS_A_TICK, TINT);
            older.heard(SCRY_RANGE, HEARD_AT);
            newer.heard(BLOCKS_A_TICK, HEARD_AT);
            Map<Integer, HeldSweepPulse> live = new LinkedHashMap<>();
            live.put(1, older);
            live.put(2, newer);

            HeldSweepVisual.advanceAll(live, HEARD_AT + 1, CubeWalls.of(OVER_A_FLOOR));

            assertEquals(HeldSweepVisual.MOST_BLOCKS, older.lit().size(), "the older front lit its most, far out");
            assertTrue(!newer.lit().isEmpty(), "the newer front still lights the floor beside the caster");
        }

        /**
         * A wall two fronts lit draws once, at the brighter of its two glows,
         * so a later pulse re-lights it rather than stacking brighter (the
         * operator saw walls brighten with each pulse past the first).
         */
        @Test
        void aWallTwoFrontsLitDrawsOnceAtItsBrighterGlow() {
            EchoField field = new EchoField(CENTER);
            LitWalls lit = new LitWalls();
            HeldSweepPulse first = new HeldSweepPulse(field, SCRY_RANGE, BLOCKS_A_TICK, TINT, false, lit);
            HeldSweepPulse second = new HeldSweepPulse(field, SCRY_RANGE, BLOCKS_A_TICK, TINT, false, lit);
            first.heard(4 * BLOCKS_A_TICK, HEARD_AT);
            first.advance(HEARD_AT + 1, CubeWalls.of(OVER_A_FLOOR), ROOM);
            second.heard(4 * BLOCKS_A_TICK, HEARD_AT + 10);
            second.advance(HEARD_AT + 11, CubeWalls.of(OVER_A_FLOOR), ROOM);

            List<HeldSweepVisual.WallGlow> walls = HeldSweepVisual.wallsToDraw(lit, HEARD_AT + 11);

            assertEquals(walls.stream().map(wall -> wall.face().pos()).distinct().count(), walls.size(),
                    "each wall draws once");
            assertTrue(walls.stream().allMatch(wall -> wall.glow() == 1f), "at the fresh hit's glow, not dimmer");
        }

        /** The pulse holds lit walls, the farthest within one tick's travel inside its front. */
        private void assertFront(HeldSweepPulse pulse, double frame) {
            double front = pulse.drawnRadius(frame);
            assertTrue(!pulse.lit().isEmpty(), "the pulse lights the floor it crossed");
            double farthest = pulse.lit().stream().mapToDouble(lit -> lit.face().distance()).max().orElse(-1);
            assertTrue(farthest <= front && farthest > front - BLOCKS_A_TICK,
                    "the farthest lit wall " + farthest + " stands at the front " + front);
        }
    }

    /**
     * The sweep reads blocks by the sculker echo's rules: its front passes
     * through a block that does not fill its cell and lights the wall behind
     * it, lights only a block with a collision shape, and draws each lit
     * block's own model quads on its sides open to sound.
     * decision scry-shows-surfaces-as-the-sculker-echo-does
     */
    @Nested
    class SculkerRules {

        private static final Vec3 CENTER = new Vec3(0.5, 0.9, 0.5);
        /** Between the caster and the wall behind it, a block east of the caster. */
        private static final BlockPos BETWEEN = new BlockPos(3, 0, 0);
        private static final BlockPos BEHIND = new BlockPos(4, 0, 0);
        private static final double RANGE = 8;
        private static final int TINT = 0x8866FF;
        private static final long HEARD_AT = 100;
        private static final int ROOM = HeldSweepVisual.MOST_BLOCKS;

        /** The walls the last pulse a test sent holds lit. */
        private LitWalls lit = new LitWalls();
        private final BlockState air = state(true, false, false);
        private final BlockState stone = state(false, true, true);
        private final BlockState slab = state(false, false, true);
        private final BlockState fence = state(false, false, true);
        private final BlockState stair = state(false, false, true);
        private final BlockState tallGrass = state(false, false, false);

        @Test
        void aFullBlockBehindASlabLightsItsSideFacingTheSlab() {
            List<HeldSweepPulse.LitFace> lit = litBy(Map.of(BETWEEN, slab, BEHIND, stone));

            assertTrue(sidesLitAt(lit, BEHIND).contains(Direction.WEST), "the side behind the slab");
        }

        @Test
        void aFullBlockBehindAFenceLightsItsSideFacingTheFence() {
            List<HeldSweepPulse.LitFace> lit = litBy(Map.of(BETWEEN, fence, BEHIND, stone));

            assertTrue(sidesLitAt(lit, BEHIND).contains(Direction.WEST), "the side behind the fence");
        }

        @Test
        void tallGrassInTheFrontsPathLightsNothing() {
            List<HeldSweepPulse.LitFace> lit = litBy(Map.of(BETWEEN, tallGrass));

            assertTrue(lit.stream().noneMatch(face -> face.face().pos().equals(BETWEEN)), "no face of the grass");
        }

        /** A lit stair, stone below it and west of it, draws its own quads on the four sides open to sound. */
        @Test
        void aLitStairDrawsItsOwnModelQuadsOnItsSidesOpenToSound() {
            BlockPos below = BETWEEN.below();
            BlockPos west = BETWEEN.west();
            BlockGetter level = levelOf(Map.of(BETWEEN, stair, below, stone, west, stone));
            Map<Direction, BakedQuad> sideQuads = new LinkedHashMap<>();
            BlockStateModelPart stairPart = mock(BlockStateModelPart.class);
            for (Direction side : Direction.values()) {
                BakedQuad quad = mock(BakedQuad.class);
                sideQuads.put(side, quad);
                when(stairPart.getQuads(side)).thenReturn(List.of(quad));
            }
            BakedQuad inner = mock(BakedQuad.class);
            when(stairPart.getQuads(null)).thenReturn(List.of(inner));
            SculkerEchoRenderer.EchoWorld world = echoWorld(level, Map.of(BETWEEN, stairPart));
            EchoQuads quads = new EchoQuads();
            pulseThrough(level);
            VertexConsumer consumer = mock(VertexConsumer.class);

            HeldSweepVisual.drawWalls(new PoseStack(), new BufferedPass(consumer, () -> { }), Vec3.ZERO,
                    HeldSweepVisual.wallsToDraw(lit, HEARD_AT + 1), pos -> quads.at(world, pos));

            ArgumentCaptor<BakedQuad> drawn = ArgumentCaptor.forClass(BakedQuad.class);
            verify(consumer, atLeastOnce()).putBakedQuad(any(), drawn.capture(), any());
            List<BakedQuad> expected = List.of(sideQuads.get(Direction.UP), sideQuads.get(Direction.NORTH),
                    sideQuads.get(Direction.SOUTH), sideQuads.get(Direction.EAST), inner);
            assertEquals(expected, quads.at(world, BETWEEN), "the quads EchoQuads collects for the stair");
            assertEquals(expected, drawn.getAllValues(), "the quads the frame submits");
        }

        private List<HeldSweepPulse.LitFace> litBy(Map<BlockPos, BlockState> blocks) {
            return List.copyOf(pulseThrough(levelOf(blocks)).lit());
        }

        /** A pulse whose front crosses its whole range on its first tick, through a level. */
        private HeldSweepPulse pulseThrough(BlockGetter level) {
            lit = new LitWalls();
            HeldSweepPulse pulse = new HeldSweepPulse(new EchoField(CENTER), RANGE, RANGE, TINT, false, lit);
            pulse.heard(RANGE, HEARD_AT);
            pulse.advance(HEARD_AT + 1, WallWorld.of(level, AirSections.NONE), ROOM);
            return pulse;
        }

        private static List<Direction> sidesLitAt(List<HeldSweepPulse.LitFace> lit, BlockPos pos) {
            return lit.stream().filter(face -> face.face().pos().equals(pos)).findFirst()
                    .map(face -> face.face().sides()).orElse(List.of());
        }

        private BlockGetter levelOf(Map<BlockPos, BlockState> blocks) {
            BlockGetter level = mock(BlockGetter.class);
            when(level.getBlockState(any(BlockPos.class))).thenAnswer(call -> blocks.getOrDefault(call.getArgument(0), air));
            return level;
        }

        /** The level's blocks as an echo draw reads them, each block's model the one part a map names, else none. */
        private static SculkerEchoRenderer.EchoWorld echoWorld(BlockGetter level, Map<BlockPos, BlockStateModelPart> parts) {
            return new SculkerEchoRenderer.EchoWorld() {
                @Override
                public BlockState stateAt(BlockPos pos) {
                    return level.getBlockState(pos);
                }

                @Override
                public void collectParts(BlockPos pos, BlockState state, List<BlockStateModelPart> collected) {
                    if (parts.containsKey(pos)) {
                        collected.add(parts.get(pos));
                    }
                }

                @Override
                public boolean passesSound(BlockPos pos) {
                    return SculkerSenses.passesSound(level, pos);
                }
            };
        }

        /**
         * A block's state as the walk reads it: whether it is air, whether
         * its collision shape fills its cell, whether it has one at all.
         */
        private static BlockState state(boolean isAir, boolean full, boolean collides) {
            BlockState state = mock(BlockState.class);
            when(state.isAir()).thenReturn(isAir);
            when(state.isCollisionShapeFullBlock(any(BlockGetter.class), any(BlockPos.class))).thenReturn(full);
            VoxelShape collision = mock(VoxelShape.class);
            when(collision.isEmpty()).thenReturn(!collides);
            when(state.getCollisionShape(any(BlockGetter.class), any(BlockPos.class))).thenReturn(collision);
            VoxelShape shape = mock(VoxelShape.class);
            when(shape.toAabbs()).thenReturn(isAir ? List.of() : List.of(new AABB(0, 0, 0, 1, 1, 1)));
            when(state.getShape(any(BlockGetter.class), any(BlockPos.class))).thenReturn(shape);
            return state;
        }
    }

    /**
     * One pulse tick's and one frame's work over the fixed synthetic world,
     * counted at the seams the fronts walk and the walls draw through: the
     * baseline a cut to the held sweeps' client cost is proven against.
     * decision diagnose-and-correct-the-lag-on-four-sense-abilities
     */
    @Nested
    class WorkCounts {

        private static final double ONE_TICK = 2.4;
        private static final Vec3 BODY = HeldSweepWorld.CASTER.add(0, 0.9, 0);
        private static final int TINT = 0x8866FF;
        private static final long HEARD_AT = 100;
        private static final int ROOM = HeldSweepVisual.MOST_BLOCKS;
        private static final int SKY_FROM = 16;
        private static final int SECTION = 16;
        private static final double SKY_WALK = 30;

        private final HeldSweepWorld world = new HeldSweepWorld();
        private int blockReads;

        private boolean countedIsAir(BlockPos pos) {
            blockReads++;
            return HeldSweepWorld.isAir(pos);
        }

        /** The fixed world with each block read counted, whether the walk asks if it lights or lets the front by. */
        private WallWorld countedWorld() {
            return new WallWorld(pos -> !countedIsAir(pos), this::countedIsAir);
        }

        /**
         * One front's last tick out to a range, on a pulse whose field walked
         * up to it: the shell positions the walk builds and the block reads
         * it makes over them.
         */
        private long[] workOfTheLastTickTo(double range) {
            EchoField field = new EchoField(BODY);
            HeldSweepPulse pulse = new HeldSweepPulse(field, range, ONE_TICK, TINT);
            pulse.heard(range - ONE_TICK, HEARD_AT);
            pulse.advance(HEARD_AT + 1, countedWorld(), ROOM);
            blockReads = 0;
            long walkedBefore = field.shellPositions();
            pulse.heard(range, HEARD_AT + 1);
            pulse.advance(HEARD_AT + 2, countedWorld(), ROOM);
            return new long[] {field.shellPositions() - walkedBefore, blockReads};
        }

        /**
         * The field already holds the most faces it keeps well inside Scry's
         * reach in this world, so the walk builds no shell of the band the
         * front crossed and reads no block of it; commit 96d5c55 built all
         * 270,961 positions of that shell and read none of them.
         */
        @Test
        void oneClientPulseTickAtScrysReachOnAFullFieldWalksNothing() {
            long[] work = workOfTheLastTickTo(HeldSweepHold.SCRY_REACH);

            assertEquals(0, work[0], "shell positions one client front tick builds, out to 96");
            assertEquals(0, work[1], "block reads one client front tick makes, out to 96");
        }

        /** As at Scry's reach, out to the ore sense's radius; commit 96d5c55 built 118,949 positions there. */
        @Test
        void oneClientPulseTickAtTheOreSensesRadiusOnAFullFieldWalksNothing() {
            long[] work = workOfTheLastTickTo(HeldSweepHold.ORE_RADIUS);

            assertEquals(0, work[0], "shell positions one client front tick builds, out to 64");
            assertEquals(0, work[1], "block reads one client front tick makes, out to 64");
        }

        /**
         * A field's walk reads no block of a section that holds only air and
         * finds the same walls as a walk reading every block, since air
         * carries no wall: over the fixed world open to the sky above y 16.
         */
        @Test
        void aWalkPassesOverSectionsOfAirAloneAndFindsTheSameWalls() {
            Predicate<BlockPos> openSky = pos -> pos.getY() >= SKY_FROM || HeldSweepWorld.isAir(pos);
            EchoField everyBlock = new EchoField(BODY);
            everyBlock.reachTo(SKY_WALK, new WallWorld(pos -> !countedRead(openSky, pos), pos -> countedRead(openSky, pos)));
            int readEveryBlock = blockReads;
            blockReads = 0;
            EchoField bySection = new EchoField(BODY);
            bySection.reachTo(SKY_WALK, new WallWorld(pos -> !countedRead(openSky, pos), pos -> countedRead(openSky, pos),
                    (sectionX, sectionY, sectionZ) -> sectionY >= SKY_FROM / SECTION));

            System.out.println("AIR SECTIONS reads every block " + readEveryBlock + ", by section " + blockReads);
            assertEquals(walls(everyBlock), walls(bySection), "the same walls, in the same order");
            assertTrue(blockReads < readEveryBlock, "block reads " + blockReads + " against " + readEveryBlock);
        }

        private boolean countedRead(Predicate<BlockPos> isAir, BlockPos pos) {
            blockReads++;
            return isAir.test(pos);
        }

        private List<BlockPos> walls(EchoField field) {
            List<BlockPos> walls = new java.util.ArrayList<>();
            for (int index = 0; index < field.size(); index++) {
                walls.add(field.face(index).pos());
            }
            return walls;
        }

        /** The faces the field around the caster holds once the first front has walked out to Scry's reach. */
        @Test
        void aFieldWalkedOutToScrysReachHoldsTheMostFacesItKeeps() {
            EchoField field = new EchoField(BODY);
            field.reachTo(HeldSweepHold.SCRY_REACH, CubeWalls.of(HeldSweepWorld::isAir));

            assertEquals(EchoField.MOST_FACES, field.size(), "faces the field keeps");
            assertEquals(HeldSweepHold.SCRY_REACH, field.face(field.size() - 1).distance(), 0.5,
                    "distance of the farthest face kept");
        }

        @Test
        void oneFrameOfATenSecondScryHoldSubmitsItsLitWallsAndFronts() {
            HeldSweepHold hold = HeldSweepHold.scry(world);
            hold.settle(HeldSweepHold.TEN_SECONDS);
            hold.tick();
            long before = hold.quads();

            hold.frame(0);

            assertEquals(7, hold.clientPulses(), "pulses a ten second Scry hold leaves live");
            assertEquals(HeldSweepVisual.MOST_BLOCKS, hold.walls(), "walls one frame draws");
            assertEquals(16_060, hold.quads() - before, "quads one frame submits");
        }

        /**
         * The wall list weighs one hit for each wall it draws, rather than
         * every live pulse's hit on every wall it lit.
         */
        @Test
        void oneFrameOfATenSecondScryHoldWeighsOneHitForEachWallItDraws() {
            HeldSweepHold hold = HeldSweepHold.scry(world);
            hold.settle(HeldSweepHold.TEN_SECONDS);
            hold.tick();

            hold.frame(0);

            assertEquals(hold.walls(), hold.wallListReads(), "lit hits the wall list weighs in one frame");
        }

        @Test
        void oneFrameOfATenSecondOreSenseHoldSubmitsItsLitWallsFrontsAndOre() {
            HeldSweepHold hold = HeldSweepHold.oreSense(world);
            hold.settle(HeldSweepHold.TEN_SECONDS);
            hold.tick();
            long before = hold.quads();

            hold.frame(0);

            assertEquals(7, hold.clientPulses(), "pulses a ten second ore-sense hold leaves live");
            assertEquals(HeldSweepVisual.MOST_BLOCKS, hold.walls(), "walls one frame draws");
            assertEquals(24_760, hold.quads() - before, "quads one frame submits");
        }
    }

    @Test
    void aSlabsTopShowsAtItsHalfHeight() {
        List<RecordingVertexConsumer.Vertex> quad = emit(BOTTOM_SLAB, Direction.UP);

        assertEquals(4, quad.size());
        for (RecordingVertexConsumer.Vertex v : quad) {
            assertEquals(0.5f, v.y(), TOLERANCE);
        }
    }

    @Test
    void aFencePostsSideSpansThePostNotTheCube() {
        List<RecordingVertexConsumer.Vertex> quad = emit(FENCE_POST, Direction.EAST);

        for (RecordingVertexConsumer.Vertex v : quad) {
            assertEquals(0.625f, v.x(), TOLERANCE);
            assertEquals(true, v.z() >= 0.375f - TOLERANCE && v.z() <= 0.625f + TOLERANCE, "z within the post");
        }
        assertEquals(1.5f, quad.stream().map(RecordingVertexConsumer.Vertex::y).max(Float::compare).orElseThrow(),
                TOLERANCE);
    }

    private static List<RecordingVertexConsumer.Vertex> emit(AABB box, Direction side) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        HeldSweepVisual.emitBoxFace(new PoseStack().last(), consumer, box, side, 0xFFFFE628);
        return consumer.vertices();
    }

    /** Scry's look reads in the astral type's color, resolved from the bundled astral and glow JSON. */
    @Nested
    class TintFromTheCastingType {

        private static final String BUNDLED_JSON_FORMAT = "/data/goo/goo/goo_type/%s.json";

        @Test
        void anAstralPingDrawsInTheAstralTypesColorNotGlowYellow() throws IOException {
            ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor = bundledColors();

            int tint = HeldSweepVisual.tint(GooTypes.ASTRAL, typeColor);

            assertEquals(GooColors.get(bundled(GooTypes.ASTRAL)), tint);
            assertNotEquals(GooColors.get(bundled(GooTypes.GLOW)), tint);
        }

        @Test
        void scrysGlistenDrawsInTheAstralTypesColorNotGlowYellow() throws IOException {
            ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor = bundledColors();

            int glisten = AilmentKind.ASTRAL.rgb(typeColor);

            assertEquals(GooColors.get(bundled(GooTypes.ASTRAL)), glisten);
            assertNotEquals(AilmentKind.GLOW.rgb(typeColor), glisten);
        }

        private ToIntFunction<ResourceKey<GooTypeDefinition>> bundledColors() throws IOException {
            Map<ResourceKey<GooTypeDefinition>, GooTypeDefinition> types = Map.of(
                    GooTypes.ASTRAL, bundled(GooTypes.ASTRAL), GooTypes.GLOW, bundled(GooTypes.GLOW));
            return key -> GooColors.get(types.get(key));
        }

        private GooTypeDefinition bundled(ResourceKey<GooTypeDefinition> key) throws IOException {
            String path = BUNDLED_JSON_FORMAT.formatted(key.identifier().getPath());
            try (InputStream in = HeldSweepVisualTest.class.getResourceAsStream(path)) {
                assertNotNull(in, "Bundled JSON missing on classpath: " + path);
                return GooTypeDefinition.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))).getOrThrow();
            }
        }
    }
}
