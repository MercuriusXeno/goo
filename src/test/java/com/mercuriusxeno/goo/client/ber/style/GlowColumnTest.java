package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.block.ability.PrismColumn;
import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The glow column a Sunbeam-converted reflector draws: no face of it lies on
 * the face the prism stands on, where it would fight the block's own face
 * for depth, and it writes no depth, so Sunbeam's ray, drawn after it, shows
 * in front of the column rather than hidden behind it.
 * decision diagnose-and-correct-the-sunbeam-prism-depth
 */
class GlowColumnTest {

    private static final double EPSILON = 1e-6;
    private static final double HALFWAY = 0.5;
    private static final double RAIL_LENGTH = 4;
    private static final int BRIGHTEST = 15;
    private static final CrystalClusterSubmitter.Look LOOK =
            new CrystalClusterSubmitter.Look(new GooRenderUtil.UvRect(0f, 0f, 1f, 1f), GlowColumn.TINT);

    @Test
    void noFaceOfTheFoldedColumnLiesOnTheLandingFace() {
        for (double fold : new double[] {0, HALFWAY, 1}) {
            for (Vec3[] face : PrismCrystal.foldFaces(fold)) {
                assertFalse(liesOnTheLandingFace(face), "a face on the landing face at fold " + fold + ": "
                        + Arrays.toString(face));
            }
        }
    }

    @Test
    void noFaceOfTheBlobTurningIntoTheColumnLiesOnTheLandingFace() {
        for (Vec3[] face : PrismCrystal.morphFaces(HALFWAY)) {
            assertFalse(liesOnTheLandingFace(face), "a face on the landing face: " + Arrays.toString(face));
        }
    }

    @Test
    void theGlowColumnWritesNoDepth() {
        PrismRenderState state = new PrismRenderState();
        state.look = LOOK;
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        GlowColumn.submit(state, new PoseStack(), collector, PrismCrystal.ColumnSides.FOUR);
        ArgumentCaptor<RenderType> drawnWith = ArgumentCaptor.forClass(RenderType.class);
        verify(collector, atLeastOnce()).submitCustomGeometry(any(PoseStack.class), drawnWith.capture(), any());
        List<RenderType> types = drawnWith.getAllValues();
        for (RenderType type : types) {
            assertFalse(type.pipeline().getDepthStencilState().writeDepth(),
                    "the column draws through " + type + ", which writes depth and hides the ray behind it");
        }
        assertTrue(types.size() > 1, "the column and its halo should both draw");
    }

    /**
     * Translucent geometry in one submit order draws per render type in no
     * fixed order, so a beam sharing the column's order may draw first and
     * the column cover it; each beam submits at a later order.
     */
    @Test
    void theReflectorsRailBeamDrawsAfterItsColumn() {
        PrismRenderState state = new PrismRenderState();
        state.look = LOOK;
        state.links = List.of(new Vec3(RAIL_LENGTH, 0, 0));
        state.linkLight = BRIGHTEST;
        assertBeamsSubmittedAfterTheColumn(collector -> new GlowReflectorStyle().submit(state, new PoseStack(),
                collector));
    }

    @Test
    void theBeaconsBeamDrawsAfterItsColumn() {
        PrismRenderState state = new PrismRenderState();
        state.look = LOOK;
        assertBeamsSubmittedAfterTheColumn(collector -> new GlowBeaconStyle().submit(state, new PoseStack(),
                collector));
    }

    /** A reflector's core shows at every linked prism, the end that draws the rail and the end that does not. */
    @Test
    void aLinkedReflectorDrawsItsCoreAfterItsColumn() {
        for (double along : new double[] {RAIL_LENGTH, -RAIL_LENGTH}) {
            PrismRenderState state = new PrismRenderState();
            state.look = LOOK;
            state.links = List.of(new Vec3(along, 0, 0));
            state.linkLight = BRIGHTEST;
            SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
            OrderedSubmitNodeCollector later = mock(OrderedSubmitNodeCollector.class);
            when(collector.order(GlowColumn.BEAM_ORDER)).thenReturn(later);
            new GlowReflectorStyle().submit(state, new PoseStack(), collector);
            verify(later, atLeastOnce()).submitCustomGeometry(any(PoseStack.class), eq(GooRenderTypes.GLOW_SHELL_TYPE),
                    any());
        }
    }

    private static void assertBeamsSubmittedAfterTheColumn(Consumer<SubmitNodeCollector> submit) {
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        OrderedSubmitNodeCollector later = mock(OrderedSubmitNodeCollector.class);
        when(collector.order(anyInt())).thenReturn(later);
        RenderType beam = GooRenderTypes.glowBeam(BeaconRenderer.BEAM_LOCATION);
        submit.accept(collector);
        verify(collector, never()).submitCustomGeometry(any(PoseStack.class), eq(beam), any());
        verify(later, atLeastOnce()).submitCustomGeometry(any(PoseStack.class), eq(beam), any());
        ArgumentCaptor<Integer> order = ArgumentCaptor.forClass(Integer.class);
        verify(collector, atLeastOnce()).order(order.capture());
        assertTrue(order.getAllValues().stream().allMatch(value -> value > 0),
                "a beam should submit after the column's order 0, at " + order.getAllValues());
    }

    /** A face lies on the landing face when every corner sits at the column's base height. */
    private static boolean liesOnTheLandingFace(Vec3[] face) {
        return Arrays.stream(face).allMatch(corner -> Math.abs(corner.y - PrismColumn.BASE_Y) < EPSILON);
    }
}
