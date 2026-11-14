package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.IndicatorShowing;
import com.mercuriusxeno.goo.ability.program.BoreDrillShape;
import com.mercuriusxeno.goo.ability.program.BoreStep;
import com.mercuriusxeno.goo.ability.program.BreakPace;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.ability.program.LevelStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.GougeStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The ground shake jitters exactly the blocks a Bore or a Level hands its
 * gradual break, gently, and leaves the rest still (decision
 * only-the-blocks-about-to-break-shake-briefly-and-gently), a held bore and a
 * held Level each play it (decisions
 * bore-shakes-the-blocks-around-the-tunnel-and-never-the-camera,
 * flatten-plays-bores-ground-shake), and a winding-up Gouge shakes the
 * cells it would lift (decision terrakinesis-shakes-the-blocks-before-the-tear).
 */
class GroundShakeTest {

    private static final TagKey<Block> BREAKABLE =
            TagKey.create(Registries.BLOCK, Identifier.parse("goo:bore_breakable"));
    private static final int SAMPLES = 200;
    private static final float SAMPLE_STEP = 0.1f;
    private static final double STILL = 1e-9;
    private static final Vec3 EYE = new Vec3(0.5, 64.5, 0.5);
    private static final Vec3 LOOK_EAST = new Vec3(1, 0, 0);
    private static final double BORE_RANGE = 8;
    private static final double BORE_CONE_DEGREES = 15;
    /** rock_bore.json's point radius. */
    private static final double POINT_RADIUS = 0.1;
    private static final BreakPace PACE = new BreakPace(5, 1.5);
    /** The jitter's amplitude before this change, read from GroundShake.AMPLITUDE at 128a57da7. */
    private static final double PREVIOUS_AMPLITUDE = 0.04;
    /** A hold long past any wind-up. */
    private static final int HELD_LONG = 100;
    private static final GougeStep GOUGE = new GougeStep(6, 1.5, 2.5, 3.5, 1.5, 1000);

    private static ClientAbility ability(Step behavior) {
        return new ClientAbility(Identifier.parse("goo:rock_test"), "test", "", 0, List.of(), List.of(behavior), 0,
                Delivery.of(DeliveryKind.SELF), AbilityBadge.SELF, List.of(), AbilityArea.NONE,
                IndicatorShowing.SELECTED);
    }

    /**
     * The farthest a block's shake carries it from its cell over a run of samples.
     *
     * @param pos  the block
     * @param zone the shake's zone
     * @return the largest offset length sampled
     */
    private static double widestSwing(BlockPos pos, GroundShake.Zone zone) {
        double widest = 0;
        for (int i = 0; i < SAMPLES; i++) {
            widest = Math.max(widest, GroundShake.offsetAt(pos, zone, i * SAMPLE_STEP).length());
        }
        return widest;
    }

    /**
     * The blocks a zone shakes.
     *
     * @param zone the shake's zone
     * @return its cells
     */
    private static Set<BlockPos> shakenIn(GroundShake.Zone zone) {
        return assertInstanceOf(GroundShake.Cells.class, zone).cells();
    }

    @Nested
    class BoreZone {

        /** A row of breakable stone straight ahead of the eye, and one breakable block under the row, off the line. */
        private final Set<BlockPos> stone = Set.of(new BlockPos(1, 64, 0), new BlockPos(2, 64, 0),
                new BlockPos(3, 64, 0), new BlockPos(4, 64, 0), new BlockPos(5, 64, 0), new BlockPos(6, 64, 0),
                new BlockPos(7, 64, 0), new BlockPos(2, 62, 0));
        private final ChannelAim aim = new ChannelAim(EYE.add(LOOK_EAST.scale(BORE_RANGE)), null, BORE_CONE_DEGREES);
        private final Predicate<BlockPos> air = cell -> !stone.contains(cell);
        private final GroundShake.Zone zone = GroundShake.boreZone(EYE, BoreDrillShape.SHIPPED, true, aim, air,
                stone::contains);

