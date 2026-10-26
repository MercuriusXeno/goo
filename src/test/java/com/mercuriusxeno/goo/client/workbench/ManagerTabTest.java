package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the tab bar's labels and their order (decisions goo-workbench-opens-the-in-game-manager-screen,
 * values-checks-go-with-the-checks-tab).
 */
class ManagerTabTest {

    /**
     * The tab bar reads Items, Groups, Prefabs, Mutators, Audit, the page's Values, Variables and Processes renamed,
     * with no Checks tab.
     */
    @Test
    void tabBarReadsPlainLabelsInOrder() {
        assertEquals(List.of("Items", "Groups", "Prefabs", "Mutators", "Audit"),
                Arrays.stream(ManagerTab.values()).map(ManagerTab::label).toList());
    }
}
