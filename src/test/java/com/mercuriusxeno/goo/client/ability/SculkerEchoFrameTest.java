package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The frame a Sculker hold draws on its shroom rescan, ten seconds in over
 * the fixed synthetic world and stream of sounds, submits the same quads at
 * the same tints and poses as the parent commit of task
 * sculker-echo-costs-less-each-frame-and-tick drew, captured under the test
 * resources, so the cut to the rescan's cost keeps the echo's look.
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
class SculkerEchoFrameTest {

    @Test
    void theRescanFrameDrawsTheQuadsAndTintsItDidBeforeTheCut() throws IOException {
        SculkerEchoHold hold = new SculkerEchoHold();
        hold.hold(SculkerEchoHold.TEN_SECONDS);
        hold.tick();
        assertEquals(capture(), hold.recordedFrame(), "quads, tints and poses the rescan frame submits");
    }

    private static List<String> capture() throws IOException {
        try (InputStream stream = SculkerEchoFrameTest.class.getResourceAsStream("sculker-echo-hold-frame.txt")) {
            assertNotNull(stream, "capture sculker-echo-hold-frame.txt stands on the classpath");
            return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().toList();
        }
    }
}