        @Test
        void theShakenBlocksAreTheBlocksTheBoreHandsTheGradualBreak() {
            assertEquals(Set.copyOf(BoreStep.progressing(EYE, BoreDrillShape.SHIPPED, true, aim, air,
                    stone::contains)), shakenIn(zone));
        }

        /** decision bore-breaks-the-block-at-the-point-and-its-surroundings */
        @Test
        void theFourBlocksAtACornerPointShakeAsTheBoreBreaksThem() {
            Vec3 corner = new Vec3(0.5, 64.0, 0.0);
            ChannelAim cornerAim = new ChannelAim(corner.add(LOOK_EAST.scale(BORE_RANGE)), null, BORE_CONE_DEGREES);
            Predicate<BlockPos> rock = cell -> cell.getX() >= 1 && cell.getX() <= 7;
            GroundShake.Zone pointZone = GroundShake.boreZone(corner, BoreDrillShape.SHIPPED, true, cornerAim,
                    rock.negate(), rock);
            Set<BlockPos> shaken = shakenIn(pointZone);
            assertEquals(Set.copyOf(BoreStep.progressing(corner, BoreDrillShape.SHIPPED, true, cornerAim,
                    rock.negate(), rock)),
                    shaken);
            assertTrue(shaken.contains(new BlockPos(1, 63, -1)), "the point's block beside the line stays still");
        }

        @Test
        void theAimedBlockShakesFromTheFirstTickWithinTheAmplitude() {
            double swing = widestSwing(new BlockPos(1, 64, 0), zone);
            assertTrue(swing > 0, "the aimed block never moves");
            assertTrue(swing <= GroundShake.AMPLITUDE * Math.sqrt(3), "the aimed block swings past the amplitude");
        }

        @Test
        void aBlockOffTheLineStaysStill() {
            assertEquals(0, zone.strengthAt(new BlockPos(2, 62, 0)), STILL);
            assertEquals(0, zone.strengthAt(new BlockPos(2, 63, 0)), STILL);
        }
    }

    @Nested
    class LevelZone {

        private final ChannelAim.FacePlane floor = new ChannelAim.FacePlane(new BlockPos(0, 63, 0), Direction.UP);
        /** The swath's first layer standing on the floor, every other layer air, and the floor itself. */
        private final Set<BlockPos> ground = Set.of(new BlockPos(-1, 64, -1), new BlockPos(0, 64, -1),
                new BlockPos(1, 64, -1), new BlockPos(1, 64, 0), new BlockPos(1, 64, 1), new BlockPos(0, 64, 1),
                new BlockPos(-1, 64, 1), new BlockPos(-1, 64, 0), new BlockPos(0, 64, 0), new BlockPos(0, 63, 0));
        private final ChannelAim aim = new ChannelAim(Vec3.ZERO, floor);
        private final BlockPos aimed = new BlockPos(0, 63, 0);
        private final GroundShake.Zone zone = GroundShake.levelZone(aim, aimed, ground::contains);

        @Test
        void theShakenBlocksAreTheBlocksLevelHandsTheGradualBreak() {
            assertEquals(Set.copyOf(LevelStep.progressing(aim, aimed, ground::contains)), shakenIn(zone));
        }

        @Test
        void theLastSwathBlockInBreakingOrderShakesFromTheFirstTick() {
            // The layer runs ring in: the middle block comes last.
            assertTrue(widestSwing(new BlockPos(0, 64, 0), zone) > 0, "the swath's middle block never moves");
        }

        @Test
        void theFloorUnderTheSwathAndABlockBesideItStayStill() {
            assertEquals(0, zone.strengthAt(new BlockPos(0, 63, 0)), STILL);
            assertEquals(0, zone.strengthAt(new BlockPos(2, 64, 0)), STILL);
        }
    }

