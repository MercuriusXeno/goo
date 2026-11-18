package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.ArmPose;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.DistancePrice;
import com.mercuriusxeno.goo.ability.IndicatorShowing;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.client.hud.MixedNumberLabel;
import com.mercuriusxeno.goo.client.hud.MixedNumberSpacing;
import com.mercuriusxeno.goo.client.hud.PanelRow;
import com.mercuriusxeno.goo.client.hud.StatedGlyphs;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.network.CostMechanicsLookup;
import com.mercuriusxeno.goo.client.network.ShippedClientAbilities;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.item.MixedNumber;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;
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
        return AbilityExplanationPanel.rows(ability, LOOKUP, TEXT_WIDTH, String::length, Level.OVERWORLD).stream()
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

    /**
     * Goop priced at 64/3 a block names 21 1/3 in its cost mechanics, and the panel lays that number by the
     * spacing rule and measures the row at its respaced width.
     * every-mixed-number-takes-the-spacing-rule
     */
    @Test
    void theCostMechanicsTextLaysItsMixedNumberByTheSpacingRuleAndMeasuresTheRespacedWidth() {
        String before = "Costs ";
        String after = " per block laid.";
        CostMechanicsLookup lang = new CostMechanicsLookup(
                key -> key.equals("goo.ability.weird.goop.cost_mechanics") ? before + "%s" + after : key,
                () -> GooValueTable.EMPTY);
        String json = AbilityJson.read("weird_goop").replaceFirst("\"cost\"\\s*:\\s*\\d+", "\"cost\": \"64 / 3\"");
        ClientAbility goop = ClientAbility.fromEntry(AbilitySyncPayload.gloveEntries(GooTypes.WEIRD,
                List.of(AbilityJson.decodeText("weird_goop", json))).getFirst());
        ToIntFunction<String> spaced = MixedNumberLabel.spacedWidth(StatedGlyphs::widthOf);
        PanelRow mechanics = AbilityExplanationPanel.rows(goop, lang, TEXT_WIDTH, spaced, Level.OVERWORLD).getLast();
        MixedNumberSpacing.Laid laid = MixedNumberSpacing.lay(new MixedNumber("21", "¹", "₃"), StatedGlyphs.VANILLA);
        float numberLeft = StatedGlyphs.widthOf(before);
        List<MixedNumberSpacing.Piece> expected = new ArrayList<>();
        expected.add(new MixedNumberSpacing.Piece(before, 0f));
        laid.pieces().forEach(piece -> expected.add(new MixedNumberSpacing.Piece(piece.text(), numberLeft + piece.x())));
        expected.add(new MixedNumberSpacing.Piece(after, numberLeft + laid.width()));
        int respaced = (int) Math.ceil(numberLeft + laid.width() + StatedGlyphs.widthOf(after));

        assertEquals(expected,
                AbilityExplanationPanel.laySegments(mechanics, StatedGlyphs::widthOf).getFirst().label().pieces());
        assertEquals(respaced, mechanics.width(spaced));
    }

    @Test
    void thePanelStandsRightOfTheWheelsOuterRadius() {
        RadialWheelRenderer.Frame frame = new RadialWheelRenderer.Frame(new RadialWheel(1, type -> 0), List.of(),
                List.of(), Map.of(), CENTER_X, CENTER_X, RADIUS, null, 0f);

        int left = AbilityExplanationPanel.panelLeft(frame);

        assertTrue(left > CENTER_X + RADIUS, "panel left " + left + ", wheel right " + (CENTER_X + RADIUS));
    }

    /**
     * decision lunar-reads-as-terran-while-you-are-on-the-moon
     */
    @Nested
    class OnTheMoon {

        private static final String TERRAN_KEY = "goo.ability.astral.terran";

        @Test
        void thePanelsLabelNamesLunarTerran() {
            List<PanelRow> rows = AbilityExplanationPanel.rows(ShippedClientAbilities.of("astral_lunar"), LOOKUP,
                    TEXT_WIDTH, String::length, Lunar.LEVEL);
            assertEquals(TERRAN_KEY, rows.getFirst().segments().stream().map(PanelRow.TextSegment::text)
                    .collect(Collectors.joining()));
        }
    }
}
