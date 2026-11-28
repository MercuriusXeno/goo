package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.AbilityMath;
import com.mercuriusxeno.goo.network.OreRevealPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bands a sweep's held ticks walk hold every block of the sphere
 * exactly once (decision glitter-sphere-icons-gem-ore-groups), and the
 * shipped Magnet and Glimmer each name their own goo type on the reveal
 * they send (decision ore-glow-takes-the-casting-goos-color).
 */
class DetectOreStepTest {

    @Test
    void theShippedMagnetRevealsAsMetalAndTheShippedGlimmerAsCrystal() {
        assertEquals(GooTypes.METAL, firstRevealOf("metal_magnet").gooType(), "magnet");
        assertEquals(GooTypes.CRYSTAL, firstRevealOf("crystal_glimmer").gooType(), "glimmer");
    }

    /** The reveal a shipped ability's ore sense sends on its pulse's first tick over the synthetic world. */
    private static OreRevealPayload firstRevealOf(String ability) {
        DetectOreStep sense = AbilityJson.decode(ability).behaviors().stream()
                .filter(DetectOreStep.class::isInstance).map(DetectOreStep.class::cast).findFirst().orElseThrow();
        return sense.revealAt(HeldSweepWorld.ORES, HeldSweepWorld.CASTER, 1);
    }

    @Test
    void theBandsOfAHoldHoldEverySphereBlockExactlyOnce() {
        BlockPos center = new BlockPos(3, -2, 7);
        int most = 6;
        Set<BlockPos> walked = new HashSet<>();
        int[] repeats = {0};
        for (int held = 1; held * 1.5 < most + 1.5; held++) {
            AbilityMath.forEachInShell(center, Math.min(most, (held - 1) * 1.5), Math.min(most, held * 1.5), pos -> {
                if (!walked.add(pos.immutable())) {
                    repeats[0]++;
                }
            });
        }
        Set<BlockPos> sphere = new HashSet<>();
        AbilityMath.forEachInSphere(center, most, pos -> sphere.add(pos.immutable()));

        assertEquals(0, repeats[0], "no block is walked twice");
        assertEquals(sphere, walked);
        assertTrue(walked.contains(center), "the first band holds the center");
    }
}
