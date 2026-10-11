package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A birth's transformation reaches the watchers before the newborn itself:
 * adding the newborn sends its spawn at once, so a transformation sent after
 * it would leave the newborn drawn whole for a frame before the blob appears
 * (decision model-transformation-is-one-animation).
 */
class SpawnSlimeStepTest {

    @Test
    void theTransformationGoesOutBeforeTheNewbornIsAdded() {
        List<String> sent = new ArrayList<>();

        SpawnSlimeStep.spawnAnnounced("slime", () -> sent.add("transformation"),
                born -> sent.add(born + " spawn"));

        assertEquals(List.of("transformation", "slime spawn"), sent);
    }
}
