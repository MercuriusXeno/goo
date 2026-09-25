package com.mercuriusxeno.goo.client.ability;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that the per-frame lens work runs only while the config turns the
 * lens on, and that a lens turned off is only released.
 */
class NetherLensClientEventsTest {

    private static final String APPLY = "apply";
    private static final String RELEASE = "release";

    private static List<String> driveOneFrame(boolean lensEnabled) {
        List<String> ran = new ArrayList<>();
        NetherLensClientEvents.driveFrame(lensEnabled, () -> ran.add(APPLY), () -> ran.add(RELEASE));
        return ran;
    }

    @Test
    void aLensTurnedOffRunsNoPerFrameWork() {
        assertEquals(List.of(RELEASE), driveOneFrame(false));
    }

    @Test
    void aLensTurnedOnRunsThePerFrameWork() {
        assertEquals(List.of(APPLY), driveOneFrame(true));
    }
}
