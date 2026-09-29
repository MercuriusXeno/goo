package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.ChainFootprint;
import com.mercuriusxeno.goo.ability.program.ProgressiveAreaStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TunnelWave's train of shock rings: every ring travels into the wall one
 * layer per tick and reaches each layer ahead of its strike, the rings are
 * as wide as the tunnel, and the wave runs until the last ring has
 * traveled the tunnel's length.
 */
class TunnelWaveTest {

    private static final float TOLERANCE = 1e-5f;
    private static final float WALL_FACE = 0.5f;
    private static final int STACKS = 12;

    @ParameterizedTest
    @ValueSource(strings = {"rock_tunnel", "blaze_tunnel", "frost_tunnel"})
    void everyRingReachesEachLayerAheadOfItsStrike(String ability) {
        ProgressiveAreaStep walk = (ProgressiveAreaStep) AbilityJson.decode(ability).behaviors().getFirst();
        int previewDelay = walk.previewDelay().evaluateInt(Variables.NONE);
        for (int launch = 0; launch <= TunnelWave.LAST_LAUNCH; launch += TunnelWave.RING_SPACING) {
            for (int layer = 0; layer < ChainFootprint.tunnelDepth(STACKS); layer++) {
                float layerFront = WALL_FACE + layer;
                int strikeTick = layer + previewDelay;
                assertTrue(TunnelWave.ringDepth(strikeTick, launch) > layerFront,
                        ability + ": the ring launched at tick " + launch + " trails layer " + layer + "'s strike");
            }
        }
    }

    @Test
    void ringsTravelOneLayerPerTickFromTheWallsFace() {
        int launch = TunnelWave.RING_SPACING;
        assertEquals(WALL_FACE, TunnelWave.ringDepth(0f, launch), 0f);
        assertEquals(WALL_FACE, TunnelWave.ringDepth(launch, launch), 0f);
        assertEquals(WALL_FACE + 3f, TunnelWave.ringDepth(launch + 3f, launch), TOLERANCE);
    }

    @Test
    void ringsFadeInFromTheWallAndOutAtTheTunnelsEnd() {
        int layers = ChainFootprint.tunnelDepth(STACKS);
        int launch = TunnelWave.RING_SPACING;
        assertEquals(0f, TunnelWave.ringStrength(launch - 1f, launch, layers), 0f);
        assertEquals(0f, TunnelWave.ringStrength(launch, launch, layers), 0f);
        assertEquals(1f, TunnelWave.ringStrength(launch + TunnelWave.FADE_IN_TICKS, launch, layers), TOLERANCE);
        assertEquals(1f, TunnelWave.ringStrength(launch + layers - TunnelWave.FADE_OUT_LAYERS, launch, layers),
                TOLERANCE);
        assertEquals(0f, TunnelWave.ringStrength(launch + layers, launch, layers), TOLERANCE);
    }

    @Test
    void waveRunsUntilTheLastRingHasTraveledTheTunnel() {
        for (int stacks = 1; stacks <= ChainFootprint.MAX_STACKS; stacks++) {
            int layers = ChainFootprint.tunnelDepth(stacks);
            int duration = TunnelWave.durationTicks(stacks);
            assertEquals(0f, TunnelWave.ringStrength(duration, TunnelWave.LAST_LAUNCH, layers), TOLERANCE);
            assertTrue(TunnelWave.ringStrength(duration - 2f, TunnelWave.LAST_LAUNCH, layers) > 0f
                    || layers <= 1, "the wave ends before its last ring on " + stacks + " stacks");
        }
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void ringsAreAsWideAsTheTunnel(Direction face) {
        AABB section = ChainFootprint.computeBounds(STACKS, false, face);
        float radius = TunnelWave.ringRadius(section, face);
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != face.getAxis()) {
                assertTrue(radius >= (section.max(axis) - section.min(axis)) / 2 - TOLERANCE,
                        "the ring is narrower than the tunnel on " + axis);
            }
        }
    }
}
