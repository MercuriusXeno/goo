package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.PipelineReads;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitStage;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Click's ring draws in 26.3's OIT phase under improved transparency, where a translucent particle layer with no
 * OIT set crashes the client: its layer carries a registered set whose phases run the particle shaders and keep
 * passing every depth test, so the ring still shows through blocks.
 * decision goo-translucency-draws-through-order-independent-transparency
 */
class ClickWaveParticleTest {

    @Test
    void theRingsLayerDrawsThroughOrderIndependentTransparencyPastEveryBlock() {
        OitPipelineSet oit = ClickWaveParticle.THROUGH_BLOCKS.oitPipelineSet();
        assertNotNull(oit, "Click's ring layer carries no OIT set");
        RegisterRenderPipelinesEvent event = mock(RegisterRenderPipelinesEvent.class);

        GooRenderTypes.registerPipelines(event);

        verify(event).registerOitPipelineSet(oit);
        assertEquals(List.of(true, true, true), Arrays.stream(OitStage.values())
                .map(stage -> oit.getPipeline(stage).getDepthStencilState() == null
                        && PipelineReads.vertexShader(oit.getPipeline(stage))
                                .equals(PipelineReads.vertexShader(GooRenderTypes.CLICK_WAVE_THROUGH_BLOCKS)))
                .toList());
    }
}
