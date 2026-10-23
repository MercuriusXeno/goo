package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.data.GooValueRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes goo_cliques.txt from the audit's clique tables rule, so the clique dump reads apart from the audit as it
 * always has (decision workbench-reruns-the-audit-headless). The values-dumps gametest writes it to the config
 * folder, and the in-game workbench under the world (decision goo-workbench-command-serves-the-page-from-the-game).
 */
public final class GooCliqueDump {
    /**
     * The clique dump's file name.
     */
    public static final String FILE_CLIQUES = "goo_cliques.txt";
    /**
     * The clique dump's first line.
     */
    public static final String TITLE = CliqueTablesAuditRule.TITLE;
    private static final String BLANK_LINE = "";

    private GooCliqueDump() {
    }

    /**
     * Writes the clique dump into the config folder.
     *
     * @param values the server's goo value registry, derived
     */
    public static void write(GooValueRegistry values) {
        write(values, FMLPaths.CONFIGDIR.get());
    }

    /**
     * Writes the clique dump into a folder.
     *
     * @param values the goo value registry, derived
     * @param folder the folder goo_cliques.txt lands in
     */
    public static void write(GooValueRegistry values, Path folder) {
        GooAuditSection cliques = new CliqueTablesAuditRule().run(
                new GooAuditInputs(values, List.of(), BuiltInRegistries.ITEM.keySet()));
        List<String> out = new ArrayList<>(List.of(TITLE, BLANK_LINE));
        out.addAll(cliques.lines());
        GooAuditReport.writeFile(folder, FILE_CLIQUES, out);
    }
}
