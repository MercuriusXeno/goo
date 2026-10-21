package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.command.GooAuditReport;
import com.mercuriusxeno.goo.data.GooReactionLoader;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.data.GooValues;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;

/**
 * Writes the goo value audit to config/goo_audit.txt on a headless server, so
 * the value and reaction review reads a fresh dump with no world load
 * (decision audit-dump-from-a-gametest).
 */
@RegistersGameTests
public final class GooAuditDumpTests {
    private static final String VALUES_AUDIT_DUMP = "values_audit_dump";
    private static final List<String> SECTIONS_IN_ORDER = List.of(
            "EXPRESSION WARNINGS", "PHANTOM IDS", "CYCLES", "CONFLICTS", "DIVISIBILITY LOSS",
            "LEAST BASE PER TYPE", "NO VALUE", "FORMULAS", "REACTIONS", "ALL VALUES");
    private static final String SECTION_MISSING = "The audit dump holds no section, in order, headed ";
    private static final String UNREADABLE = "The audit dump could not be read back: ";

    private GooAuditDumpTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, VALUES_AUDIT_DUMP, GooAuditDumpTests::writesTheAuditDump);
    }

    /**
     * Loads base values, derives from recipes, writes the report, and reads it back
     * for every section header in order.
     *
     * @param helper the gametest helper
     */
    public static void writesTheAuditDump(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GooValueRegistry values = GooValues.registryOf(server);
        values.loadBaseValuesFromPacks(server);
        values.deriveFromRecipes(server);
        GooAuditReport.writeReport(values, GooReactionLoader.reactionsOf(server));
        List<String> lines;
        try {
            lines = Files.readAllLines(FMLPaths.CONFIGDIR.get().resolve(GooAuditReport.FILE_AUDIT),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            helper.fail(UNREADABLE + e.getMessage());
            return;
        }
        int from = 0;
        for (String section : SECTIONS_IN_ORDER) {
            int found = headerIndex(lines, section, from);
            helper.assertTrue(found >= 0, SECTION_MISSING + section);
            from = found + 1;
        }
        helper.succeed();
    }

    private static int headerIndex(List<String> lines, String section, int from) {
        for (int i = from; i < lines.size(); i++) {
            if (lines.get(i).startsWith(section + " (")) {
                return i;
            }
        }
        return -1;
    }
}
