package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.Processes;
import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the words an assignment's chain of mutators draws and the kind line's dropdown, with mocked graphics and no
 * Screen (decisions expression-builder-composes-every-form-without-typing, exclusive-choice-sets-draw-as-dropdowns).
 */
class MutatorControlsTest {
    // The expression builder's lettering on the mocked font, which the rows letter at (decision
    // conversions-rows-letter-at-the-builders-scale).
    private static final Lettering LETTERS = new Lettering(DrawnFrame.font(), ControlFlow.BUILDER_SCALE);

    /**
     * A process's kind line draws one dropdown reading its kind; picking another kind writes that kind.
     */
    @Nested
    class KindDropdown {
        private static final String SECTION = "_conversions";
        private static final String KEY = "rust";
        private final ProcessesTab processes = mock(ProcessesTab.class);
        private final BuilderOverlays overlays = new BuilderOverlays(mock(ManagerActions.class), null);
        private final MutatorControls controls = new MutatorControls(processes, overlays, mock(ManagerActions.class),
                () -> List.of("tree"), () -> List.of("metal"), () -> { });

        KindDropdown() {
            when(processes.formOf(anyString(), anyString())).thenReturn(new Processes.Additive("tree"));
            when(processes.processNames(SECTION)).thenReturn(List.of(KEY, "smelt"));
        }

        private DrawnFrame frame() {
            DrawnFrame frame = new DrawnFrame();
            controls.draw(frame.graphics, DrawnFrame.font(), SECTION, KEY, new GroupsPane.Bounds(0, 0, 590, 390), 0,
                    frame.targets);
            overlays.draw(frame.graphics, DrawnFrame.font(), frame.targets);
            return frame;
        }

        @Test
        void kindLineDrawsOneButtonReadingTheChosenKind() {
            List<String> texts = frame().texts();
            assertTrue(texts.contains(ControlFlow.dropdownLabel("add")), texts.toString());
            assertFalse(texts.contains("convert") || texts.contains("stack"), texts.toString());
        }

        @Test
        void pickingConvertWritesAFormula() {
            frame().click(ControlFlow.dropdownLabel("add"));
            DrawnFrame open = frame();
            assertTrue(open.texts().containsAll(List.of("convert", "add", "stack")), open.texts().toString());
            open.click("convert");
            verify(processes).setForm(SECTION, KEY, new Processes.Formula("metal", 1, "0", 1, 1));
            assertFalse(overlays.listOpen());
        }

        private void pickTheChosenKind(Processes.Form form, String kind) {
            when(processes.formOf(SECTION, KEY)).thenReturn(form);
            frame().click(ControlFlow.dropdownLabel(kind));
            frame().click(kind);
            verify(processes, never()).setForm(anyString(), anyString(), any());
            assertFalse(overlays.listOpen());
        }

        @Test
        void pickingConvertOnAFormulaKeepsItsTerms() {
            pickTheChosenKind(new Processes.Formula("metal", 4, "yore", 2, 1), "convert");
        }

        @Test
        void pickingAdditiveOnAnAdditiveKeepsItsPrefab() {
            pickTheChosenKind(new Processes.Additive("oak"), "add");
        }

        @Test
        void pickingStackOnAStackKeepsItsCountAndProcess() {
            pickTheChosenKind(new Processes.Stack(3, "smelt"), "stack");
        }
    }

    /**
     * Each form's sentence reads in symbols: a formula as two ratios, an additive as +, a stack as x[n]
     * (decision mutator-sentences-read-in-symbols).
     */
    @Nested
    class Sentences {
        private static final String SECTION = "_conversions";
        private final ProcessesTab processes = mock(ProcessesTab.class);
        private final BuilderOverlays overlays = mock(BuilderOverlays.class);
        private final MutatorControls controls = new MutatorControls(processes, overlays, mock(ManagerActions.class),
                () -> List.of("oak"), () -> List.of("metal"), () -> { });

        Sentences() {
            when(processes.processNames(SECTION)).thenReturn(List.of("rust", "smelt"));
        }

        private DrawnFrame frame(Processes.Form form) {
            when(processes.formOf(SECTION, "rust")).thenReturn(form);
            DrawnFrame frame = new DrawnFrame();
            controls.draw(frame.graphics, DrawnFrame.font(), SECTION, "rust", new GroupsPane.Bounds(0, 0, 590, 390), 0,
                    frame.targets);
            return frame;
        }

