package com.mercuriusxeno.goo.tools.workbench;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the gradle run's dump runner audits the draft: it writes the draft into a folder datapack and
 * names that pack's root on the gradlew command line (decision workbench-authors-a-draft-and-promotes-by-rename).
 * The pack is a folder on disk, so this test writes under a temp folder.
 */
class HeadlessGametestDumpRunnerTest {

    private static final String DRAFT = "{\n    \"minecraft:dirt\": {\"earth\": 2}\n}\n";

    @TempDir
    Path temp;

    @Test
    void draftLandsInAFolderDatapack() throws IOException {
        Path draft = temp.resolve("base_values.draft.json");
        Files.writeString(draft, DRAFT);
        HeadlessGametestDumpRunner runner = new HeadlessGametestDumpRunner(temp.resolve("repo"));
        Path packs = runner.writeDraftPacks(draft);
        Path pack = packs.resolve("goo_values_draft");
        assertEquals(DRAFT, Files.readString(pack.resolve("data/goo/goo_values/base_values.json")));
        assertTrue(JsonParser.parseString(Files.readString(pack.resolve("pack.mcmeta"))).getAsJsonObject()
                .getAsJsonObject("pack").has("min_format"));
        assertTrue(packs.startsWith(temp.resolve("repo/runs/gameTestServer")), packs.toString());
    }

    @Test
    void commandNamesThePacksRoot() {
        Path packs = temp.resolve("packs");
        List<String> command = new HeadlessGametestDumpRunner(temp).command(packs);
        assertTrue(command.contains("-PgameTestPacks=" + packs), command.toString());
        assertTrue(command.contains("-PgameTestSelect=goo:values_dumps"), command.toString());
    }
}