    @Test
    void theJitterIsHalfTheAmplitudeItPlayedAtBefore() {
        // decision only-the-blocks-about-to-break-shake-briefly-and-gently: the ground swung 0.04 at 128a57da7.
        assertEquals(PREVIOUS_AMPLITUDE / 2, GroundShake.AMPLITUDE, STILL);
    }

    @Test
    void theShakingBlocksAreLitAsTheWorldLightsThemWithNoEntityShadeOverIt() {
        // The tesselator bakes the world's face shade in; a pipeline shading by direction again darkens it.
        assertEquals(Identifier.withDefaultNamespace("core/block"),
                GroundShake.renderType().pipeline().getVertexShader());
    }

    @Test
    void levelsDustDiscFloatsAboveEveryShakenFaceSoNoSeamShowsItThrough() {
        // The disc is translucent tan; a shaken face rising through it leaves it showing only at the seams.
        assertTrue(RockExplosionVisual.cursorHeightAboveFace() > GroundShake.highestFaceRise(),
                "disc " + RockExplosionVisual.cursorHeightAboveFace() + " above the face, faces rise "
                        + GroundShake.highestFaceRise());
    }

    @Nested
    class Players {

        /** decision bore-shows-a-big-drill-from-the-glove: Bore's particles come from its vortex's contact alone */
        @Test
        void aBoreShedsNoCrumbsWhileALevelStillDoes() {
            assertFalse(GroundShake.shedsCrumbs(ability(new BoreStep(BREAKABLE, PACE))));
            assertTrue(GroundShake.shedsCrumbs(ability(new LevelStep(BREAKABLE, PACE))));
        }

        @Test
        void aHeldBoreAndAHeldLevelEachShakeTheGround() {
            assertTrue(GroundShake.shakes(ability(new BoreStep(BREAKABLE, PACE)), true, HELD_LONG));
            assertTrue(GroundShake.shakes(ability(new LevelStep(BREAKABLE, PACE)), true, HELD_LONG));
        }

        @Test
        void neitherShakesUnheldAndOtherAbilitiesNever() {
            assertFalse(GroundShake.shakes(ability(new BoreStep(BREAKABLE, PACE)), false, HELD_LONG));
            assertFalse(GroundShake.shakes(ability(new LevelStep(BREAKABLE, PACE)), false, HELD_LONG));
            assertFalse(GroundShake.shakes(ability(mock(Step.class)), true, HELD_LONG));
            assertFalse(GroundShake.shakes(null, true, HELD_LONG));
        }

        @Test
        void gougeShakesWhileItWindsUpAndStopsOnceTheHunkIsHeldOrTheHoldEnds() {
            ClientAbility gouge = ability(GOUGE);
            assertTrue(GroundShake.shakes(gouge, true, 1));
            assertTrue(GroundShake.shakes(gouge, true, GougeStep.WINDUP_TICKS - 1));
            assertFalse(GroundShake.shakes(gouge, true, GougeStep.WINDUP_TICKS));
            assertFalse(GroundShake.shakes(gouge, false, 1));
        }

        @Test
        void gougeShakesNothingWithNoWindUpRunning() {
            // The server publishes no wind-up while a hunk is held or a throw cools down: tick zero.
            assertFalse(GroundShake.shakes(ability(GOUGE), true, 0));
        }
    }

    @Nested
    class GougeZone {

        @Test
        void theZoneShakesEveryCellTheHunkWouldLiftAndNoneBeside() {
            BlockPos struck = new BlockPos(3, 63, 0);
            Set<BlockPos> hunk = Set.of(struck, struck.east(), struck.below());
            GroundShake.Zone zone = GroundShake.cellsZone(hunk);
            for (BlockPos cell : hunk) {
                assertTrue(widestSwing(cell, zone) > 0, cell + " never moves");
            }
            for (BlockPos beside : List.of(struck.west(), struck.above(), struck.north(), struck.east().east())) {
                assertEquals(0, widestSwing(beside, zone), STILL, beside + " moves");
            }
        }
    }