        private List<String> sentence(Processes.Form form) {
            return frame(form).texts();
        }

        @Test
        void formulaReadsAsTwoRatios() {
            // Decision mutator-body-indents-under-its-name-line: the arrow => in place of the word becomes.
            assertEquals(List.of(ControlFlow.dropdownLabel("convert"), "1", ":", "4", "=>", "2", ":", "3"),
                    sentence(new Processes.Formula("metal", 4, "yore", 2, 3)));
        }

        /**
         * A convert's ratio colons stand one clear pixel from what stands each side of them: the source side's "1" and
         * its divisor box, the target side's multiplier box and its divisor box (decision
         * ratio-colon-takes-the-builders-spacing).
         */
        @Test
        void ratioColonsStandOneClearPixelFromEachSide() {
            DrawnFrame frame = frame(new Processes.Formula("metal", 4, "yore", 3, 2));
            List<DrawnFrame.Text> colons = frame.drawn().stream().filter(text -> text.text().equals(":")).toList();
            assertEquals(2, colons.size(), frame.drawn().toString());
            int colonWidth = LETTERS.width(":");
            DrawnFrame.Text one = frame.find("1");
            DrawnFrame.Text sourceColon = colons.getFirst();
            assertEquals(one.x() + LETTERS.width("1") + 1, sourceColon.x(), frame.drawn().toString());
            assertEquals(sourceColon.x() + colonWidth + 1, frame.buttonAround(frame.find("4"))[0]);
            DrawnFrame.Text targetColon = colons.getLast();
            assertEquals(frame.buttonAround(frame.find("3"))[2] + 1, targetColon.x(), frame.drawn().toString());
            assertEquals(targetColon.x() + colonWidth + 1, frame.buttonAround(frame.find("2"))[0]);
        }

        /**
         * A one-line sentence stands on the expression builder's line (decision
         * conversions-rows-letter-at-the-builders-scale).
         */
        @Test
        void oneLineSentenceTakesTheBuildersLine() {
            when(processes.formOf(SECTION, "rust")).thenReturn(new Processes.Additive("oak"));
            int bottom = controls.draw(new DrawnFrame().graphics, DrawnFrame.font(), SECTION, "rust",
                    new GroupsPane.Bounds(0, 0, 590, 390), 0, new ClickTargets());
            assertEquals(ControlFlow.LINE, bottom);
        }

