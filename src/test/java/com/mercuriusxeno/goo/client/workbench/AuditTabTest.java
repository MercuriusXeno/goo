package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Audit tab's state from recorded audit and dump-run replies, with no Screen (decisions
 * goo-workbench-opens-the-in-game-manager-screen, workbench-shows-each-roots-remainders-and-items-solved,
 * workbench-lists-type-to-type-paths-worst-first, workbench-traces-each-undercut-manual-value-to-its-roots).
 */
class AuditTabTest {

    private static final String AUDIT = """
            {"sections": [
              {"section": "roots", "header": "ROOT REMAINDERS AND ITEMS SOLVED", "flags": [],
               "lines": ["minecraft:cobblestone  remainders 2  items solved 40"]},
              {"section": "paths", "header": "TYPE TO TYPE PATHS, WORST FIRST", "flags": ["rock -> metal at 64x"],
               "lines": ["rock -> metal  64x via minecraft:iron_ore"]},
              {"section": "undercut", "header": "UNDERCUT MANUAL VALUES", "flags": ["minecraft:stick under its roots"],
               "lines": ["minecraft:stick  manual 2 leaf, roots say 3 leaf"], "dump": "goo_audit.txt"}
            ]}
            """;
    private static final String RUN = """
            {"exitCode": 0, "durationSeconds": 65.4, "sections": [
              {"header": "LEAST VALUE PER TYPE", "lines": ["rock 1"], "dump": "goo_audit.txt"}]}
            """;
    private static final String FAILED_RUN = """
            {"exitCode": 3, "durationSeconds": 2.0, "sections": []}
            """;

    /**
     * The flagged sections lead in the order the audit wrote them, then the quiet ones; each carries its header,
     * its flags, its lines and the file it came from.
     */
    @Test
    void flaggedSectionsLeadThenQuietOnes() {
        AuditTab tab = new AuditTab();
        tab.readSections(AUDIT);
        List<AuditTab.AuditSection> sections = tab.sections();
        assertEquals(List.of("TYPE TO TYPE PATHS, WORST FIRST", "UNDERCUT MANUAL VALUES",
                "ROOT REMAINDERS AND ITEMS SOLVED"), sections.stream().map(AuditTab.AuditSection::header).toList());
        assertEquals("1 flag", sections.getFirst().badge());
        assertEquals(List.of("rock -> metal at 64x"), sections.getFirst().flags());
        assertEquals("goo_audit.txt", sections.get(1).source());
        assertEquals("goo_audit.json", sections.get(2).source());
        assertEquals(List.of("minecraft:cobblestone  remainders 2  items solved 40"), sections.get(2).lines());
        assertEquals("", sections.get(2).badge());
    }

    /**
     * A run keeps the latest progress lines in arrival order, then its result names the exit and the duration and
     * replaces the sections.
     */
    @Test
    void runKeepsLatestProgressThenReadsItsResult() {
        AuditTab tab = new AuditTab();
        tab.startRun();
        assertTrue(tab.running());
        IntStream.range(0, AuditTab.PROGRESS_LINES_SHOWN + 5).forEach(line -> tab.progress("line " + line));
        assertEquals(AuditTab.PROGRESS_LINES_SHOWN, tab.progressLines().size());
        assertEquals("line 5", tab.progressLines().getFirst());
        assertEquals("line " + (AuditTab.PROGRESS_LINES_SHOWN + 4), tab.progressLines().getLast());
        tab.finishRun(RUN);
        assertFalse(tab.running());
        assertEquals("dumps refreshed · 1 m 5 s", tab.runLine());
        assertEquals(List.of("LEAST VALUE PER TYPE"), tab.sections().stream().map(AuditTab.AuditSection::header)
                .toList());
        tab.finishRun(FAILED_RUN);
        assertEquals("the run failed, exit 3 · 0 m 2 s", tab.runLine());
    }
}
