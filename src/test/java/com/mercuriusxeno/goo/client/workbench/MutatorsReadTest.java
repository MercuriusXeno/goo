package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.Processes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that a Mutators row reads Valid or Invalid with its mark, and that a multiply-and-divide factor reads as a
 * ratio, each number its own box, with mocked graphics and no Screen (decisions
 * mutators-read-valid-or-invalid-with-a-mark, factor-reads-as-a-ratio).
 */
class MutatorsReadTest {
    private static final String RATIO = ":";

    private static Font font() {
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        return font;
    }

    // Every string the graphics drew, in order.
    private static List<String> drawn(GuiGraphicsExtractor graphics) {
        ArgumentCaptor<String> texts = ArgumentCaptor.forClass(String.class);
        verify(graphics, atLeastOnce()).text(any(Font.class), texts.capture(), anyInt(), anyInt(), anyInt());
        return texts.getAllValues();
    }

    /**
     * Valid reads a green check and Invalid a red x.
     */
    @Test
    void verdictReadsItsMark() {
        ProcessesPane.VerdictMark valid = ProcessesPane.verdictMark(new ProcessesTab.ProcessRow("rust", "formula",
                "metal / 4 -> yore / 9", true, "Valid", ""));
        assertEquals("✔", valid.glyph());
        assertEquals(ProcessesPane.GREEN, valid.color());
        ProcessesPane.VerdictMark invalid = ProcessesPane.verdictMark(new ProcessesTab.ProcessRow("broken",
                "formula", "metal ->", false, "Invalid", "Formula lacks a right side"));
        assertEquals("✘", invalid.glyph());
        assertEquals(ProcessesPane.RED, invalid.color());
    }

    /**
     * An assignment's scale lands number, colon, number, and neither "times" nor "over".
     */
    @Test
    void assignmentScaleReadsAsARatio() {
        ProcessesTab processes = mock(ProcessesTab.class);
        when(processes.formOf(anyString(), anyString())).thenReturn(new Processes.Assignment("logs", 1, 1,
                List.of()));
        Supplier<List<String>> none = List::of;
        GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
        new MutatorControls(processes, new BuilderOverlays(mock(ManagerActions.class), null), none, none, () -> { })
                .draw(graphics, font(), "_post_conversions", "#logs", new GroupsPane.Bounds(0, 0, 600, 400), 0,
                        new ClickTargets());
        List<String> texts = drawn(graphics);
        assertTrue(Collections.indexOfSubList(texts, List.of("1", RATIO, "1")) >= 0, texts.toString());
        assertFalse(texts.contains("times") || texts.contains("over"), texts.toString());
    }

    /**
     * The builder's factor reads as a ratio too: times 3 reads 3:1 and divided by 4 reads 1:4.
     */
    @Test
    void builderFactorReadsAsARatio() {
        assertEquals(List.of("3", RATIO, "1"), builderRatio("3 $waxed"));
        assertEquals(List.of("1", RATIO, "4"), builderRatio("$waxed / 4"));
    }

    private static List<String> builderRatio(String shown) {
        ExpressionBuilderPane pane = new ExpressionBuilderPane(mock(ManagerActions.class),
                new ItemIcons(id -> Map.of()), List::of, () -> List.of(new BuilderGrammar.Prefab("waxed", true)));
        pane.open(shown, typed -> { }, () -> { });
        GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
        pane.extract(graphics, font(), new GroupsPane.Bounds(0, 0, 600, 400), 0, new ClickTargets());
        List<String> texts = drawn(graphics);
        int colon = texts.indexOf(RATIO);
        assertTrue(colon > 0, texts.toString());
        assertFalse(texts.contains("times") || texts.contains("divided by"), texts.toString());
        return texts.subList(colon - 1, colon + 2);
    }
}
