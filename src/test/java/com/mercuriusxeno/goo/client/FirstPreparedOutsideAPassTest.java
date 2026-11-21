package com.mercuriusxeno.goo.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A render type's first prepare loads and uploads its textures, which Minecraft 26.3 refuses while a render pass
 * stands, so a type goo first meets inside a stage's open pass waits for the next frame's start to prepare.
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
class FirstPreparedOutsideAPassTest {

    private static final boolean PASS_OPEN = true;

    private final List<String> preparedAt = new ArrayList<>();
    private final FirstPreparedOutsideAPass<String, String> preparation = new FirstPreparedOutsideAPass<>(key -> {
        preparedAt.add(key);
        return "prepared " + key;
    });

    @Test
    void aTypeFirstMetInsideAPassDrawsNothingAndPreparesNothing() {
        assertNull(preparation.prepared("icon", PASS_OPEN));
        assertEquals(List.of(), preparedAt);
    }

    @Test
    void aTypeFirstMetInsideAPassPreparesAtTheNextFrameStartAndThenDraws() {
        preparation.prepared("icon", PASS_OPEN);
        preparation.prepareWaiting();
        assertEquals(List.of("icon"), preparedAt);
        assertEquals("prepared icon", preparation.prepared("icon", PASS_OPEN));
    }

    @Test
    void aTypeMetOutsideAPassPreparesAtOnce() {
        assertEquals("prepared icon", preparation.prepared("icon", !PASS_OPEN));
        assertEquals("prepared icon", preparation.prepared("icon", PASS_OPEN));
    }

    @Test
    void aFrameStartPreparesEachWaitingTypeOnce() {
        preparation.prepared("icon", PASS_OPEN);
        preparation.prepared("icon", PASS_OPEN);
        preparation.prepareWaiting();
        preparation.prepareWaiting();
        assertEquals(List.of("icon"), preparedAt);
    }
}
