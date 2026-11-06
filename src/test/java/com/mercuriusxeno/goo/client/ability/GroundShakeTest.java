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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The ground shake jitters the blocks within its radius of the working point
 * and leaves the rest still, and a held bore and a held flatten each play it
 * (decisions bore-shakes-the-blocks-around-the-tunnel-and-never-the-camera,
 * flatten-plays-bores-ground-shake).
 */
class GroundShakeTest {

    private static final TagKey<Block> BREAKABLE =
            TagKey.create(Registries.BLOCK, Identifier.parse("goo:bore_breakable"));
    private static final int SAMPLES = 200;
    private static final float SAMPLE_STEP = 0.1f;
    private static final double STILL = 1e-9;
    private static final Vec3 EYE = new Vec3(0.5, 64.5, 0.5);
    private static final Vec3 LOOK_EAST = new Vec3(1, 0, 0);
    private static final double BORE_RANGE = 4;

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
    class Jitter {

        @Test
        void aBlockBesideTheBoreTunnelShakesWithinTheAmplitude() {
            GroundShake.Zone tunnel = GroundShake.boreZone(EYE, LOOK_EAST, BORE_RANGE);
            double swing = widestSwing(new BlockPos(2, 63, 0), tunnel);
            assertTrue(swing > 0, "a block under the tunnel never moves");
            assertTrue(swing <= GroundShake.AMPLITUDE * Math.sqrt(3), "a block swings past the amplitude");
        }

        @Test
        void aBlockPastTheRadiusStaysStill() {
            GroundShake.Zone tunnel = GroundShake.boreZone(EYE, LOOK_EAST, BORE_RANGE);
            assertEquals(0, widestSwing(new BlockPos(2, 54, 0), tunnel), STILL);
        }

        @Test
        void flattenShakesTheSwathItBreaks() {
            ChannelAim.FacePlane floor = new ChannelAim.FacePlane(new BlockPos(0, 63, 0), Direction.UP);
            GroundShake.Zone swath = GroundShake.flattenZone(floor, new BlockPos(0, 63, 0));
            assertTrue(widestSwing(new BlockPos(1, 64, 1), swath) > 0, "a block in the swath never moves");
            assertEquals(0, widestSwing(new BlockPos(0, 76, 0), swath), STILL);
        }
    }

    @Test
    void theShakingBlocksAreLitAsTheWorldLightsThemWithNoEntityShadeOverIt() {
        // The tesselator bakes the world's face shade in; a pipeline shading by direction again darkens it.
        assertEquals(Identifier.withDefaultNamespace("core/block"),
                GroundShake.renderType().pipeline().getVertexShader());
    }

    @Nested
    class Players {

        @Test
        void aHeldBoreAndAHeldFlattenEachShakeTheGround() {
            assertTrue(GroundShake.shakes(ability(new BoreStep(BREAKABLE, 2)), true));
            assertTrue(GroundShake.shakes(ability(new FlattenStep(BREAKABLE)), true));
        }

        @Test
        void neitherShakesUnheldAndOtherAbilitiesNever() {
            assertFalse(GroundShake.shakes(ability(new BoreStep(BREAKABLE, 2)), false));
            assertFalse(GroundShake.shakes(ability(new FlattenStep(BREAKABLE)), false));
            assertFalse(GroundShake.shakes(ability(mock(Step.class)), true));
            assertFalse(GroundShake.shakes(null, true));
        }
    }
}
