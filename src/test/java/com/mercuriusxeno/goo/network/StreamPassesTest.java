package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.UnmakeStep;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Unmake's step runs in the stream's block pass on the player, where it works
 * the cone's blocks and mobs alike, and nothing runs in the entity pass
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class StreamPassesTest {

    private final List<Step> unmake = AbilityJson.decode("unstable_unmake").behaviors();

    @Test
    void theUnmakeStepRunsInTheBlockPass() {
        List<Step> blockPass = GooStreamHandler.channelSteps(unmake, true);

        assertEquals(1, blockPass.size());
        assertInstanceOf(UnmakeStep.class, blockPass.getFirst());
    }

    @Test
    void nothingRunsInTheEntityPassSinceTheMeltWorksMobsItself() {
        assertEquals(List.of(), GooStreamHandler.channelSteps(unmake, false));
    }
}
