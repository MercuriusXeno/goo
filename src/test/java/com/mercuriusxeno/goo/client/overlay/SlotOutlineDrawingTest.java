package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.PipelineReads;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Goo's selection outline draws through the line type vanilla 26.3 picks for its own block outline, so under
 * improved transparency, where a translucent outline draws in the OIT phase, its type carries an OIT set and the
 * client draws it rather than crashing (defect row 8).
 * decision goo-translucency-draws-through-order-independent-transparency
 */
class SlotOutlineDrawingTest {

    @Test
    void theOutlineUnderImprovedTransparencyCarriesAnOitSet() {
        assertNotNull(PipelineReads.oitPipelines(SlotOutlineDrawing.selectionOutlineType(false, true)),
                "the selection outline's type under improved transparency carries no OIT set");
    }

    /**
     * A slot's removal indicator sits on the canister's own edges, so under improved transparency it draws in the
     * OIT phase beside the canister's translucent faces, blending with them rather than z-fighting them from the
     * solid phase; with improved transparency off it keeps the opaque lines it drew before.
     * decision goo-translucency-draws-through-order-independent-transparency
     */
    @Test
    void theWireframeBlendsWithTheCanisterUnderImprovedTransparency() {
        assertEquals(List.of(RenderTypes.linesTranslucentNoDepthWrite(), RenderTypes.lines()),
                List.of(SlotOutlineDrawing.wireframeType(true), SlotOutlineDrawing.wireframeType(false)));
    }

    @Test
    void theOutlinePicksVanillasTypeInEachMode() {
        assertEquals(List.of(RenderTypes.linesDepthBias(), RenderTypes.linesTranslucentNoDepthWrite(),
                        RenderTypes.linesTranslucent()),
                List.of(SlotOutlineDrawing.selectionOutlineType(true, true),
                        SlotOutlineDrawing.selectionOutlineType(false, true),
                        SlotOutlineDrawing.selectionOutlineType(false, false)));
    }
}