        /**
         * An add row draws its prefab's reduced value after the prefab button: each type's icon beside its amount, the
         * set in parentheses; a prefab resolving to nothing draws no parentheses (decision
         * add-mutator-entry-shows-its-reduced-value).
         */
        @Test
        void addRowDrawsItsPrefabsReducedValue() {
            Map<String, Map<String, String>> values = Map.of("oak", orderedAmounts(), "birch", Map.of());
            MutatorControls valued = new MutatorControls(processes, overlays, mock(ManagerActions.class),
                    () -> List.of("oak", "birch"), () -> List.of("metal"), () -> { },
                    name -> values.getOrDefault(name, Map.of()));
            when(processes.formOf(SECTION, "rust")).thenReturn(new Processes.Additive("oak"));
            DrawnFrame frame = new DrawnFrame();
            valued.draw(frame.graphics, DrawnFrame.font(), SECTION, "rust", new GroupsPane.Bounds(0, 0, 590, 390), 0,
                    frame.targets);
            DrawnFrame.Text prefab = frame.find("$oak");
            int prefabRight = frame.buttonAround(prefab)[2];
            ArgumentCaptor<Integer> iconXs = ArgumentCaptor.forClass(Integer.class);
            verify(frame.graphics, atLeast(0)).blit(any(), any(), iconXs.capture(), anyInt(), anyFloat(), anyFloat(),
                    anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
            List<Integer> icons = iconXs.getAllValues().stream().filter(x -> x > prefabRight).toList();
            assertEquals(2, icons.size(), frame.drawn().toString());
            DrawnFrame.Text rock = frame.drawn().stream().filter(text -> text.text().equals("192")).findFirst()
                    .orElseThrow(() -> new AssertionError(frame.drawn().toString()));
            DrawnFrame.Text yore = frame.drawn().stream().filter(text -> text.text().equals("336")).findFirst()
                    .orElseThrow(() -> new AssertionError(frame.drawn().toString()));
            assertTrue(icons.get(0) < rock.x() && rock.x() < icons.get(1) && icons.get(1) < yore.x(),
                    icons + " " + frame.drawn());
            when(processes.formOf(SECTION, "rust")).thenReturn(new Processes.Additive("birch"));
            DrawnFrame bare = new DrawnFrame();
            valued.draw(bare.graphics, DrawnFrame.font(), SECTION, "rust", new GroupsPane.Bounds(0, 0, 590, 390), 0,
                    bare.targets);
            assertTrue(bare.drawn().stream().noneMatch(text -> text.text().contains("(")), bare.drawn().toString());
        }

        /**
         * A stack applying an add twice draws the stack's total on the row through the amount grid: a prefab reducing
         * to 3 metal reads 6 metal (decision prefab-hovers-its-card-and-the-row-shows-the-stack-preview).
         */
        @Test
        void stackOverAnAddDrawsTheStacksTotal() {
            MutatorControls valued = new MutatorControls(processes, overlays, mock(ManagerActions.class),
                    () -> List.of("oak"), () -> List.of("metal"), () -> { },
                    name -> Map.of("oak", Map.of("metal", "3")).getOrDefault(name, Map.of()));
            when(processes.formOf(SECTION, "grow")).thenReturn(new Processes.Additive("oak"));
            when(processes.formOf(SECTION, "rust")).thenReturn(new Processes.Stack(2, "grow"));
            DrawnFrame frame = new DrawnFrame();
            valued.draw(frame.graphics, DrawnFrame.font(), SECTION, "rust", new GroupsPane.Bounds(0, 0, 590, 390), 0,
                    frame.targets);
            List<String> texts = frame.texts();
            List<String> cells = GooAmountGrid.reduced(Map.of("metal", "6")).inlineRuns().stream()
                    .flatMap(List::stream).map(ExpressionLines.Run::text).filter(text -> !text.isEmpty()).toList();
            assertEquals(cells, texts.subList(texts.indexOf("2") + 1, texts.size()), texts.toString());
            assertTrue(cells.contains("6"), cells.toString());
        }

        private static Map<String, String> orderedAmounts() {
            Map<String, String> amounts = new LinkedHashMap<>();
            amounts.put("rock", "192");
            amounts.put("yore", "336");
            return amounts;
        }

        @Test
        void formulaToNothingReadsArrowNothing() {
            assertEquals(List.of(ControlFlow.dropdownLabel("convert"), "1", ":", "4", "=>", "nothing"),
                    sentence(new Processes.Formula("metal", 4, "0", 1, 1)));
        }

        @Test
        void additiveReadsAddThenPlusItsPrefab() {
            assertEquals(List.of(ControlFlow.dropdownLabel("add"), "+", "$oak"),
                    sentence(new Processes.Additive("oak")));
        }

        // Decision mutator-row-is-kind-then-type-reading-add-and-no-applies: [stack v] then the mutator.
        @Test
        void stackReadsItsKindThenItsMutatorTimesItsCount() {
            assertEquals(List.of(ControlFlow.dropdownLabel("stack"), "smelt", "x", "2"),
                    sentence(new Processes.Stack(2, "smelt")));
        }

        /**
         * Each row's kind control hovers the row's formula, an additive and a stack alike (decision
         * conversion-controls-hover-their-formula).
         */
        @Test
        void everyRowsKindControlHoversItsFormula() {
            for (Processes.Form form : List.of(new Processes.Formula("metal", 4, "yore", 2, 3),
                    new Processes.Additive("oak"), new Processes.Stack(2, "smelt"))) {
                DrawnFrame frame = frame(form);
                DrawnFrame.Text kind = frame.drawn().getFirst();
                assertEquals(Optional.of(ConversionHovers.hoverKey(SECTION, "rust")),
                        frame.targets.hovered(kind.x() + 1, kind.y() + 1), form.toString());
            }
        }

        /**
         * A stack's link to the mutator it applies hovers that mutator's formula, as an additive's prefab link hovers
         * its value (decision every-linkable-thing-hovers-its-preview-value).
         */
        @Test
        void stacksMutatorLinkHoversThatMutatorsFormula() {
            DrawnFrame frame = frame(new Processes.Stack(2, "smelt"));
            DrawnFrame.Text applied = frame.find("smelt");
            assertEquals(Optional.of(ConversionHovers.hoverKey(SECTION, "smelt")),
                    frame.targets.hovered(applied.x() + 1, applied.y() + 1));
        }

        /**
         * A row starting at x 40 draws its kind dropdown at 40, unindented, then the source type's button, then the
         * ratio (decision mutator-row-is-kind-then-type-reading-add-and-no-applies).
         */
        @Test
        void kindStandsAtTheRowsLeftBeforeTheType() {
            when(processes.formOf(SECTION, "rust")).thenReturn(new Processes.Formula("metal", 4, "yore", 2, 3));
            DrawnFrame frame = new DrawnFrame();
            controls.draw(frame.graphics, DrawnFrame.font(), SECTION, "rust", new GroupsPane.Bounds(40, 0, 590, 390),
                    0, frame.targets);
            String kind = ControlFlow.dropdownLabel("convert");
            DrawnFrame.Text drawnKind = frame.find(kind);
            assertEquals(40, frame.buttonAround(drawnKind)[0], frame.drawn().toString());
            DrawnFrame.Text share = frame.find("1");
            assertEquals(drawnKind.y(), share.y(), "the kind and the ratio share the line");
            assertTrue(share.x() > 40 + ButtonPainter.width(LETTERS, kind) + GooTypeIcons.sizeOf("metal"),
                    "the type's button stands between the kind and the ratio: " + frame.drawn());
        }

        @Test
        void kindOptionsReadConvertAddAndStack() {
            DrawnFrame kind = frame(new Processes.Additive("oak"));
            kind.click(ControlFlow.dropdownLabel("add"));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<BuilderOverlays.Option>> options = ArgumentCaptor.forClass(List.class);
            verify(overlays).openOptions(any(), options.capture());
            assertEquals(List.of("convert", "add", "stack"),
                    options.getValue().stream().map(BuilderOverlays.Option::label).toList());
        }

        // Clicks a number's segment and commits the typed text through the builder's mixed-number box it opened on
        // the number's own text (decision conversions-sentences-are-built-from-the-builders-controls).
        private void commitTyped(DrawnFrame frame, String label, String typed) {
            frame.click(label);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<String>> commits = ArgumentCaptor.forClass(Consumer.class);
            verify(overlays, atLeastOnce()).openMixedNumber(any(), eq(label), commits.capture());
            commits.getValue().accept(typed);
        }

        /**
         * A convert's multiplier takes a mixed number: 2 1/2 over a divisor of 1 writes the ratio 5 : 2, and over a
         * divisor of 2 writes 5 : 4 (decision conversions-sentences-are-built-from-the-builders-controls).
         */
        @Test
        void multiplierTakesAMixedNumber() {
            commitTyped(frame(new Processes.Formula("metal", 4, "yore", 3, 1)), "3", "2 1/2");
            verify(processes).setFormulaRatio(SECTION, "rust", 5, 2);
        }

        @Test
        void multiplierOverADivisorFoldsTheFractionIn() {
            commitTyped(frame(new Processes.Formula("metal", 4, "yore", 3, 2)), "3", "2 1/2");
            verify(processes).setFormulaRatio(SECTION, "rust", 5, 4);
        }

        /**
         * A stack's count is the same mixed-number box, taking a whole count and refusing a fraction it cannot hold.
         */
        @Test
        void stackCountTakesAWholeAndRefusesAFraction() {
            commitTyped(frame(new Processes.Stack(2, "smelt")), "2", "1/2");
            verify(processes, never()).setStackCount(anyString(), anyString(), anyInt());
            commitTyped(frame(new Processes.Stack(2, "smelt")), "2", "3");
            verify(processes).setStackCount(SECTION, "rust", 3);
        }

        /**
         * Every number in a sentence draws as a builder segment 14 tall on the builder's line: its box spans the
         * line from top to bottom.
         */
        @Test
        void numbersDrawAsBuilderSegmentsFourteenTall() {
            DrawnFrame frame = frame(new Processes.Formula("metal", 4, "yore", 3, 2));
            for (String number : List.of("4", "3", "2")) {
                int[] box = frame.buttonAround(frame.find(number));
                assertEquals(ButtonPainter.HEIGHT, box[3] - box[1], number);
            }
            assertEquals(14, ButtonPainter.HEIGHT);
        }

        @Test
        void sourceDivisorRefusesZero() {
            commitTyped(frame(new Processes.Formula("metal", 4, "yore", 2, 3)), "4", "0");
            verify(processes, never()).setFormulaDivisor(anyString(), anyString(), anyInt());
        }

        @Test
        void targetDivisorRefusesZero() {
            DrawnFrame frame = frame(new Processes.Formula("metal", 4, "yore", 2, 3));
            commitTyped(frame, "3", "0");
            verify(processes, never()).setFormulaRatio(anyString(), anyString(), anyInt(), anyInt());
        }

        @Test
        void targetDivisorTakesAWholeNumber() {
            commitTyped(frame(new Processes.Formula("metal", 4, "yore", 2, 3)), "3", "5");
            verify(processes).setFormulaRatio(SECTION, "rust", 2, 5);
        }
    }

