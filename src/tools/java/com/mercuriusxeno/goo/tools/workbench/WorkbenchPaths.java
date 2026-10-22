package com.mercuriusxeno.goo.tools.workbench;

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

    /**
     * @param projectDir the repository root
     * @return the shipped base_values.json and the headless gametest run's inventory and dumps
     */
    public static WorkbenchPaths inProject(Path projectDir) {
        Path config = projectDir.resolve("runs/gameTestServer/config");
        return new WorkbenchPaths(projectDir.resolve("src/main/resources/data/goo/goo_values/base_values.json"),
                config.resolve("goo_inventory.json"), config.resolve("goo_inventory"), config.resolve("goo_audit.txt"),
                config.resolve("goo_cliques.txt"), config.resolve("goo_audit.json"));
    }
}
