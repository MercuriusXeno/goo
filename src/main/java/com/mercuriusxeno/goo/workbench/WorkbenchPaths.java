package com.mercuriusxeno.goo.workbench;

import java.nio.file.Path;

/**
 * The files the workbench reads and writes.
 *
 * @param baseValues    base_values.json, read and written in place
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
