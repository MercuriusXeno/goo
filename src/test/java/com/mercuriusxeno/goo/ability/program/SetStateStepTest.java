package com.mercuriusxeno.goo.ability.program;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * SetStateStep: it writes its resolved values onto the host's own block,
 * finishes the tick it runs, and asks for the state-write capability.
 * decision bulb-one-model-max-light-beacon-combo
 */
class SetStateStepTest {

    @Test
    void writesResolvedValuesOntoTheHostsOwnBlockAndFinishes() {
        StateWriteHost host = mock(StateWriteHost.class);
        SetStateStep step = new SetStateStep(Map.of("lit", new StateValue.Named("true")));

        boolean finished = step.tick(new StepContext(host, 0, 0));

        assertTrue(finished, "the step should finish the tick it runs");
        verify(host).writeOwnState(Map.of("lit", "true"));
    }

    @Test
    void needsStateWriteAndWhatItsValuesNeed() {
        SetStateStep step = new SetStateStep(Map.of("facing", new StateValue.PlacedFace()));

        assertEquals(Set.of(HostCapability.STATE_WRITE, HostCapability.PLACED_FACE), step.requires());
    }
}
