package com.mercuriusxeno.goo.command;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests /goo workbench's open seam: a player gets the open payload, and a source with no player is told where the
 * manager opens (decision goo-workbench-opens-the-in-game-manager-screen).
 */
class WorkbenchCommandTest {

    /**
     * A player source is sent the open payload and told the manager is opening.
     */
    @Test
    void playerSourceIsSentTheOpenPayload() {
        List<String> opened = new ArrayList<>();
        List<String> told = new ArrayList<>();
        assertEquals(1, WorkbenchCommand.openFor("Xeno", opened::add, told::add));
        assertEquals(List.of("Xeno"), opened);
        assertEquals(List.of(WorkbenchCommand.MSG_OPENING), told);
    }

    /**
     * A console source opens nothing and is told the manager opens from an opped client.
     */
    @Test
    void consoleSourceIsToldTheManagerOpensFromAClient() {
        List<String> opened = new ArrayList<>();
        List<String> told = new ArrayList<>();
        assertEquals(0, WorkbenchCommand.<String>openFor(null, opened::add, told::add));
        assertEquals(List.of(), opened);
        assertEquals(List.of(WorkbenchCommand.MSG_NEEDS_CLIENT), told);
    }
}
