package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the tab bar's labels and their order (decision goo-workbench-opens-the-in-game-manager-screen).
 */
class ManagerTabTest {

    /**
     * The tab bar reads Items, Groups, Prefabs, Mutators, Audit, Checks, the page's Values, Variables and Processes
     * renamed.
     */
    @Test
    void tabBarReadsPlainLabelsInOrder() {
        assertEquals(List.of("Items", "Groups", "Prefabs", "Mutators", "Audit", "Checks"),
                Arrays.stream(ManagerTab.values()).map(ManagerTab::label).toList());
    }
}