    /**
     * An iterator reads "copies [#group v]", then "then mutates using [@a] [x]", "and [@b] [x]" and
     * "[+ optional mutation]", its source one dropdown over every group and itself (decisions
     * iterator-line-is-source-ratio-then-chain, iterator-reads-copies-then-mutates-using-and).
     */
    @Nested
    class IteratorLine {
        private static final String SECTION = "_conversions";
        private static final String MODEL = """
                {"path": "base_values.json", "members": [
                  {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "ores", "keySeparator": null, "kind": "VALUE", "text": "[\\"iron_ore\\"]",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "_conversions", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "smelt", "keySeparator": null, "kind": "VALUE", "text": "\\"metal / 2 -> 0\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "rust", "keySeparator": null, "kind": "VALUE", "text": "\\"@smelt\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "#chained", "keySeparator": null, "kind": "VALUE",
                      "text": "\\"#ores @smelt @rust\\"", "entries": [], "leading": null, "trailing": null,
                      "closingGap": null},
                     {"key": "#bare", "keySeparator": null, "kind": "VALUE", "text": "\\"#ores\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}
                ]}
                """;
        private final ProcessesTab processes;
        private final BuilderOverlays overlays = new BuilderOverlays(mock(ManagerActions.class), null);
        private final MutatorControls controls;

