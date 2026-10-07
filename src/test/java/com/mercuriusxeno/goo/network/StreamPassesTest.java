package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.DamageStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.UnmakeStep;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Unmake's block step runs in the stream's block pass on the player and its
 * damage in the entity pass on each mob in the cone
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
    void theDamageRunsInTheEntityPass() {
        List<Step> entityPass = GooStreamHandler.channelSteps(unmake, false);

        assertEquals(1, entityPass.size());
        assertInstanceOf(DamageStep.class, entityPass.getFirst());
    }
}
