package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.IndicatorShowing;
import com.mercuriusxeno.goo.ability.program.BoreStep;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The ground shake jitters the blocks within its radius of the working point
 * and leaves the rest still, and a held bore and a held flatten each play it
 * (decisions bore-shakes-the-blocks-around-the-tunnel-and-never-the-camera,
 * flatten-plays-bores-ground-shake), and a winding-up Terrakinesis shakes the
 * cells it would lift (decision terrakinesis-shakes-the-blocks-before-the-tear).
 */
class GroundShakeTest {

    private static final TagKey<Block> BREAKABLE =
            TagKey.create(Registries.BLOCK, Identifier.parse("goo:bore_breakable"));
    private static final double DELTA = 1e-9;
    private static final Vec3 EYE = new Vec3(0.5, 64.5, 0.5);
    private static final Vec3 LOOK_EAST = new Vec3(1, 0, 0);
    private static final double BORE_RANGE = 4;
    /** A hold long past any wind-up. */
    private static final int HELD_LONG = 100;
    private static final TerrakinesisStep TERRAKINESIS = new TerrakinesisStep(6, 1.5, 2.5, 3.5, 1.5, 1000);

    private static ClientAbility ability(Step behavior) {
        return new ClientAbility(Identifier.parse("goo:rock_test"), "test", "", 0, List.of(), List.of(behavior), 0,
                Delivery.of(DeliveryKind.SELF), AbilityBadge.SELF, List.of(), AbilityArea.NONE,
                IndicatorShowing.SELECTED);
    }

    @Nested
    class ZoneStrength {

        /** On the tunnel's line a block shakes full, one block off five ninths, two off a ninth, three off still. */
        @Test
        void theBoreTunnelShakesFullOnItsLineAndFadesOverItsRadius() {
            GroundShake.Zone tunnel = GroundShake.boreZone(EYE, LOOK_EAST, BORE_RANGE);
            assertEquals(1, tunnel.strengthAt(new BlockPos(2, 64, 0)), DELTA);
            assertEquals(5.0 / 9, tunnel.strengthAt(new BlockPos(2, 63, 0)), DELTA);
            assertEquals(1.0 / 9, tunnel.strengthAt(new BlockPos(2, 62, 0)), DELTA);
            assertEquals(0, tunnel.strengthAt(new BlockPos(2, 61, 0)), DELTA);
        }

        /** The swath's three layers off the face shake full on its spine, the face's own ground five ninths. */
        @Test
        void flattenShakesTheSwathItBreaksFullAndTheFacesGroundFiveNinths() {
            ChannelAim.FacePlane floor = new ChannelAim.FacePlane(new BlockPos(0, 63, 0), Direction.UP);
            GroundShake.Zone swath = GroundShake.flattenZone(floor, new BlockPos(0, 63, 0));
            assertEquals(1, swath.strengthAt(new BlockPos(0, 64, 0)), DELTA);
            assertEquals(1, swath.strengthAt(new BlockPos(0, 65, 0)), DELTA);
            assertEquals(1, swath.strengthAt(new BlockPos(0, 66, 0)), DELTA);
            assertEquals(5.0 / 9, swath.strengthAt(new BlockPos(0, 63, 0)), DELTA);
        }
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
            assertTrue(GroundShake.shakes(ability(new BoreStep(BREAKABLE, 2)), true, HELD_LONG));
            assertTrue(GroundShake.shakes(ability(new FlattenStep(BREAKABLE)), true, HELD_LONG));
        }

        @Test
        void neitherShakesUnheldAndOtherAbilitiesNever() {
            assertFalse(GroundShake.shakes(ability(new BoreStep(BREAKABLE, 2)), false, HELD_LONG));
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
        void theZoneShakesEveryCellTheHunkWouldLiftFullAndTheCellsBesideItAtZero() {
            BlockPos struck = new BlockPos(3, 63, 0);
            GroundShake.Zone zone = GroundShake.cellsZone(Set.of(struck, struck.east(), struck.below()));
            List<BlockPos> cells = List.of(struck, struck.east(), struck.below(), struck.west(), struck.above(),
                    struck.north(), struck.east().east());
            Map<BlockPos, Double> strengths = cells.stream()
                    .collect(Collectors.toMap(cell -> cell, zone::strengthAt));
            assertEquals(Map.of(struck, 1.0, struck.east(), 1.0, struck.below(), 1.0, struck.west(), 0.0,
                    struck.above(), 0.0, struck.north(), 0.0, struck.east().east(), 0.0), strengths);
        }
    }
}
