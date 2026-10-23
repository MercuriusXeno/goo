package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooReactionLoader;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.workbench.CaptureManifest;
import com.mercuriusxeno.goo.workbench.DumpRunner;
import com.mercuriusxeno.goo.workbench.InventoryDump;
import com.mercuriusxeno.goo.workbench.WorkbenchPaths;
import net.minecraft.server.MinecraftServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * The in-game workbench's inventory and dumps, run in-process on the running server with no gradle child: the
 * inventory from the live registries, the audit and the cliques over the draft derived in a scratch registry,
 * never the server's own (decision goo-workbench-command-serves-the-page-from-the-game).
 */
public final class InGameDumps {
    private static final String DERIVING = "deriving the draft's values over the server's recipes";
    private static final String WRITING = "writing the audit and the cliques";
    private static final String WRITTEN = "the dumps are written";
    private static final int FAILED = 1;

    private InGameDumps() {
    }

    /**
     * @param server the running server
     * @param paths  the workbench's files, whose inventory folder the dump writes into
     * @return a dump writing the inventory on the server thread, then capturing on this game's client each item
     *         the manifest lacks before the page reads it; a dedicated server captures none
     */
    public static InventoryDump inventory(MinecraftServer server, WorkbenchPaths paths) {
        return () -> {
            onServerThread(server, () -> GooInventoryReport.writeInventory(server, paths.inventoryFile().getParent()));
            captureMissingSlots(paths);
        };
    }

    // decision images-are-slot-captures-taken-on-detection
    private static void captureMissingSlots(WorkbenchPaths paths) throws IOException {
        List<String> missing = CaptureManifest.read(CaptureManifest.fileBeside(paths.imageFolder()))
                .missingFrom(CaptureManifest.itemIdsOf(Files.readString(paths.inventoryFile())));
        try {
            ISidedProxy.get().captureSlots(paths.imageFolder(), missing).join();
        } catch (CompletionException e) {
            throw new IOException(e.getCause() == null ? e.getMessage() : e.getCause().toString(), e);
        }
    }

    /**
     * @param server the running server
     * @param paths  the workbench's files, whose dump folder the run writes into
     * @return a runner deriving the draft and writing the audit and the cliques on the server thread
     */
    public static DumpRunner dumps(MinecraftServer server, WorkbenchPaths paths) {
        return (draft, progress) -> run(server, paths, draft, progress);
    }

    private static int run(MinecraftServer server, WorkbenchPaths paths, Path draft, Consumer<String> progress)
            throws IOException, InterruptedException {
        String json = Files.readString(draft);
        progress.accept(DERIVING);
        try {
            onServerThread(server, () -> {
                GooValueRegistry values = GooBaseValuesPreview.derivedOnServer(json, server);
                progress.accept(WRITING);
                GooAuditReport.writeReport(values, GooReactionLoader.reactionsOf(server),
                        paths.auditJson().getParent());
                GooCliqueDump.write(values, paths.cliqueDump().getParent());
            });
        } catch (IOException e) {
            progress.accept(e.getMessage());
            return FAILED;
        }
        progress.accept(WRITTEN);
        return 0;
    }

    /**
     * Runs work on the server thread and waits on it, since the registries and recipes are the server thread's;
     * the caller is a page request's thread, never the server thread, so the wait never blocks the tick.
     *
     * @param server the running server
     * @param work   the work its thread runs
     */
    private static void onServerThread(MinecraftServer server, Runnable work) throws IOException, InterruptedException {
        try {
            server.submit(work).join();
        } catch (CompletionException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException(e.getMessage());
            }
            throw new IOException(e.getCause() == null ? e.getMessage() : e.getCause().toString(), e);
        }
    }
}