        IteratorLine() {
            ManagerDraft draft = new ManagerDraft();
            draft.read(MODEL);
            processes = new ProcessesTab(draft);
            controls = new MutatorControls(processes, overlays, mock(ManagerActions.class), List::of,
                    () -> List.of("metal"), () -> { });
        }

        private DrawnFrame frame(String key) {
            DrawnFrame frame = new DrawnFrame();
            controls.draw(frame.graphics, DrawnFrame.font(), SECTION, key, new GroupsPane.Bounds(0, 0, 590, 390), 0,
                    frame.targets);
            overlays.draw(frame.graphics, DrawnFrame.font(), frame.targets);
            return frame;
        }

        // Decision iterator-reads-copies-then-mutates-using-and.
        @Test
        void sourcedIteratorReadsCopiesThenMutatesUsingAnd() {
            assertEquals(List.of("copies", ControlFlow.dropdownLabel("#ores"), "mutated by", "smelt", "x",
                    "and", "rust", "x", "+ optional mutation"), frame("#chained").texts());
        }

        @Test
        void sourceDropdownListsEveryGroupAndItself() {
            frame("#chained").click(ControlFlow.dropdownLabel("#ores"));
            List<String> open = frame("#chained").texts();
            assertTrue(open.containsAll(List.of("#ores", "itself")), open.toString());
        }

        /**
         * Picking itself on an iterator with no mutators keeps it an assignment, so the line keeps its
         * controls rather than reading 'No form reads this line'.
         */
        @Test
        void pickingItselfWithNoMutatorsKeepsTheControls() {
            frame("#bare").click(ControlFlow.dropdownLabel("#ores"));
            frame("#bare").click("itself");
            assertEquals(new Processes.Assignment("", List.of()), processes.formOf(SECTION, "#bare"));
            assertEquals(List.of("copies", ControlFlow.dropdownLabel("itself"), "+ optional mutation"),
                    frame("#bare").texts());
        }
    }

