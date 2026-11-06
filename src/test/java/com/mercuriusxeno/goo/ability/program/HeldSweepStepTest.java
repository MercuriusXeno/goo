package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.network.HeldSweepPayload;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scry, Glitter and Magnet, decoded from their shipped JSON, each sweep
 * through the one held-sweep visual at the shared fast pace.
 * decision held-sweeps-share-one-fast-shell-visual
 */
class HeldSweepStepTest {

    private static final List<String> HELD_SWEEPS = List.of("astral_scry", "crystal_glitter", "metal_magnet");
    /** Half a second of the hold. */
    private static final int HALF_A_SECOND = 10;

    @Test
    void scryGlitterAndMagnetDrawThroughOneSweepVisual() {
        Set<Class<?>> visuals = HELD_SWEEPS.stream()
                .map(name -> sweepOf(name).sweepAt(Vec3.ZERO, 1).getClass()).collect(Collectors.toSet());

        assertEquals(Set.of(HeldSweepPayload.class), visuals, "one visual draws all three");
    }

    @ParameterizedTest
    @ValueSource(strings = {"astral_scry", "crystal_glitter", "metal_magnet"})
    void theSweepPassesHalfItsRangeWithinHalfASecond(String name) {
        HeldSweepPace pace = sweepOf(name).pace();

        double radius = pace.radiusAt(HALF_A_SECOND);

        assertTrue(radius >= pace.range() / 2, name + " reached " + radius + " of " + pace.range());
    }

    private static HeldSweepStep sweepOf(String name) {
        return AbilityJson.decode(name).behaviors().stream()
                .filter(HeldSweepStep.class::isInstance).map(HeldSweepStep.class::cast).findFirst().orElseThrow();
    }
}
