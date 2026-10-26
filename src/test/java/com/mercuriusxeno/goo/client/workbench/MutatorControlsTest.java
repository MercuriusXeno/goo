package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the words an assignment's chain of mutators draws: "then applies" leads only a chain holding a mutator, so
 * an assignment with none ends its sentence on its scale rather than trailing into nothing
 * (decision expression-builder-composes-every-form-without-typing).
 */
class MutatorControlsTest {

    @Test
    void emptyChainLeadsWithNoWords() {
        assertEquals(List.of(), MutatorControls.chainLead(List.of()));
    }

    @Test
    void chainHoldingAMutatorLeadsWithThenApplies() {
        assertEquals(List.of("then applies"), MutatorControls.chainLead(List.of("@smelt")));
    }
}