    /**
     * An iterator row draws "copies" and its source one indent in, with no ratio, and each chained mutator with its x
     * on its own line after "mutated by" or "and", then "+ optional mutation" on a line of its own; two rows
     * with equal chains take equal heights (decisions each-chained-mutator-takes-its-own-indented-line,
     * iterator-reads-copies-then-mutates-using-and, iterator-drops-its-ratio-from-row-and-grammar).
     */
    @Nested
    class ChainLines {
        private static final String SECTION = "_conversions";
        private final ProcessesTab processes = mock(ProcessesTab.class);
        private final MutatorControls controls = new MutatorControls(processes,
                new BuilderOverlays(mock(ManagerActions.class), null), mock(ManagerActions.class), () -> List.of("oak"),
                () -> List.of("metal"), () -> { });
        private int bottom;

        ChainLines() {
            when(processes.processNames(SECTION)).thenReturn(List.of("rust", "smelt", "melt"));
            when(processes.groupNames()).thenReturn(List.of("ores"));
        }

        private DrawnFrame frame(String key, List<String> chain) {
            when(processes.formOf(SECTION, key)).thenReturn(new Processes.Assignment("ores", chain));
            DrawnFrame frame = new DrawnFrame();
            bottom = controls.draw(frame.graphics, DrawnFrame.font(), SECTION, key,
                    new GroupsPane.Bounds(0, 0, 590, 390), 0, frame.targets);
            return frame;
        }

        /**
         * An iterator's words and its chained mutator's label letter at the expression builder's scale (decision
         * conversions-rows-letter-at-the-builders-scale).
         */
        @Test
        void chainWordsAndLabelsLetterAtTheBuildersScale() {
            DrawnFrame frame = frame("#ores", List.of("@smelt"));
            for (String label : List.of("copies", "mutated by", "smelt")) {
                assertEquals(ControlFlow.BUILDER_SCALE, frame.placed(label).scale(), label);
            }
        }

        /**
         * A mutator inside an iterator's chain hovers the formula card its own row hovers (decision
         * mutator-name-hovers-its-formula-wherever-it-stands).
         */
        @Test
        void chainedMutatorHoversItsFormula() {
            DrawnFrame frame = frame("#ores", List.of("@smelt"));
            DrawnFrame.Text smelt = frame.find("smelt");
            assertEquals(Optional.of(ConversionHovers.hoverKey(SECTION, "smelt")),
                    frame.targets.hovered(smelt.x() + 1, smelt.y() + 1));
        }

        @Test
        void eachChainedMutatorTakesItsOwnLine() {
            DrawnFrame frame = frame("#ores", List.of("@smelt", "@melt"));
            DrawnFrame.Text copies = frame.find("copies");
            DrawnFrame.Text source = frame.find(ControlFlow.dropdownLabel("#ores"));
            DrawnFrame.Text then = frame.find("mutated by");
            DrawnFrame.Text and = frame.find("and");
            DrawnFrame.Text smelt = frame.find("smelt");
            DrawnFrame.Text melt = frame.find("melt");
            assertEquals(ControlFlow.INDENT, copies.x(), frame.drawn().toString());
            assertEquals(copies.y(), source.y());
            assertTrue(source.x() > copies.x(), source + " vs " + copies);
            assertFalse(frame.texts().contains(":"), frame.texts().toString());
            // Decision conversions-rows-letter-at-the-builders-scale: the lines stand the builder's pitch apart.
            int pitch = ControlFlow.LINE + ControlFlow.BUILDER_LINE_GAP;
            assertEquals(List.of(source.y() + pitch, then.y() + pitch), List.of(then.y(), and.y()),
                    frame.drawn().toString());
            assertEquals(List.of(then.y(), and.y()), List.of(smelt.y(), melt.y()), frame.drawn().toString());
            assertTrue(smelt.x() > then.x() && melt.x() > and.x(), frame.drawn().toString());
            List<DrawnFrame.Text> removes = frame.drawn().stream().filter(text -> text.text().equals("x")).toList();
            assertEquals(2, removes.size(), frame.drawn().toString());
            assertEquals(smelt.y(), removes.get(0).y());
            assertTrue(removes.get(0).x() > smelt.x());
            assertEquals(melt.y(), removes.get(1).y());
            assertTrue(removes.get(1).x() > melt.x());
            DrawnFrame.Text add = frame.find("+ optional mutation");
            assertEquals(melt.y() + pitch, add.y(), add + " vs " + melt);
            int firstHeight = bottom;
            frame("#gems", List.of("@rust", "@melt"));
            assertEquals(firstHeight, bottom, "two-link chains take equal heights");
        }

