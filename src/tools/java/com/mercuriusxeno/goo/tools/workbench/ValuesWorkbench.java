package com.mercuriusxeno.goo.tools.workbench;

import com.mercuriusxeno.goo.workbench.ValuesWorkbenchServer;
import com.mercuriusxeno.goo.workbench.WorkbenchPaths;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * Runs the values workbench: serves the page on a loopback port and opens the browser on it, with no
 * Minecraft client (decision workbench-authors-base-values-whole). The gradle task valuesWorkbench runs
 * it from the repository root, feeding it the gametest dumps, and it serves until the page's Stop ends it
 * (decision gradle-run-stays-on-the-gametest-dump).
 */
public final class ValuesWorkbench {
    private static final int ANY_FREE_PORT = 0;

    private ValuesWorkbench() {
    }

    /**
     * @param args the repository root, then optionally the port, 0 or none for any free one
     * @throws IOException where the port cannot be bound
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        Path projectDir = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        int port = args.length > 1 ? Integer.parseInt(args[1]) : ANY_FREE_PORT;
        ValuesWorkbenchServer server = ValuesWorkbenchServer.start(WorkbenchPaths.inProject(projectDir),
                new HeadlessGametestInventoryDump(projectDir), new HeadlessGametestDumpRunner(projectDir), port);
        System.out.println("Values workbench at " + server.url() + " (Stop in the page closes it)");
        openBrowser(server.url());
        // The page's Stop ends the server; main returning then lets the JVM exit and the gradle task end
        // (decision topbar-stop-ends-every-workbench-process).
        server.awaitStop();
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (IOException | UnsupportedOperationException e) {
            System.out.println("Open " + url + " in a browser: " + e.getMessage());
        }
    }
}
