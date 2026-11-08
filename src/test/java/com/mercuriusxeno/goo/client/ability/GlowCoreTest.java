package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The core a glow beam emanates from inside its prism is brighter and
 * larger than the beam, and softens outward: each layer wider and fainter
 * than the one inside it.
 * decision diagnose-and-correct-the-sunbeam-prism-depth
 */
class GlowCoreTest {

    private static final List<GlowBeamMesh.Layer> BEAM = GlowBeamMesh.LAYERS;
    private static final List<GlowBeamMesh.Layer> CORE = GlowCore.LAYERS;

    @Test
    void theCoreIsBrighterThanTheBeam() {
        assertTrue(ARGB.alpha(CORE.getFirst().color()) > ARGB.alpha(BEAM.getFirst().color()),
                "the core's middle should add more light than the beam's core");
    }

    @Test
    void theCoreIsLargerThanTheBeam() {
        assertTrue(CORE.getFirst().radius() > BEAM.getFirst().radius(),
                "the core's middle should be wider than the beam's core, so the beam's end sits inside it");
        assertTrue(CORE.getLast().radius() > BEAM.getLast().radius(),
                "the core's glow should reach wider than the beam's");
    }

    @Test
    void theCoreSoftensOutward() {
        for (int layer = 1; layer < CORE.size(); layer++) {
            assertTrue(CORE.get(layer).radius() > CORE.get(layer - 1).radius(), "layer " + layer + " is no wider");
            assertTrue(ARGB.alpha(CORE.get(layer).color()) < ARGB.alpha(CORE.get(layer - 1).color()),
                    "layer " + layer + " is no fainter");
        }
    }

    @Test
    void theCoreEmitsItsLayers() {
        VertexConsumer buffer = mock(VertexConsumer.class);
        when(buffer.addVertex(any(PoseStack.Pose.class), anyFloat(), anyFloat(), anyFloat())).thenReturn(buffer);
        when(buffer.setColor(anyInt())).thenReturn(buffer);
        GlowCore.emit(new PoseStack().last(), buffer, 1f, 1f);
        verify(buffer, atLeastOnce()).addVertex(any(PoseStack.Pose.class), anyFloat(), anyFloat(), anyFloat());
    }
}
