package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityMath;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bands a sweep's held ticks walk hold every block of the sphere
 * exactly once (decision glitter-sphere-icons-gem-ore-groups).
 */
class DetectOreStepTest {

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