        /**
         * At every area width from 150 to 400 the mutated by line, the and line and the + optional mutation line end
         * at one right edge, half the inset the validity marker set before from the area's right, still right packed
         * (decision chain-lines-inset-halves-at-most).
         */
        @Test
        void chainLinesEndHalfTheMarkersInsetFromTheRight() {
            Font font = DrawnFrame.font();
            for (int width = 150; width <= 400; width++) {
                when(processes.formOf(SECTION, "#ores")).thenReturn(new Processes.Assignment("ores",
                        List.of("@smelt", "@melt")));
                DrawnFrame frame = new DrawnFrame();
                controls.draw(frame.graphics, font, SECTION, "#ores", new GroupsPane.Bounds(0, 0, width, 390), 0,
                        frame.targets);
                List<Integer> rights = new ArrayList<>(frame.drawn().stream().filter(text -> text.text().equals("x"))
                        .map(text -> frame.buttonAround(text)[2]).toList());
                rights.add(frame.buttonAround(frame.find("+ optional mutation"))[2]);
                int formerInset = width - ProcessesPane.markerLeft(font, width) + BuilderOverlays.GAP;
                assertEquals(List.of(width - formerInset / 2, width - formerInset / 2, width - formerInset / 2),
                        rights, "at " + width + ": " + frame.drawn());
            }
        }

        /**
         * On each chain line the x stands one gap past its own mutator's button, whatever the other line's name
         * measures, the x's sharing one right edge; and in a 220-wide area a thirty-letter name's mutated by starts
         * at or right of the area's left (decision chain-x-follows-its-mutator-at-one-gap).
         */
        @Test
        void eachXFollowsItsOwnMutatorAtOneGap() {
            String longName = "abcdefghijklmnopqrstuvwxyzabcd";
            when(processes.formOf(SECTION, "#ores")).thenReturn(new Processes.Assignment("ores",
                    List.of("@" + longName, "@ab")));
            DrawnFrame frame = new DrawnFrame();
            controls.draw(frame.graphics, DrawnFrame.font(), SECTION, "#ores", new GroupsPane.Bounds(0, 0, 590, 390), 0,
                    frame.targets);
            List<DrawnFrame.Text> removes = frame.drawn().stream().filter(text -> text.text().equals("x")).toList();
            assertEquals(2, removes.size(), frame.drawn().toString());
            for (String name : List.of(longName, "ab")) {
                DrawnFrame.Text applied = frame.find(name);
                DrawnFrame.Text remove = removes.stream().filter(text -> text.y() == applied.y()).findFirst()
                        .orElseThrow(() -> new AssertionError(name + "'s x in " + frame.drawn()));
                assertEquals(frame.buttonAround(applied)[2] + BuilderOverlays.GAP, frame.buttonAround(remove)[0],
                        name + ": " + frame.drawn());
            }
            assertEquals(frame.buttonAround(removes.get(0))[2], frame.buttonAround(removes.get(1))[2]);
            DrawnFrame narrow = new DrawnFrame();
            controls.draw(narrow.graphics, DrawnFrame.font(), SECTION, "#ores", new GroupsPane.Bounds(0, 0, 220, 390),
                    0, narrow.targets);
            assertTrue(narrow.find("mutated by").x() >= 0, narrow.drawn().toString());
        }
    }

    /**
     * Every group, prefab and mutator name a row draws follows its ref on shift + left click, opens its dropdown on a
     * plain left click and underlines while shift is held
     * (decision diagnose-and-correct-mutator-references-that-do-not-link).
     */
    @Nested
    class ReferencesLink {
        private static final String SECTION = "_conversions";
        private final ProcessesTab processes = mock(ProcessesTab.class);
        private final ManagerActions actions = mock(ManagerActions.class);
        private final BuilderOverlays overlays = new BuilderOverlays(actions, null);
        private final MutatorControls controls = new MutatorControls(processes, overlays, actions,
                () -> List.of("oak"), () -> List.of("metal"), () -> { });

        ReferencesLink() {
            when(processes.processNames(SECTION)).thenReturn(List.of("rust", "smelt"));
            when(processes.groupNames()).thenReturn(List.of("ores"));
        }

