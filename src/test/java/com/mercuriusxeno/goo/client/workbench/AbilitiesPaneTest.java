package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the Abilities tab's pane through drawn frames of a recorded model, with no Screen: each ability's display name
 * heads its numeric leaves, each leaf a row of the one builder frame, and a cost naming an item holding none of the
 * ability's goo type reads a red line under its row (decisions
 * abilities-tab-lists-every-numeric-param-as-expression-boxes, ability-cost-builds-as-a-one-type-by-type-value).
 */
class AbilitiesPaneTest {
    private static final GroupsPane.Bounds VIEW = new GroupsPane.Bounds(0, 0, 500, 400);
    private static final String NAME_KEY = "goo.ability.unstable.lurker";
    private static final String NAME = "Lurker";
    private static final String MODEL = """
            {"abilities": [{"id": "goo:unstable_lurker", "gooType": "unstable", "displayName": "%s", "draft": false,
              "leaves": [
                {"path": "cost", "value": "3 * minecraft:cobblestone", "kind": "COST"},
                {"path": "behaviors/0/amount", "value": "health / 2", "kind": "EXPRESSION"},
                {"path": "behaviors/1/range", "value": 16, "kind": "NUMBER"}
              ]}]}
            """.formatted(NAME_KEY);

    private static AbilitiesPane pane(Map<String, Map<String, String>> worths) {
        AbilitiesTab tab = new AbilitiesTab();
        tab.readModel(MODEL);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
        ExpressionBuilderPane builder = new ExpressionBuilderPane(actions, icons, List::of, List::of);
        return new AbilitiesPane(tab, actions, icons, builder, key -> NAME_KEY.equals(key) ? NAME : key,
                ref -> worths.getOrDefault(ref.id(), Map.of()), java.util.Set::of);
    }

    private static DrawnFrame draw(AbilitiesPane pane) {
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), VIEW, frame.targets);
        return frame;
    }

    @Test
    void theDisplayNameHeadsEachLeafsRow() {
        DrawnFrame frame = draw(pane(Map.of("minecraft:cobblestone", Map.of("unstable", "250"))));
        int heading = frame.find(NAME).y();
        for (String label : List.of("cost", "behaviors/0/amount", "behaviors/1/range")) {
            assertTrue(frame.find(label).y() > heading, label + " stands above the heading");
        }
        assertTrue(frame.texts().contains("health / 2"), frame.texts().toString());
        assertTrue(frame.texts().contains("16"), frame.texts().toString());
    }

    @Test
    void aCostItemHoldingNoneOfTheTypeReadsARedLine() {
        DrawnFrame frame = draw(pane(Map.of("minecraft:cobblestone", Map.of("rock", "250"))));
        DrawnFrame.Text error = frame.find("minecraft:cobblestone holds no unstable");
        assertEquals(Palette.DANGER, error.color());
        assertTrue(error.y() > frame.find("cost").y());
    }

    @Test
    void aCostItemHoldingTheTypeReadsNoLine() {
        DrawnFrame frame = draw(pane(Map.of("minecraft:cobblestone", Map.of("unstable", "250"))));
        assertTrue(frame.drawn().stream().noneMatch(text -> text.color() == Palette.DANGER), frame.texts().toString());
    }
}