    /**
     * Each zone resolves along the ray its caller hands it, so the local
     * player's shake, handed the main camera's ray, eases with the view on
     * sneak, and Bore's shakes from the eye its drill draws from
     * (decision diagnose-and-correct-eye-aimed-visuals-jumping-on-sneak).
     */
    @Nested
    class AimsFromTheRayHanded {

        /** The feet stand at 64: a standing eye 1.62 above them, a crouched one 1.27. */
        private static final double STANDING_EYE_Y = 65.62;
        private static final double CROUCHED_EYE_Y = 65.27;
        private static final double MARCH = 1e-3;
        private static final double BLOCK_REACH = 4.5;
        private static final Vec3 LOOK_EAST_AND_DOWN = new Vec3(1, -1, 0).normalize();

        private static CameraEye.Sight sightAt(double eyeY, Vec3 look) {
            return new CameraEye.Sight(new Vec3(0.5, eyeY, 0.5), look);
        }

        /**
         * A clip against the cells a predicate holds solid, marched along the line.
         *
         * @param solid whether a cell stops the line
         * @return the clip
         */
        private static CameraEye.ViewClip clipAgainst(Predicate<BlockPos> solid) {
            return (from, to) -> {
                Vec3 line = to.subtract(from);
                for (double along = 0; along <= 1; along += MARCH / line.length()) {
                    Vec3 point = from.add(line.scale(along));
                    BlockPos cell = BlockPos.containing(point);
                    if (solid.test(cell)) {
                        return new BlockHitResult(point, Direction.UP, cell, false);
                    }
                }
                return BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));
            };
        }

        private static Set<Integer> heightsIn(GroundShake.Zone zone) {
            return shakenIn(zone).stream().map(BlockPos::getY).collect(Collectors.toSet());
        }

        @Test
        void theBoreZoneRunsAlongTheRayFromTheOriginHanded() {
            Predicate<BlockPos> rock = cell -> cell.getX() >= 1 && cell.getX() <= 7;
            BoreStep bore = new BoreStep(BREAKABLE, PACE);

            GroundShake.Zone standing = GroundShake.boreZoneAlong(sightAt(STANDING_EYE_Y, LOOK_EAST), bore,
                    true, BORE_RANGE, rock.negate(), rock);
            GroundShake.Zone sunk = GroundShake.boreZoneAlong(sightAt(64.9, LOOK_EAST), bore,
                    true, BORE_RANGE, rock.negate(), rock);

            assertEquals(Set.of(65), heightsIn(standing));
            assertEquals(Set.of(64), heightsIn(sunk));
        }

        @Test
        void theGougeZoneTearsTheCellsWhereTheRayFromTheOriginHandedStrikes() {
            Predicate<BlockPos> wall = cell -> cell.getX() >= 3;
            CameraEye.ViewClip blocks = clipAgainst(wall);

            GroundShake.Zone standing = GroundShake.gougeZoneAlong(sightAt(STANDING_EYE_Y, LOOK_EAST), GOUGE,
                    blocks, wall);
            GroundShake.Zone sunk = GroundShake.gougeZoneAlong(sightAt(64.9, LOOK_EAST), GOUGE, blocks, wall);

            assertEquals(Set.copyOf(GougeStep.hunkCells(new BlockPos(3, 65, 0), GOUGE.radius(), wall)),
                    shakenIn(standing));
            assertEquals(Set.copyOf(GougeStep.hunkCells(new BlockPos(3, 64, 0), GOUGE.radius(), wall)),
                    shakenIn(sunk));
        }

        @Test
        void theFlattenZoneLiesUnderTheCursorTheRayFromTheOriginHandedStrikes() {
            ChannelAim.FacePlane floor = new ChannelAim.FacePlane(new BlockPos(0, 63, 0), Direction.UP);
            Predicate<BlockPos> onTheFloor = cell -> cell.getY() == 64;
            CameraEye.ViewClip blocks = clipAgainst(cell -> cell.getY() < 64);

            GroundShake.Zone standing = flattenZoneFrom(sightAt(STANDING_EYE_Y, LOOK_EAST_AND_DOWN), floor, blocks,
                    onTheFloor);
            GroundShake.Zone crouched = flattenZoneFrom(sightAt(CROUCHED_EYE_Y, LOOK_EAST_AND_DOWN), floor, blocks,
                    onTheFloor);

            assertTrue(shakenIn(standing).contains(new BlockPos(3, 64, 0)));
            assertFalse(shakenIn(standing).contains(new BlockPos(0, 64, 0)));
            assertTrue(shakenIn(crouched).contains(new BlockPos(0, 64, 0)));
            assertFalse(shakenIn(crouched).contains(new BlockPos(3, 64, 0)));
        }

        private static GroundShake.Zone flattenZoneFrom(CameraEye.Sight sight, ChannelAim.FacePlane plane,
                                                        CameraEye.ViewClip blocks, Predicate<BlockPos> breakable) {
            Vec3 cursor = GloveThrowSender.cursorPoints(true, sight, sight, null, BLOCK_REACH, blocks).drawn();
            return GroundShake.flattenZoneUnder(sight.eye(), cursor, plane, breakable);
        }

        /**
         * The shake runs along the camera's sight, which the drill's point
         * lies on wherever the blob it extends from stands, so the shaken
         * block is the one the drill's point lands on
         * (decision bore-drill-point-floats-along-the-crosshair-to-the-aimed-block).
         */
        @Test
        void theBoreZoneShakesTheBlockTheDrillsPointLandsOn() {
            Vec3 camera = new Vec3(0.5, 65.47, 0.5);
            CameraEye.Sight drawnFrom = CameraEye.chosen(true, new CameraEye.Sight(camera, LOOK_EAST),
                    sightAt(CROUCHED_EYE_Y, LOOK_EAST));
            Predicate<BlockPos> rock = cell -> cell.getX() >= 1 && cell.getX() <= 7;

            GroundShake.Zone zone = GroundShake.boreZoneAlong(drawnFrom,
                    new BoreStep(BREAKABLE, PACE, POINT_RADIUS, List.of(), List.of()), true, BORE_RANGE,
                    rock.negate(), rock);

            Vec3 point = BoreDrill.pointOf(drawnFrom, BORE_RANGE, BoreDrillShape.SHIPPED, true, rock.negate(), rock);
            assertEquals(new Vec3(1, 65.47, 0.5), point);
            assertEquals(Set.of(new BlockPos(1, 65, 0)), shakenIn(zone));
        }
    }

    /**
     * A shaking block's copy stands in front of the block, so it draws the
     * crack vanilla shows on the block over itself
     * (decision diagnose-and-remove-flattens-particle-artifacting).
     */
    @Nested
    class Cracks {

        private static final BlockPos CRACKING = new BlockPos(2, 64, 0);
        private static final int STAGE = 4;

        @Test
        void aShakingBlockVanillaCracksDrawsThatCrackOnItsCopy() {
            List<BlockBreakingRenderState> breaking = List.of(
                    new BlockBreakingRenderState(CRACKING.east(), mock(BlockState.class), 1),
                    new BlockBreakingRenderState(CRACKING, mock(BlockState.class), STAGE));
            assertEquals(OptionalInt.of(STAGE), GroundShake.crackStageOn(CRACKING, breaking));
        }

        @Test
        void aShakingBlockVanillaLeavesUncrackedDrawsNoCrack() {
            List<BlockBreakingRenderState> breaking = List.of(
                    new BlockBreakingRenderState(CRACKING.east(), mock(BlockState.class), STAGE));
            assertEquals(OptionalInt.empty(), GroundShake.crackStageOn(CRACKING, breaking));
        }
    }
}
