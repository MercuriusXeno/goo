package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.IndicatorShowing;
import com.mercuriusxeno.goo.ability.program.BoreStep;
import com.mercuriusxeno.goo.ability.program.BreakPace;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.ability.program.FlattenStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.TerrakinesisStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The ground shake jitters only the blocks a bore or a flatten breaks within
 * its lead, gently, and leaves the rest still (decision
 * only-the-blocks-about-to-break-shake-briefly-and-gently), a held bore and a
 * held flatten each play it (decisions
 * bore-shakes-the-blocks-around-the-tunnel-and-never-the-camera,
 * flatten-plays-bores-ground-shake), and a winding-up Terrakinesis shakes the
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
    /** A bore whose nearest block breaks in one tick. */
    private static final BreakPace ONE_BLOCK_A_TICK = new BreakPace(1, 1);
    /** The jitter's amplitude before this change, read from GroundShake.AMPLITUDE at 128a57da7. */
    private static final double PREVIOUS_AMPLITUDE = 0.04;
    /** A hold long past any wind-up. */
    private static final int HELD_LONG = 100;
    private static final TerrakinesisStep TERRAKINESIS = new TerrakinesisStep(6, 1.5, 2.5, 3.5, 1.5, 1000);

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

    @Nested
    class BoreZone {

        /** A row of breakable stone straight ahead of the eye, and one breakable block under the row, out of the cone. */
        private final Set<BlockPos> stone = Set.of(new BlockPos(1, 64, 0), new BlockPos(2, 64, 0),
                new BlockPos(3, 64, 0), new BlockPos(4, 64, 0), new BlockPos(5, 64, 0), new BlockPos(6, 64, 0),
                new BlockPos(7, 64, 0), new BlockPos(2, 62, 0));
        private final GroundShake.Zone zone = GroundShake.boreZone(new BoreStep(BREAKABLE, ONE_BLOCK_A_TICK), EYE,
                new ChannelAim(EYE.add(LOOK_EAST.scale(BORE_RANGE)), null, BORE_CONE_DEGREES),
                cell -> !stone.contains(cell), stone::contains);

        @Test
        void theBlocksTheBoreBreaksWithinTheLeadShakeWithinTheAmplitude() {
            for (int x = 1; x <= GroundShake.SHAKE_LEAD_TICKS; x++) {
                double swing = widestSwing(new BlockPos(x, 64, 0), zone);
                assertTrue(swing > 0, "x " + x + " breaks within the lead and never moves");
                assertTrue(swing <= GroundShake.AMPLITUDE * Math.sqrt(3), "x " + x + " swings past the amplitude");
            }
        }

        @Test
        void aBreakableBlockPastTheLeadStaysStillUntilItsTurnNears() {
            // One block a tick: the sixth in the row breaks after the lead runs out.
            assertEquals(0, widestSwing(new BlockPos(GroundShake.SHAKE_LEAD_TICKS + 1, 64, 0), zone), STILL);
        }

        @Test
        void aBlockBesideTheTunnelThatNeverBreaksStaysStill() {
            assertEquals(0, widestSwing(new BlockPos(2, 62, 0), zone), STILL);
            assertEquals(0, widestSwing(new BlockPos(2, 63, 0), zone), STILL);
        }
    }

    @Nested
    class FlattenZone {

        private final ChannelAim.FacePlane floor = new ChannelAim.FacePlane(new BlockPos(0, 63, 0), Direction.UP);
        /** The swath's first layer standing on the floor, every other layer air, and the floor itself. */
        private final Set<BlockPos> ground = Set.of(new BlockPos(-1, 64, -1), new BlockPos(0, 64, -1),
                new BlockPos(1, 64, -1), new BlockPos(1, 64, 0), new BlockPos(1, 64, 1), new BlockPos(0, 64, 1),
                new BlockPos(-1, 64, 1), new BlockPos(-1, 64, 0), new BlockPos(0, 64, 0), new BlockPos(0, 63, 0));
        private final GroundShake.Zone zone = GroundShake.flattenZone(new ChannelAim(Vec3.ZERO, floor),
                new BlockPos(0, 63, 0), ground::contains);

        @Test
        void theSwathBlocksFlattenBreaksWithinTheLeadShake() {
            // The layer breaks ring in: the corner and its ring neighbours go first.
            for (BlockPos next : List.of(new BlockPos(-1, 64, -1), new BlockPos(0, 64, -1), new BlockPos(1, 64, -1),
                    new BlockPos(1, 64, 0), new BlockPos(1, 64, 1))) {
                assertTrue(widestSwing(next, zone) > 0, next + " breaks within the lead and never moves");
            }
        }

        @Test
        void aSwathBlockBreakingAfterTheLeadAndTheFloorUnderTheSwathStayStill() {
            assertEquals(0, widestSwing(new BlockPos(0, 64, 0), zone), STILL);
            assertEquals(0, widestSwing(new BlockPos(0, 63, 0), zone), STILL);
            assertEquals(0, widestSwing(new BlockPos(2, 64, 0), zone), STILL);
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
    void flattensDustDiscFloatsAboveEveryShakenFaceSoNoSeamShowsItThrough() {
        // The disc is translucent tan; a shaken face rising through it leaves it showing only at the seams.
        assertTrue(RockExplosionVisual.cursorHeightAboveFace() > GroundShake.highestFaceRise(),
                "disc " + RockExplosionVisual.cursorHeightAboveFace() + " above the face, faces rise "
                        + GroundShake.highestFaceRise());
    }

    @Nested
    class Players {

        @Test
        void aHeldBoreAndAHeldFlattenEachShakeTheGround() {
            assertTrue(GroundShake.shakes(ability(new BoreStep(BREAKABLE, ONE_BLOCK_A_TICK)), true, HELD_LONG));
            assertTrue(GroundShake.shakes(ability(new FlattenStep(BREAKABLE)), true, HELD_LONG));
        }

        @Test
        void neitherShakesUnheldAndOtherAbilitiesNever() {
            assertFalse(GroundShake.shakes(ability(new BoreStep(BREAKABLE, ONE_BLOCK_A_TICK)), false, HELD_LONG));
            assertFalse(GroundShake.shakes(ability(new FlattenStep(BREAKABLE)), false, HELD_LONG));
            assertFalse(GroundShake.shakes(ability(mock(Step.class)), true, HELD_LONG));
            assertFalse(GroundShake.shakes(null, true, HELD_LONG));
        }

        @Test
        void terrakinesisShakesWhileItWindsUpAndStopsOnceTheHunkIsHeldOrTheHoldEnds() {
            ClientAbility terrakinesis = ability(TERRAKINESIS);
            assertTrue(GroundShake.shakes(terrakinesis, true, 1));
            assertTrue(GroundShake.shakes(terrakinesis, true, TerrakinesisStep.WINDUP_TICKS - 1));
            assertFalse(GroundShake.shakes(terrakinesis, true, TerrakinesisStep.WINDUP_TICKS));
            assertFalse(GroundShake.shakes(terrakinesis, false, 1));
        }

        @Test
        void terrakinesisShakesNothingWithNoWindUpRunning() {
            // The server publishes no wind-up while a hunk is held or a throw cools down: tick zero.
            assertFalse(GroundShake.shakes(ability(TERRAKINESIS), true, 0));
        }
    }

    @Nested
    class TerrakinesisZone {

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
}
