package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.ArmPose;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.DistancePrice;
import com.mercuriusxeno.goo.ability.IndicatorShowing;
import com.mercuriusxeno.goo.client.hud.PanelRow;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.network.CostMechanicsLookup;
import com.mercuriusxeno.goo.data.GooValueTable;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The panel explaining the hovered ability: its rows and where it stands.
 * decision explanation-panel-draws-right-of-the-wheel
 */
class AbilityExplanationPanelTest {

    private static final String NAME = "ability.gootest.word";
    private static final String DESCRIPTION = "ability.gootest.word.description";
    private static final String COST_MECHANICS = "ability.gootest.mechanics";
    /** Wide enough that no test line wraps. */
    private static final int TEXT_WIDTH = 1000;
    private static final int CENTER_X = 1200;
    private static final int RADIUS = 1000;

    /** A lang reading every key back as written. */
    private static final CostMechanicsLookup LOOKUP = new CostMechanicsLookup(key -> key, () -> GooValueTable.EMPTY);

    private static ClientAbility explaining(Optional<String> costMechanics) {
        return new ClientAbility(Identifier.fromNamespaceAndPath("gootest", "word"), NAME, "", 0, List.of(),
                List.of(), 0, Delivery.ARC, AbilityBadge.WORLD, List.of(), AbilityArea.NONE, IndicatorShowing.HELD,
                List.of(), AbilityDefinition.NO_UPKEEP, DistancePrice.NONE, List.of(),
                ArmPose.defaultFor(DeliveryKind.ARC), DESCRIPTION, costMechanics);
    }

    private static List<String> rowTexts(ClientAbility ability) {
        return AbilityExplanationPanel.rows(ability, LOOKUP, TEXT_WIDTH, String::length).stream()
                .map(row -> row.segments().stream().map(PanelRow.TextSegment::text).collect(Collectors.joining()))
                .toList();
    }

    @Test
    void anAbilityNamingCostMechanicsReadsItsNameDescriptionAndCostMechanics() {
        assertEquals(List.of(NAME, DESCRIPTION, COST_MECHANICS), rowTexts(explaining(Optional.of(COST_MECHANICS))));
    }

    @Test
    void aPlainCostAbilityReadsItsNameAndDescription() {
        assertEquals(List.of(NAME, DESCRIPTION), rowTexts(explaining(Optional.empty())));
    }

    @Test
    void thePanelStandsRightOfTheWheelsOuterRadius() {
        RadialWheelRenderer.Frame frame = new RadialWheelRenderer.Frame(new RadialWheel(1, type -> 0), List.of(),
                List.of(), Map.of(), CENTER_X, CENTER_X, RADIUS, null, 0f);

        int left = AbilityExplanationPanel.panelLeft(frame);

        assertTrue(left > CENTER_X + RADIUS, "panel left " + left + ", wheel right " + (CENTER_X + RADIUS));
    }
}
