package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The held dome draws only for an armed arc throw at the world or the crosshair
 * whose area is a sphere, anchored in the cell the throw lands in, and its
 * through-blocks pass ignores depth (decision held-visual-ghosts-the-landing-in-two-passes).
 */
class HeldDomeRendererTest {

    private static boolean shows(String name, boolean armed) {
        AbilityDefinition definition = AbilityJson.decode(name);
        return HeldDomeRenderer.showsDome(definition.delivery(), definition.badge(), definition.area(), armed);
    }

    @Nested
    class ShowPredicate {
        @Test
        void razorWithAnArmedPressShowsTheDome() {
            assertTrue(shows("crystal_cloud", true));
        }

        @Test
        void razorWithNoPressShowsNoDome() {
            assertFalse(shows("crystal_cloud", false));
        }

        @ParameterizedTest
        @ValueSource(strings = {"crystal_shards", "ender_blink", "blaze_spitfire"})
        void mobSelfAndChanneledShowNoDome(String name) {
            assertFalse(shows(name, true));
        }
    }

    @Nested
    class Anchor {
        private static final BlockPos FLOOR = new BlockPos(3, 64, -7);

        @Test
        void blockTargetCentersTheDomeInTheCellBesideTheStruckFace() {
            Vec3 point = new Vec3(3.5, 65, -6.5);
            HeldDomeRenderer.DomeAnchor anchor = HeldDomeRenderer.anchorOf(
                    new TargetResult.BlockTarget(FLOOR, Direction.UP, false, point));
            assertEquals(new Vec3(3, 65, -7), anchor.domeCorner());
            assertEquals(new Vec3(3.5, 65, -6.5), anchor.ringCenter());
            assertEquals(Direction.UP, anchor.face());
        }

        @Test
        void pointInOpenAirCentersTheDomeOnThePoint() {
            Vec3 point = new Vec3(10, 70, 10);
            HeldDomeRenderer.DomeAnchor anchor = HeldDomeRenderer.anchorOf(
                    new TargetResult.PointTarget(point, FLOOR, Direction.UP, false));
            assertEquals(new Vec3(9.5, 69.5, 9.5), anchor.domeCorner());
            assertEquals(point, anchor.ringCenter());
        }

        @Test
        void nothingAimedAtAnchorsNothing() {
            assertNull(HeldDomeRenderer.anchorOf(new TargetResult.None()));
        }
    }

    /** A dome the renderer would show with no ghost for its type draws nothing at all, as Rock Crush did. */
    @Test
    void everyShippedHeldDomeHasAGhostForItsType() {
        for (var file : AbilityJson.files()) {
            AbilityDefinition ability = AbilityJson.decode(file);
            if (HeldDomeRenderer.showsDome(ability.delivery(), ability.badge(), ability.area(), true)) {
                assertNotNull(HeldDomeRenderer.ghostOf(ability.gooType()), ability.id() + " shows a dome with no ghost");
            }
        }
    }

    @Test
    void rockCrushsGhostIsTheRockDust() {
        assertSame(RockExplosionVisual.INSTANCE, HeldDomeRenderer.ghostOf(GooTypes.ROCK));
        PipelineShaders.assertExist(GooRenderTypes.ROCK_EXPLOSION_THROUGH_BLOCKS);
        assertEquals(CompareOp.ALWAYS_PASS,
                GooRenderTypes.ROCK_EXPLOSION_THROUGH_BLOCKS.getDepthStencilState().depthTest());
    }

    @Test
    void razorsGhostIsTheCrystalLanding() {
        assertSame(CrystalExplosionVisual.INSTANCE, HeldDomeRenderer.ghostOf(GooTypes.CRYSTAL));
    }

    @Test
    void blackHolesGhostDrawsItsBodyAndCoronaThroughBlocks() {
        for (RenderPipeline pipeline : List.of(GooRenderTypes.NETHER_BLACKHOLE_HELD,
                GooRenderTypes.NETHER_BLACKHOLE_THROUGH_BLOCKS, GooRenderTypes.NETHER_CORONA_THROUGH_BLOCKS)) {
            PipelineShaders.assertExist(pipeline);
            assertFalse(pipeline.getDepthStencilState().writeDepth(), pipeline.getLocation() + " writes depth");
        }
        assertEquals(CompareOp.ALWAYS_PASS,
                GooRenderTypes.NETHER_BLACKHOLE_THROUGH_BLOCKS.getDepthStencilState().depthTest());
        assertEquals(CompareOp.ALWAYS_PASS,
                GooRenderTypes.NETHER_CORONA_THROUGH_BLOCKS.getDepthStencilState().depthTest());
        assertSame(NetherHeldGhost.INSTANCE, HeldDomeRenderer.ghostOf(GooTypes.NETHER));
    }

    @Test
    void throughBlocksPassIgnoresDepthOverTheCrystalShader() {
        PipelineShaders.assertExist(GooRenderTypes.CRYSTAL_EXPLOSION_THROUGH_BLOCKS);
        assertEquals(CompareOp.ALWAYS_PASS,
                GooRenderTypes.CRYSTAL_EXPLOSION_THROUGH_BLOCKS.getDepthStencilState().depthTest());
        assertEquals(GooRenderTypes.CRYSTAL_EXPLOSION.getVertexShader(),
                GooRenderTypes.CRYSTAL_EXPLOSION_THROUGH_BLOCKS.getVertexShader());
    }
}
