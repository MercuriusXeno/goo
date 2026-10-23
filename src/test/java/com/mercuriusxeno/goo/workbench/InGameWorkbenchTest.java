package com.mercuriusxeno.goo.workbench;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the in-game workbench's lifecycle seam: one page server per game server, closed by the game's stopping
 * hook or a second /goo workbench, and ended by the page's Stop alone, the game left running
 * (decision goo-workbench-command-serves-the-page-from-the-game). It binds a loopback port under a temp folder.
 */
class InGameWorkbenchTest {

    @TempDir
    Path temp;

    private final Object gameServer = new Object();
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void closeTheWorkbench() {
        InGameWorkbench.close(gameServer);
    }

    private ValuesWorkbenchServer started() throws IOException {
        return ValuesWorkbenchServer.start(WorkbenchPaths.inWorld(temp), () -> new byte[0], () -> { },
                (draft, progress) -> 0, 0);
    }

    @Test
    void stoppingHookClosesTheServersPage() throws IOException, InterruptedException {
        ValuesWorkbenchServer server = InGameWorkbench.open(gameServer, this::started);
        assertSame(server, InGameWorkbench.running(gameServer).orElseThrow());
        assertTrue(InGameWorkbench.close(gameServer));
        assertTrue(server.awaitStop(Duration.ofSeconds(5)));
        assertTrue(InGameWorkbench.running(gameServer).isEmpty());
        assertFalse(InGameWorkbench.close(gameServer));
    }

    @Test
    void pageStopEndsThePageAloneAndTheNextOpenStartsAnew() throws IOException, InterruptedException {
        ValuesWorkbenchServer first = InGameWorkbench.open(gameServer, this::started);
        HttpResponse<String> stopped = client.send(HttpRequest.newBuilder(URI.create(first.url() + "/api/stop"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, stopped.statusCode());
        assertTrue(first.awaitStop(Duration.ofSeconds(5)));
        // The stop returned to this thread and the JVM serves on: the page's Stop ends its server, never the game.
        assertTrue(InGameWorkbench.running(gameServer).isEmpty());
        ValuesWorkbenchServer second = InGameWorkbench.open(gameServer, this::started);
        assertNotEquals(first, second);
        assertFalse(second.stopped());
    }
}
