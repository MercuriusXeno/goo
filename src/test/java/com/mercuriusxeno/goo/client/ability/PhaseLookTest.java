package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A phased body drawn as the inventory's paper doll is marked so it draws a
 * readable ghost, its own skin faded; a body in phase is left unmarked.
 * phased-things-draw-as-a-ghostly-image
 */
class PhaseLookTest {

    @Test
    void onlyAPhasedPaperDollDrawsTheReadableGhost() {
        EntityRenderState phased = new EntityRenderState();
        phased.setRenderData(PhaseLook.PHASED, true);
        EntityRenderState inPhase = new EntityRenderState();

        PhaseLook.markPaperDoll(phased);
        PhaseLook.markPaperDoll(inPhase);

        assertEquals(Map.of("phased", true, "in phase", false),
                Map.of("phased", PhaseLook.isPaperDoll(phased), "in phase", PhaseLook.isPaperDoll(inPhase)));
    }
}