        private DrawnFrame frame(String key, Processes.Form form) {
            when(processes.formOf(SECTION, key)).thenReturn(form);
            DrawnFrame frame = new DrawnFrame();
            controls.draw(frame.graphics, DrawnFrame.font(), SECTION, key, new GroupsPane.Bounds(0, 0, 590, 390), 0,
                    frame.targets);
            return frame;
        }

        private DrawnFrame iterator() {
            return frame("#ores", new Processes.Assignment("ores", List.of("@smelt")));
        }

        @Test
        void shiftClickingAnIteratorsGroupOpensTheGroup() {
            iterator().shiftClick(ControlFlow.dropdownLabel("#ores"));
            verify(actions).openGroup("ores");
        }

        @Test
        void shiftClickingAnIteratorsChainedMutatorOpensTheMutator() {
            iterator().shiftClick("smelt");
            verify(actions).openMutator("smelt");
        }

        @Test
        void shiftClickingAnAdditivesPrefabOpensThePrefab() {
            frame("rust", new Processes.Additive("oak")).shiftClick("$oak");
            verify(actions).openVariable("oak");
        }

        @Test
        void shiftClickingAStacksMutatorOpensTheMutator() {
            frame("rust", new Processes.Stack(2, "smelt")).shiftClick("smelt");
            verify(actions).openMutator("smelt");
        }

        @Test
        void plainClickingAStacksMutatorOpensItsDropdown() {
            frame("rust", new Processes.Stack(2, "smelt")).click("smelt");
            assertTrue(overlays.listOpen());
            verify(actions, never()).openMutator(anyString());
        }

        // The click areas a row registers, watched, for the form drawn under the key.
        private ClickTargets watchedTargets(String key, Processes.Form form) {
            when(processes.formOf(SECTION, key)).thenReturn(form);
            ClickTargets watched = spy(new ClickTargets());
            controls.draw(new DrawnFrame().graphics, DrawnFrame.font(), SECTION, key,
                    new GroupsPane.Bounds(0, 0, 590, 390), 0, watched);
            return watched;
        }

        // The ref's one link area: it carries the ref's card as its own hover, and its shift + left click follows.
        private void assertRefSegment(ClickTargets watched, String hoverKey) {
            ArgumentCaptor<ClickTargets.LinkGestures> gestures =
                    ArgumentCaptor.forClass(ClickTargets.LinkGestures.class);
            verify(watched).addLink(anyInt(), anyInt(), anyInt(), eq(ControlFlow.LINE), gestures.capture(),
                    eq(hoverKey));
            gestures.getValue().follow().run();
        }

        /**
         * An iterator's #group source is the builder's ref segment: one link area carrying the group's card and
         * opening the group on shift + left click (decision
         * conversions-sentences-are-built-from-the-builders-controls).
         */
        @Test
        void groupSourceIsTheBuildersRefSegment() {
            assertRefSegment(watchedTargets("#ores", new Processes.Assignment("ores", List.of("@smelt"))),
                    GroupHovers.hoverKey("ores"));
            verify(actions).openGroup("ores");
        }

        /**
         * An add's $prefab is the builder's ref segment carrying the prefab's card and opening the prefab.
         */
        @Test
        void additivePrefabIsTheBuildersRefSegment() {
            assertRefSegment(watchedTargets("rust", new Processes.Additive("oak")), PrefabHovers.hoverKey("oak"));
            verify(actions).openVariable("oak");
        }

        /**
         * A convert's goo types are the builder's type icon buttons, each naming its pick on hover.
         */
        @Test
        void goosTypesAreTheBuildersTypeIcons() {
            ClickTargets watched = watchedTargets("rust", new Processes.Formula("metal", 4, "yore", 2, 3));
            for (String type : List.of("metal", "yore")) {
                verify(watched).add(anyInt(), anyInt(), anyInt(), eq(ControlFlow.LINE), any(Runnable.class),
                        eq(GooTypeIcons.pickHoverKey(type)));
            }
        }

        @Test
        void holdingShiftUnderlinesEveryReference() {
            DrawnFrame stack = new DrawnFrame().holdingShift();
            when(processes.formOf(SECTION, "rust")).thenReturn(new Processes.Stack(2, "smelt"));
            controls.draw(stack.graphics, DrawnFrame.font(), SECTION, "rust", new GroupsPane.Bounds(0, 0, 590, 390), 0,
                    stack.targets);
            assertTrue(stack.underlined("smelt"));
        }
    }
}
