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
     * The tab bar reads Items, #Groups, $Prefabs, Conversions, Audit, Abilities, the page's Values, Variables and
     * Processes renamed, with no Checks tab (decisions conversions-is-the-one-section-and-the-pane-name,
     * hash-marks-groups-and-dollar-marks-prefabs-everywhere,
     * abilities-tab-lists-every-numeric-param-as-expression-boxes).
     */
    @Test
    void tabBarReadsItsLabelsInOrderGroupsAndPrefabsMarked() {
        assertEquals(List.of("Items", "#Groups", "$Prefabs", "Conversions", "Audit", "Abilities"),
                Arrays.stream(ManagerTab.values()).map(ManagerTab::label).toList());
    }
}
