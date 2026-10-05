package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A clone's transformation reaches the watchers before the clone itself:
 * adding the clone sends its spawn at once, so a transformation sent after
 * it would leave the clone drawn whole for a frame before the blob appears
 * (decision model-transformation-is-one-animation).
 */
class CloneEntityStepTest {

    @Test
    void theTransformationGoesOutBeforeTheCloneIsAdded() {
        List<String> sent = new ArrayList<>();

        CloneEntityStep.spawnAnnounced("clone", () -> sent.add("transformation"),
                clone -> sent.add(clone + " spawn"));

        assertEquals(List.of("transformation", "clone spawn"), sent);
    }
}
