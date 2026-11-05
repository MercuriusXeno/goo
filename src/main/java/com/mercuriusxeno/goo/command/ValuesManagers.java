package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.network.GooValueSync;
import com.mercuriusxeno.goo.network.WorkbenchRequests;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mercuriusxeno.goo.workbench.ModPackChoice;
import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import com.mercuriusxeno.goo.workbench.WorkbenchPaths;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The workbench operations each running server holds for its world's goo_values datapack, or for a dev client's
 * source base_values.json (decision dev-client-workbench-targets-the-source-base-values), answering the values
 * manager's requests, stood by /goo workbench or the first request and closed as the server stops
 * (decision goo-workbench-opens-the-in-game-manager-screen).
 */
public final class ValuesManagers {
    private static final String JAR_VALUES = "/data/goo/goo_values/base_values.json";
    private static final String PACK_MCMETA = "pack.mcmeta";
    private static final String PACK_MCMETA_TEMPLATE = """
            {
              "pack": {
                "description": "Goo values the values manager promotes, loaded over the jar's",
                "min_format": %d,
                "max_format": %d
              }
            }
            """;
    private static final String NO_JAR_VALUES = "The jar holds no ";
    private static final String NO_MOD_PACK = "No pack in the resource stack carries the mod's ";
    // The empty path resolves against the game's working directory, runs/client under the project in a dev client.
    private static final String RUN_DIRECTORY = "";
    private static final String WORKER_NAME = "goo-values-manager";
    // A world's folder datapack answers this prefix before its folder's name as its pack id.
    private static final String FILE_PACK_PREFIX = "file/";
    private static final Map<MinecraftServer, Standing> STANDING = new ConcurrentHashMap<>();

    private ValuesManagers() {
    }

    private record Standing(WorkbenchOperations operations, WorkbenchRequests requests, ExecutorService worker) {
    }

    /**
     * Stands the server's operations where none stand, writing the datapack's pack.mcmeta first so a promoted file
     * loads.
     *
     * @param server the running server
     * @return the operations over its world's values
     * @throws IOException where the datapack folder cannot be written
     */
    public static WorkbenchOperations operationsFor(MinecraftServer server) throws IOException {
        return standing(server).operations();
    }

    /**
     * @param server the running server
     * @return the request joiner answering its values manager requests
     * @throws IOException where the datapack folder cannot be written
     */
    public static WorkbenchRequests requestsFor(MinecraftServer server) throws IOException {
        return standing(server).requests();
    }

    /**
     * Ends the server's dump run in flight and its worker, as the server stopping does.
     *
     * @param server the stopping server
     */
    public static void close(MinecraftServer server) {
        Standing standing = STANDING.remove(server);
        if (standing != null) {
            standing.operations().stopDumps();
            standing.worker().shutdownNow();
        }
    }

    private static synchronized Standing standing(MinecraftServer server) throws IOException {
        Standing standing = STANDING.get(server);
        if (standing != null) {
            return standing;
        }
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        // decision dev-client-workbench-targets-the-source-base-values
        WorkbenchPaths paths = WorkbenchPaths.standing(world, Path.of(RUN_DIRECTORY), isDevClient());
        String packId = replacedPackId(server, world, paths);
        // A pool rather than one thread: a dump run holds its thread for minutes, and a save or a resolve sent while
        // it runs answers beside it.
        ExecutorService worker = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, WORKER_NAME);
            thread.setDaemon(true);
            return thread;
        });
        Standing stood = new Standing(new WorkbenchOperations(paths, ValuesManagers::jarValues,
                InGameDumps.inventory(server, paths), InGameDumps.dumps(server, paths),
                text -> applyLive(server, text, packId),
                // A datapack type counts as known (decision draft-load-drops-entries-naming-an-unknown-type).
                () -> GooTypes.all(server.registryAccess())), new WorkbenchRequests(worker), worker);
        STANDING.put(server, stood);
        return stood;
    }

    /**
     * Applies base_values.json text to the values every player holds, as /goo regen does but from the text the
     * manager left standing in place of the goo_values pack's file: the server's registry loads it over the other
     * packs, derives from the server's recipes, saves the effective cache and syncs every client, on the server
     * thread, with no /reload (decision save-applies-the-draft-live-promote-renames-it).
     *
     * @param server the running server
     * @param text   the base_values.json text now standing
     * @param packId the id of the pack whose own file the text stands in for: the world's goo_values pack, or the
     *               mod's own pack in a dev client
     */
    static void applyLive(MinecraftServer server, String text, String packId) {
        server.submit(() -> {
            GooValueRegistry registry = GooValues.registryOf(server);
            registry.loadBaseValuesOverPacks(server, text, packId::equals);
            registry.deriveFromRecipes(server);
            registry.saveEffectiveValues();
            GooValueSync.sendToAll(server);
        }).join();
    }

    /**
     * Names the pack whose layer a save replaces live, writing the world pack's pack.mcmeta first on the world route
     * so a promoted file loads; the source route writes no pack.
     *
     * @param server the running server
     * @param world  the world's save folder
     * @param paths  the files the workbench stands on
     * @return the mod's own pack id on the source route, else the world's goo_values pack id
     * @throws IOException where the datapack folder cannot be written or no pack carries the mod's file
     */
    private static String replacedPackId(MinecraftServer server, Path world, WorkbenchPaths paths)
            throws IOException {
        if (!paths.equals(WorkbenchPaths.inWorld(world))) {
            return modPackId(server);
        }
        writePackMeta(WorkbenchPaths.packRoot(world));
        return FILE_PACK_PREFIX + WorkbenchPaths.packRoot(world).getFileName();
    }

    private static boolean isDevClient() {
        return !FMLEnvironment.isProduction() && FMLEnvironment.getDist() == Dist.CLIENT;
    }

    /**
     * @param server the running server
     * @return the id of the pack carrying the mod's own base_values.json, whose layer a dev client's save replaces
     * @throws IOException where the jar's file or a pack's file cannot be read
     */
    private static String modPackId(MinecraftServer server) throws IOException {
        List<ModPackChoice.PackLayer> stack = new ArrayList<>();
        for (Resource resource : GooValueRegistry.loadResourceStack(server)) {
            try (InputStream in = resource.open()) {
                stack.add(new ModPackChoice.PackLayer(resource.sourcePackId(), in.readAllBytes()));
            }
        }
        return ModPackChoice.packCarrying(stack, jarValues())
                .orElseThrow(() -> new IOException(NO_MOD_PACK + JAR_VALUES));
    }

    private static void writePackMeta(Path packRoot) throws IOException {
        Path mcmeta = packRoot.resolve(PACK_MCMETA);
        if (Files.isRegularFile(mcmeta)) {
            return;
        }
        Files.createDirectories(packRoot);
        int format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).major();
        Files.writeString(mcmeta, PACK_MCMETA_TEMPLATE.formatted(format, format), StandardCharsets.UTF_8);
    }

    /**
     * @return the jar's base_values.json, the shipped values a first draft starts from
     */
    private static byte[] jarValues() throws IOException {
        try (InputStream in = ValuesManagers.class.getResourceAsStream(JAR_VALUES)) {
            if (in == null) {
                throw new IOException(NO_JAR_VALUES + JAR_VALUES);
            }
            return in.readAllBytes();
        }
    }
}
