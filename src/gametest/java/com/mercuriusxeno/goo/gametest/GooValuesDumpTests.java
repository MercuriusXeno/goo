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
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Writes the audit and the clique dumps in one headless server boot, the run the values workbench's
 * Audit tab starts (decision workbench-reruns-the-audit-headless).
 */
@RegistersGameTests
public final class GooValuesDumpTests {
    private static final String VALUES_DUMPS = "values_dumps";
    private static final String AUDIT_FIRST_HEADER = "EXPRESSION WARNINGS (";
    private static final String UNWRITTEN = "This run wrote no ";
    private static final String HEADER_MISSING = " holds no line starting ";
    private static final String UNREADABLE = "A dump could not be read back: ";

    private GooValuesDumpTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, VALUES_DUMPS, GooValuesDumpTests::writesBothDumps);
    }

    /**
     * Loads base values, derives from recipes, writes both dumps and reads each back for its first header.
     *
     * @param helper the gametest helper
     */
    public static void writesBothDumps(GameTestHelper helper) {
        Instant start = Instant.now().minusSeconds(1);
        MinecraftServer server = helper.getLevel().getServer();
        GooValueRegistry values = GooValues.registryOf(server);
        values.loadBaseValuesFromPacks(server);
        values.deriveFromRecipes(server);
        GooAuditReport.writeReport(values, GooReactionLoader.reactionsOf(server));
        GooCliqueDump.write(values);
        Path config = FMLPaths.CONFIGDIR.get();
        try {
            assertWrittenWith(helper, config.resolve(GooAuditReport.FILE_AUDIT), AUDIT_FIRST_HEADER, start);
            assertWrittenWith(helper, config.resolve(GooCliqueDump.FILE_CLIQUES), GooCliqueDump.TITLE, start);
        } catch (IOException e) {
            helper.fail(UNREADABLE + e.getMessage());
            return;
        }
        helper.succeed();
    }

    private static void assertWrittenWith(GameTestHelper helper, Path dump, String header, Instant start)
            throws IOException {
        helper.assertTrue(Files.isRegularFile(dump)
                && Files.getLastModifiedTime(dump).toInstant().isAfter(start), UNWRITTEN + dump.getFileName());
        List<String> lines = Files.readAllLines(dump, StandardCharsets.UTF_8);
        helper.assertTrue(lines.stream().anyMatch(line -> line.startsWith(header)),
                dump.getFileName() + HEADER_MISSING + header);
    }
}
