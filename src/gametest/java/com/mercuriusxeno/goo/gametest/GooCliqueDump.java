package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.command.CliqueTablesAuditRule;
import com.mercuriusxeno.goo.command.GooAuditInputs;
import com.mercuriusxeno.goo.command.GooAuditSection;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.loading.FMLPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes config/goo_cliques.txt from the audit's clique tables rule, so the clique dump reads apart from the
 * audit as it always has (decision workbench-reruns-the-audit-headless).
 */
final class GooCliqueDump {
    static final String FILE_CLIQUES = "goo_cliques.txt";
    static final String TITLE = CliqueTablesAuditRule.TITLE;

    private GooCliqueDump() {
    }

    /**
     * Writes the clique dump from the derived values.
     *
     * @param values the server's goo value registry, derived
     */
    static void write(GooValueRegistry values) {
        GooAuditSection cliques = new CliqueTablesAuditRule().run(
                new GooAuditInputs(values, List.of(), BuiltInRegistries.ITEM.keySet()));
        List<String> out = new ArrayList<>(List.of(TITLE, ""));
        out.addAll(cliques.lines());
        try {
            Files.write(FMLPaths.CONFIGDIR.get().resolve(FILE_CLIQUES), out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Goo.LOGGER.error("Failed to write {}: {}", FILE_CLIQUES, e.getMessage());
        }
    }
}
