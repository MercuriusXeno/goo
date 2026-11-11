package com.mercuriusxeno.goo.client.radial;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One petal reaches the GUI as its fill sampling the fluid sprite, then its
 * solid edge faded by the petal's presence.
 * decision petals-render-the-live-fluid
 * decision wedges-take-a-solid-edge
 */
class PetalPainterTest {

    private static final int TYPES = 4;
    private static final double PRESENCE = 0.4;
    private static final int EDGE_COLOR = 0xFF123456;
    private static final PetalLook.SpriteBox SPRITE = new PetalLook.SpriteBox(0.25f, 0.5f, 0.125f, 0.25f);

    @Test
    void aPetalSubmitsItsAtlasFillThenItsUntexturedEdgeInTheEdgeColorFadedByPresence() {
        GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
        when(graphics.pose()).thenReturn(new Matrix3x2fStack(1));
        TextureSetup atlas = TextureSetup.singleTexture(mock(GpuTextureView.class), mock(GpuSampler.class));
        RadialWheel wheel = new RadialWheel(TYPES, type -> 0);
        RadialWheel.PetalArc resting = wheel.displayedLayout(0f).getFirst();
        RadialWheel.PetalArc petal = new RadialWheel.PetalArc(resting.type(), resting.ability(), resting.start(),
                resting.arc(), resting.length(), resting.root(), resting.openness(), PRESENCE);
        RadialWheelRenderer.Frame frame = new RadialWheelRenderer.Frame(wheel, List.of(), List.of(), Map.of(), 0, 0,
                100, null, 0f);

        PetalPainter.paint(graphics, frame, new PetalLook.FluidFace(atlas, SPRITE, 0xFFFFFFFF, EDGE_COLOR), petal,
                0xFFFFFFFF);

        ArgumentCaptor<PetalRenderState> submitted = ArgumentCaptor.forClass(PetalRenderState.class);
        verify(graphics, times(2)).submitGuiElementRenderState(submitted.capture());
        PetalRenderState fill = submitted.getAllValues().getFirst();
        PetalRenderState edge = submitted.getAllValues().getLast();
        assertEquals(List.of(atlas, false, RadialWheelRenderer.faded(EDGE_COLOR, PRESENCE)),
                List.of(fill.textureSetup(), edge.isTextured(), edge.color()));
    }
}
