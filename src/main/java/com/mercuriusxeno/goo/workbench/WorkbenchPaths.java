package com.mercuriusxeno.goo.workbench;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The files the workbench reads and writes.
 *
 * @param baseValues    the live base_values.json, which only Promote writes
 *                      (decision workbench-authors-a-draft-and-promotes-by-rename)
 * @param inventoryFile the inventory dump's JSON
 * @param imageFolder   the inventory dump's image folder, one PNG per item at namespace/path.png
 * @param auditDump     the audit dump the values-dumps run writes
 * @param cliqueDump    the clique dump the values-dumps run writes
 * @param auditJson     the audit's sections as data, each rule's section, lines and flags
 */
public record WorkbenchPaths(Path baseValues, Path inventoryFile, Path imageFolder, Path auditDump,
                             Path cliqueDump, Path auditJson) {
    private static final String GAMETEST_CONFIG = "runs/gameTestServer/config";
    private static final String SHIPPED_BASE_VALUES = "src/main/resources/data/goo/goo_values/base_values.json";
    private static final String INVENTORY_FILE = "goo_inventory.json";
    private static final String IMAGE_FOLDER = "goo_inventory";
    private static final String AUDIT_DUMP = "goo_audit.txt";
    private static final String CLIQUE_DUMP = "goo_cliques.txt";
    private static final String AUDIT_JSON = "goo_audit.json";
    private static final String DRAFT_FILE = "base_values.draft.json";
    private static final String WORLD_PACK = "datapacks/goo_values";
    private static final String PACK_VALUES = "data/goo/goo_values/base_values.json";
    private static final String WORLD_WORKBENCH = "goo_workbench";

    /**
     * @param world a world's save folder
     * @return the goo_values datapack the in-game workbench promotes into, which the game loads over the jar's file
     */
    public static Path packRoot(Path world) {
        return world.resolve(WORLD_PACK);
    }

    /**
     * @param world a world's save folder
     * @return the live file and draft in the world's goo_values datapack, and the inventory and dumps in the
     *         world's goo_workbench folder, outside the datapacks the game scans
     *         (decision goo-workbench-command-serves-the-page-from-the-game)
     */
    public static WorkbenchPaths inWorld(Path world) {
        Path folder = world.resolve(WORLD_WORKBENCH);
        return new WorkbenchPaths(packRoot(world).resolve(PACK_VALUES), folder.resolve(INVENTORY_FILE),
                folder.resolve(IMAGE_FOLDER), folder.resolve(AUDIT_DUMP), folder.resolve(CLIQUE_DUMP),
                folder.resolve(AUDIT_JSON));
    }

    /**
     * The files the in-game workbench stands on: a dev client's source base_values.json, walked up from its run
     * directory, or the world's goo_values datapack in production, on a server, or where the walk finds no file.
     * decision dev-client-workbench-targets-the-source-base-values
     *
     * @param world        a world's save folder
     * @param runDirectory the game's run directory, under the project in a dev client
     * @param devClient    whether the game is a client outside a production launch
     * @return the project's paths where the gate is open and the walk finds the source file, else the world's
     */
    public static WorkbenchPaths standing(Path world, Path runDirectory, boolean devClient) {
        if (!devClient) {
            return inWorld(world);
        }
        for (Path folder = runDirectory.toAbsolutePath().normalize(); folder != null; folder = folder.getParent()) {
            if (Files.isRegularFile(folder.resolve(SHIPPED_BASE_VALUES))) {
                return inProject(folder);
            }
        }
        return inWorld(world);
    }

    /**
     * @return base_values.draft.json beside the live file, which every save writes and Promote renames over it
     */
    public Path draft() {
        return baseValues.resolveSibling(DRAFT_FILE);
    }

    /**
     * @param projectDir the repository root
     * @return the shipped base_values.json and the headless gametest run's inventory and dumps
     */
    public static WorkbenchPaths inProject(Path projectDir) {
        Path config = projectDir.resolve(GAMETEST_CONFIG);
        return new WorkbenchPaths(projectDir.resolve(SHIPPED_BASE_VALUES),
                config.resolve(INVENTORY_FILE), config.resolve(IMAGE_FOLDER), config.resolve(AUDIT_DUMP),
                config.resolve(CLIQUE_DUMP), config.resolve(AUDIT_JSON));
    }
}
