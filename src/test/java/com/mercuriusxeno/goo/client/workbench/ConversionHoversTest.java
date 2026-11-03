package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.Processes;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests what a Conversions row's controls hover: a convert its recipe as icons and numbers, an additive its prefab's
 * formula, and a stack the mutator it applies with every amount multiplied by its count (decisions
 * conversion-controls-hover-their-formula, every-linkable-thing-hovers-its-preview-value).
 */
class ConversionHoversTest {
    private static final String SECTION = "_conversions";
    private static final String MODEL = """
            {"path": "base_values.json", "members": [
              {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                 {"key": "sprout", "keySeparator": null, "kind": "VALUE", "text": "{\\"leaf\\": \\"2\\"}",
                  "entries": [], "leading": null, "trailing": null, "closingGap": null}],
               "leading": null, "trailing": null, "closingGap": null}
            ]}
            """;
    private static final String VALUES = """
            {"constants": [
              {"name": "sprout", "kind": "tree", "tree": {"leaf": "2"}, "value": null, "error": null,
               "usedBy": [], "readers": []}
            ]}
            """;
    private final ProcessesTab processes = mock(ProcessesTab.class);
    private final VariablesTab variables;

    ConversionHoversTest() {
        ManagerDraft draft = new ManagerDraft();
        draft.read(MODEL);
        variables = new VariablesTab(draft);
        variables.readEvaluated(VALUES);
    }

    private List<TooltipLine> hover(String key, Processes.Form form) {
        when(processes.formOf(SECTION, key)).thenReturn(form);
        return ConversionHovers.tooltipFor(processes, variables, ConversionHovers.hoverKey(SECTION, key))
                .orElseThrow();
    }

    private static ExpressionLines.Run type(String type) {
        return new ExpressionLines.Run(ExpressionLines.Kind.TYPE, "", type);
    }

    private static ExpressionLines.Run number(String digits) {
        return new ExpressionLines.Run(ExpressionLines.Kind.NUMBER, digits, null);
    }

    private static ExpressionLines.Run symbol(String symbol) {
        return new ExpressionLines.Run(ExpressionLines.Kind.OPERATOR, symbol, null);
    }

    @Test
    void convertReadsItsSourceDivisorArrowThenItsMultipliedTarget() {
        assertEquals(List.of(new TooltipLine.Runs(List.of(type("metal"), symbol("/"), number("3"), symbol("=>"),
                number("2"), type("yore")))), hover("smelt", new Processes.Formula("metal", 3, "yore", 2, 1)));
    }

    @Test
    void convertToNothingReadsTimesZero() {
        assertEquals(List.of(new TooltipLine.Runs(List.of(type("metal"), symbol("=>"), number("x0")))),
                hover("burn", new Processes.Formula("metal", 1, "0", 1, 1)));
    }

    @Test
    void additiveHoversItsPrefabsFormula() {
        assertEquals(PrefabHovers.tooltipFor(variables, PrefabHovers.hoverKey("sprout")).orElseThrow(),
                hover("grow", new Processes.Additive("sprout")));
    }

    @Test
    void stackOfThreeOverAnAdditiveOfTwoLeafReadsSixLeaf() {
        when(processes.formOf(SECTION, "grow")).thenReturn(new Processes.Additive("sprout"));
        assertEquals(List.of(new TooltipLine.GooAmount("leaf", "6")),
                hover("thrice", new Processes.Stack(3, "grow")));
    }

    @Test
    void stackOverAConvertMultipliesBothSides() {
        when(processes.formOf(SECTION, "smelt")).thenReturn(new Processes.Formula("metal", 3, "yore", 2, 1));
        assertEquals(List.of(new TooltipLine.Runs(List.of(number("4"), type("metal"), symbol("/"), number("3"),
                symbol("=>"), number("8"), type("yore")))), hover("smelt4", new Processes.Stack(4, "smelt")));
    }
}
