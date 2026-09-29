package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.ChainFootprint;
import com.mercuriusxeno.goo.ability.program.ProgressiveAreaStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TunnelWave drives into the wall one layer per tick from burnout, reaching
 * each layer ahead of its strike, for as long as the tunnel runs.
 */
class TunnelWaveTest {

    private static final float TOLERANCE = 1e-5f;
    private static final float WALL_FACE = 0.5f;
    private static final int STACKS = 12;

    @ParameterizedTest
    @ValueSource(strings = {"rock_tunnel", "blaze_tunnel", "frost_tunnel"})
    void waveReachesEachLayerAheadOfItsStrike(String ability) {
        ProgressiveAreaStep walk = (ProgressiveAreaStep) AbilityJson.decode(ability).behaviors().getFirst();
        int previewDelay = walk.previewDelay().evaluateInt(Variables.NONE);
        assertTrue(previewDelay > 0, ability + " strikes as it previews");
        for (int layer = 0; layer < ChainFootprint.tunnelDepth(STACKS); layer++) {
            float layerFront = WALL_FACE + layer;
            assertEquals(layerFront, TunnelWave.depth(layer), TOLERANCE, "the wave misses layer " + layer);
            int strikeTick = layer + previewDelay;
            assertTrue(TunnelWave.depth(strikeTick) > layerFront, "the wave trails layer " + layer + "'s strike");
        }
    }

    @Test
    void waveRunsTheTunnelsLength() {
        for (int stacks = 1; stacks <= ChainFootprint.MAX_STACKS; stacks++) {
            assertEquals(ChainFootprint.tunnelDepth(stacks) + 1, TunnelWave.durationTicks(stacks));
        }
    }

    @Test
    void waveFadesInFromTheWallAndOutAtTheTunnelsEnd() {
        int layers = ChainFootprint.tunnelDepth(STACKS);
        assertEquals(0f, TunnelWave.strength(0f, layers), 0f);
        assertEquals(1f, TunnelWave.strength(TunnelWave.FADE_IN_TICKS, layers), TOLERANCE);
        assertEquals(1f, TunnelWave.strength(layers - TunnelWave.FADE_OUT_LAYERS, layers), TOLERANCE);
        assertEquals(0f, TunnelWave.strength(layers, layers), TOLERANCE);
    }
}
