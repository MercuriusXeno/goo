package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.ProgressiveAreaStep;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * FrostRimeVisuals gives frost_tunnel its per-layer effect: the ability names
 * frost_rime and the registry resolves it. Particle emission needs a
 * ServerLevel, which no unit test can build, so the operator's watch in the
 * dev client proves it.
 */
class FrostRimeVisualsTest {

    @Test
    void frostTunnelWalksWithFrostRime() {
        ProgressiveAreaStep walk = (ProgressiveAreaStep) AbilityJson.decode("frost_tunnel").behaviors().getFirst();
        assertEquals(LayerVisualsType.FROST_RIME, walk.visuals());
        assertSame(FrostRimeVisuals.INSTANCE, LayerVisualsType.byName(LayerVisualsType.FROST_RIME));
    }
}
