package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.Processes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * One Mutators entry drawn as a sentence of controls, every field the game's conversion parser reads a click away:
 * a process opens its line at the row's left with its kind, convert, add or stack, before its goo type; a formula
 * picks its source and target goo types through the picker, nothing among them for the target, and reads as two
 * ratios, "1 : N" of the source becoming "M : K" of the target, no divisor taking 0; an additive reads "+ prefab" and
 * a stack "mutator xN" (decisions mutator-sentences-read-in-symbols,
 * mutator-row-is-kind-then-type-reading-add-and-no-applies); an assignment picks its source group, or the
 * target's own values, its scale and its chain of processes. Each control names what it does on hover, the sentence
 * wraps inside the pane, and each change writes the entry through Processes' writers (decision
 * expression-builder-composes-every-form-without-typing).
 */
final class MutatorControls {
    private static final int HALF = 2;
    private static final String NOTHING_TARGET = "0";
    private static final String GROUP_MARK = "#";
    private static final String STACK_MARK = "@";
    private static final String NO_SOURCE = "";
    // Decision mutator-sentences-read-in-symbols: the word formula goes.
    private static final String CONVERT = "convert";
    // Decision mutator-row-is-kind-then-type-reading-add-and-no-applies: the additive kind reads add.
    private static final String ADDITIVE = "add";
    private static final String STACK = "stack";
    private static final String SOURCE_SHARE = "1";
    // Decision iterator-reads-copies-then-mutates-using-and, as the operator restated it: the source after copies,
    // the first mutator after mutated by, each later one after and, and the button that adds one naming it optional.
    private static final String COPIES = "copies";
    private static final String MUTATED_BY = "mutated by";
    private static final String AND = "and";
    private static final String ADD_MUTATION = "+ optional mutation";
    // Decision mutator-body-indents-under-its-name-line: the arrow in place of the word becomes.
    private static final String ARROW = "=>";
    private static final String TIMES_MARK = "x";
    private static final String NOTHING = "nothing";
    private static final String PLUS = "+";
    private static final String ITSELF = "itself";
    private static final String RATIO = ":";
    private static final String REMOVE_LINK = "x";
    // The operator's ruling: every X's tooltip reads the one word Delete, and no other control explains itself.
    private static final String REMOVE_LINK_HINT = "Delete";
    private static final String UNREAD = "No form reads this line:";
    private static final String START_OVER = "start over";
    // A stack that applies a stack multiplies through it; past this depth the chain loops and totals nothing.
    private static final int MAX_STACK_DEPTH = 8;

    private final ProcessesTab processes;
    private final BuilderOverlays overlays;
    private final ManagerActions actions;
    private final Supplier<List<String>> treePrefabs;
    private final Supplier<List<String>> gooTypes;
    private final Runnable changed;
    private final Function<String, Map<String, String>> prefabAmounts;

    /**
     * Controls whose add rows draw no reduced value.
     *
     * @param processes   the Mutators tab's state
     * @param overlays    the number box, dropdown and picker the controls open
     * @param actions     where a shift + left click on a group, prefab or mutator name opens it
     * @param treePrefabs the tree prefabs' names, which an additive adds
     * @param gooTypes    the goo types' ids, the first a fresh formula's source
     * @param changed     runs after each change written to the draft
     */
    MutatorControls(ProcessesTab processes, BuilderOverlays overlays, ManagerActions actions,
                    Supplier<List<String>> treePrefabs, Supplier<List<String>> gooTypes, Runnable changed) {
        this(processes, overlays, actions, treePrefabs, gooTypes, changed, prefab -> Map.of());
    }

    /**
     * @param processes     the Mutators tab's state
     * @param overlays      the number box, dropdown and picker the controls open
     * @param actions       where a shift + left click on a group, prefab or mutator name opens it
     * @param treePrefabs   the tree prefabs' names, which an additive adds
     * @param gooTypes      the goo types' ids, the first a fresh formula's source
     * @param changed       runs after each change written to the draft
     * @param prefabAmounts a prefab's reduced value, each goo type with its amount in the order it reads, as
     *                      VariablesTab.prefabValue answers it; empty where it resolves to nothing
     */
    MutatorControls(ProcessesTab processes, BuilderOverlays overlays, ManagerActions actions,
                    Supplier<List<String>> treePrefabs, Supplier<List<String>> gooTypes, Runnable changed,
                    Function<String, Map<String, String>> prefabAmounts) {
        this.processes = processes;
        this.overlays = overlays;
        this.actions = actions;
        this.treePrefabs = treePrefabs;
        this.gooTypes = gooTypes;
        this.changed = changed;
        this.prefabAmounts = prefabAmounts;
    }

    /**
     * Draws the entry's sentence across an area.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param section  the entry's block
     * @param key      the entry's key
     * @param area     the area the sentence wraps inside
     * @param top      its first line's top
     * @param targets  the frame's click areas
     * @return the y below the sentence
     */
    int draw(GuiGraphicsExtractor graphics, Font font, String section, String key, GroupsPane.Bounds area, int top,
             ClickTargets targets) {
        // Decision conversions-rows-letter-at-the-builders-scale: the sentence letters as the expression builder does.
        ControlFlow flow = ControlFlow.lettered(graphics, font, targets, null,
                new ControlFlow.Span(area.left(), area.right(), top), null);
        boolean iterator = ProcessesTab.isIterator(key);
        // Decision mutator-chain-forms-a-column-left-of-the-validity-marker, its inset halved by decision
        // chain-lines-inset-halves-at-most.
        int chainInset = (area.right() - ProcessesPane.markerLeft(font, area.right()) + BuilderOverlays.GAP) / HALF;
        Entry entry = new Entry(flow, section, key, chainInset);
        Processes.Form form = processes.formOf(section, key);
        // Decision mutator-row-is-kind-then-type-reading-add-and-no-applies: a mutator's line starts unindented at
        // the row's left with its kind, then its goo type.
        if (!iterator) {
            drawKind(entry, form);
        }
        drawForm(entry, form, iterator);
        return flow.bottom();
    }

    private void drawForm(Entry entry, Processes.Form form, boolean assignment) {
        switch (form) {
            case Processes.Formula formula -> drawFormula(entry, formula);
            case Processes.Additive additive -> drawAdditive(entry, additive);
            case Processes.Stack stack -> drawStack(entry, stack);
            case Processes.Assignment chain -> drawAssignment(entry, chain);
            case Processes.Unread unread -> drawUnread(entry, unread, assignment);
        }
    }

    /**
     * Where one entry's controls draw.
     *
     * @param chainInset how far left of the area's right edge an iterator's chain lines end, a gap left of the
     *                   row's validity marker
     */
    private record Entry(ControlFlow flow, String section, String key, int chainInset) {
    }

    private void drawKind(Entry entry, Processes.Form form) {
        ControlFlow flow = entry.flow();
        ControlFlow.ChoiceSet kinds = new ControlFlow.ChoiceSet();
        boolean formula = form instanceof Processes.Formula;
        kinds.add(new BuilderOverlays.Option(CONVERT, null, unlessChosen(formula, () -> freshFormula(entry))),
                formula);
        if (!treePrefabs.get().isEmpty()) {
            boolean additive = form instanceof Processes.Additive;
            kinds.add(new BuilderOverlays.Option(ADDITIVE, null, unlessChosen(additive,
                    () -> write(entry, new Processes.Additive(treePrefabs.get().getFirst())))), additive);
        }
        List<String> others = otherProcesses(entry);
        if (!others.isEmpty()) {
            boolean stack = form instanceof Processes.Stack;
            kinds.add(new BuilderOverlays.Option(STACK, null, unlessChosen(stack,
                    () -> write(entry, new Processes.Stack(1, others.getFirst())))), stack);
        }
        flow.choices(kinds, overlays);
        // Decision conversion-controls-hover-their-formula: the kind hovers the row's formula.
        flow.hoverLast(ConversionHovers.hoverKey(entry.section(), entry.key()));
    }

    // Picking the kind the form already is keeps the form, so a misclick wipes nothing.
    private static Consumer<BuilderOverlays.Anchor> unlessChosen(boolean chosen, Runnable fresh) {
        return at -> {
            if (!chosen) {
                fresh.run();
            }
        };
    }

    private void freshFormula(Entry entry) {
        List<String> types = gooTypes.get();
        if (!types.isEmpty()) {
            write(entry, new Processes.Formula(types.getFirst(), 1, NOTHING_TARGET, 1, 1));
        }
    }

    private List<String> otherProcesses(Entry entry) {
        return processes.processNames(entry.section()).stream().filter(name -> !name.equals(entry.key())).toList();
    }

    private void drawFormula(Entry entry, Processes.Formula formula) {
        ControlFlow flow = entry.flow();
        String section = entry.section();
        String key = entry.key();
        flow.typeIcon(formula.source(), at -> overlays.openPicker(at,
                type -> edit(() -> processes.setFormulaSource(section, key, type))));
        // Decision mutator-sentences-read-in-symbols: "[source] 1 : [#] becomes [target] [#] : [#]".
        flow.text(SOURCE_SHARE, Palette.MUTED);
        // Decision ratio-colon-takes-the-builders-spacing: a clear pixel each side of the colon.
        flow.ratioColon(RATIO, Palette.MUTED);
        RatioWriter ratio = (multiplier, divisor) -> processes.setFormulaRatio(section, key, multiplier, divisor);
        divisorControl(flow, formula.sourceDivisor(), typed -> setSourceDivisor(entry, formula, typed, ratio));
        flow.text(ARROW, Palette.MUTED);
        Consumer<BuilderOverlays.Anchor> pickTarget = at -> overlays.openPicker(at,
                type -> edit(() -> processes.setFormulaTarget(section, key, type)),
                () -> edit(() -> processes.setFormulaTarget(section, key, NOTHING_TARGET)));
        if (NOTHING_TARGET.equals(formula.target())) {
            flow.control(NOTHING, null, pickTarget);
            return;
        }
        flow.typeIcon(formula.target(), pickTarget);
        // The target's ratio takes a mixed number either side, M : K reading M / K, reduced as it is written.
        numberControl(flow, formula.multiplier(),
                typed -> writeRatio(typed.numerator(), typed.denominator() * formula.divisor(), ratio));
        flow.ratioColon(RATIO, Palette.MUTED);
        divisorControl(flow, formula.divisor(),
                typed -> writeRatio(formula.multiplier() * typed.denominator(), typed.numerator(), ratio));
    }

    // A fraction typed for the source's divisor folds its denominator into the target's multiplier.
    private void setSourceDivisor(Entry entry, Processes.Formula formula, Fraction typed, RatioWriter ratio) {
        if (typed.numerator() <= Integer.MAX_VALUE) {
            processes.setFormulaDivisor(entry.section(), entry.key(), (int) typed.numerator());
        }
        if (typed.denominator() != 1 && !NOTHING_TARGET.equals(formula.target())) {
            writeRatio(formula.multiplier() * typed.denominator(), formula.divisor(), ratio);
        }
    }

    private void drawAdditive(Entry entry, Processes.Additive additive) {
        ControlFlow flow = entry.flow();
        flow.text(PLUS, Palette.MUTED);
        // Decision hash-marks-groups-and-dollar-marks-prefabs-everywhere: the prefab reads after $.
        nameControl(flow, new Ref(PrefabNames.marked(additive.constant()), () -> actions.openVariable(additive.constant())),
                PrefabHovers.hoverKey(additive.constant()), SearchDropdown.over(List.of(), treePrefabs.get(), false),
                prefab -> processes.setAdditive(entry.section(), entry.key(), prefab));
        // Decision add-mutator-entry-shows-its-reduced-value: the value after the prefab, each type's icon beside its
        // amount in parentheses as the prefab's amount line reads, each pair wrapping whole; the hover stays.
        Map<String, String> amounts = prefabAmounts.apply(additive.constant());
        if (!amounts.isEmpty()) {
            // Decision one-amount-grid-draws-every-amount-list: the grid's cells inline, its parens round them.
            GooAmountGrid.reduced(amounts).inlineRuns().forEach(cell -> flow.runs(cell, null, null));
        }
    }

    private void drawStack(Entry entry, Processes.Stack stack) {
        ControlFlow flow = entry.flow();
        // Decision every-linkable-thing-hovers-its-preview-value: the mutator a stack applies hovers its formula.
        nameControl(flow, new Ref(stack.formula(), () -> actions.openMutator(stack.formula())),
                ConversionHovers.hoverKey(entry.section(), stack.formula()),
                SearchDropdown.overMutators(otherProcesses(entry)),
                formula -> processes.setStackFormula(entry.section(), entry.key(), formula));
        flow.text(TIMES_MARK, Palette.MUTED);
        countControl(flow, stack.count(), count -> processes.setStackCount(entry.section(), entry.key(), count));
        drawStackTotal(entry, stack);
    }

    // Decision prefab-hovers-its-card-and-the-row-shows-the-stack-preview: a stack over an add draws the stack's
    // total, the prefab's reduced value times the count, through the amount grid at all times.
    private void drawStackTotal(Entry entry, Processes.Stack stack) {
        long count = stack.count();
        Processes.Form applied = processes.formOf(entry.section(), stack.formula());
        for (int depth = 0; applied instanceof Processes.Stack inner && depth < MAX_STACK_DEPTH; depth++) {
            count *= inner.count();
            applied = processes.formOf(entry.section(), inner.formula());
        }
        if (!(applied instanceof Processes.Additive additive)) {
            return;
        }
        long times = count;
        Map<String, String> total = new LinkedHashMap<>();
        prefabAmounts.apply(additive.constant()).forEach((type, amount) -> total.put(type,
                ConversionHovers.times(amount, times)));
        if (!total.isEmpty()) {
            GooAmountGrid.reduced(total).inlineRuns().forEach(cell -> entry.flow().runs(cell, null, null));
        }
    }

    // "copies [source]" one indent in under the name line, then the chain; the row draws no ratio.
    // Decision iterator-reads-copies-then-mutates-using-and.
    // Decision iterator-drops-its-ratio-from-row-and-grammar.
    private void drawAssignment(Entry entry, Processes.Assignment assignment) {
        ControlFlow flow = entry.flow();
        flow.indent();
        flow.text(COPIES, Palette.MUTED);
        drawSource(entry, assignment.source());
        drawChain(entry, assignment.chain());
    }

    // Decision iterator-line-is-source-ratio-then-chain: one dropdown reads the source group or itself, listing every
    // group and itself; a shift + left click on a group opens it.
    private void drawSource(Entry entry, String source) {
        ControlFlow flow = entry.flow();
        List<BuilderOverlays.Option> sources = new ArrayList<>();
        for (String group : processes.groupNames()) {
            sources.add(new BuilderOverlays.Option(GROUP_MARK + group, null, at -> setSource(entry, group)));
        }
        sources.add(new BuilderOverlays.Option(ITSELF, null, at -> setSource(entry, NO_SOURCE)));
        Consumer<BuilderOverlays.Anchor> open = at -> overlays.openOptions(at, sources);
        if (source.isEmpty()) {
            flow.control(ControlFlow.dropdownLabel(ITSELF), null, open);
        } else {
            // Decisions group-referent-previews-shorthand-members-then-its-value,
            // conversions-sentences-are-built-from-the-builders-controls.
            flow.refSegment(ControlFlow.dropdownLabel(GROUP_MARK + source), GroupHovers.hoverKey(source), open,
                    () -> actions.openGroup(source));
        }
    }

    private void setSource(Entry entry, String group) {
        edit(() -> processes.setAssignmentSource(entry.section(), entry.key(), group));
    }

    // The processes the assignment applies in order, each removable on its own line under the source, the first
    // after "mutated by" and each later one after "and", then "+ optional mutation" on a line of its own; each line
    // right packs, its x at one edge left of the row's validity marker, so the lines stand clear of the marker and
    // the row's remove X.
    // Decision each-chained-mutator-takes-its-own-indented-line.
    // Decision mutator-chain-forms-a-column-left-of-the-validity-marker.
    // Decision iterator-reads-copies-then-mutates-using-and.
    private void drawChain(Entry entry, List<String> chain) {
        ControlFlow flow = entry.flow();
        Lettering letters = flow.letters();
        String section = entry.section();
        String key = entry.key();
        int inset = entry.chainInset();
        for (int index = 0; index < chain.size(); index++) {
            drawLink(entry, chain.get(index).substring(STACK_MARK.length()), index);
        }
        flow.newLine();
        flow.alignRight(ButtonPainter.width(letters, ADD_MUTATION) + inset);
        flow.control(ADD_MUTATION, null, at -> openNames(at, SearchDropdown.overMutators(
                processes.processNames(section)), name -> processes.addAssignmentLink(section, key, STACK_MARK + name)));
    }

    // One chained mutator's line, right packed whole: its words, the mutator's button, then its x one gap past it.
    // Decision chain-x-follows-its-mutator-at-one-gap.
    private void drawLink(Entry entry, String applied, int index) {
        ControlFlow flow = entry.flow();
        Lettering letters = flow.letters();
        String words = index == 0 ? MUTATED_BY : AND;
        flow.newLine();
        flow.alignRight(letters.width(words) + BuilderOverlays.GAP + ButtonPainter.width(letters, applied)
                + BuilderOverlays.GAP + ButtonPainter.width(letters, REMOVE_LINK) + entry.chainInset());
        flow.text(words, Palette.MUTED);
        // Decision mutator-name-hovers-its-formula-wherever-it-stands: an applied mutator, waxed among them, hovers the
        // formula its own row hovers.
        flow.followLink(applied, ConversionHovers.hoverKey(entry.section(), applied),
                () -> actions.openMutator(applied));
        flow.control(REMOVE_LINK, REMOVE_LINK_HINT,
                at -> edit(() -> processes.removeAssignmentLink(entry.section(), entry.key(), index)));
    }

    private void drawUnread(Entry entry, Processes.Unread unread, boolean assignment) {
        ControlFlow flow = entry.flow();
        flow.text(UNREAD, Palette.DANGER);
        flow.text(unread.text(), Palette.DANGER);
        List<String> groups = processes.groupNames();
        if (assignment && !groups.isEmpty()) {
            flow.control(START_OVER, null, at -> write(entry,
                    new Processes.Assignment(groups.getFirst(), List.of())));
        }
    }

    /**
     * A name a row draws and what a shift + left click on it opens.
     *
     * @param label  the name as drawn
     * @param follow opens the group, prefab or mutator it names
     */
    private record Ref(String label, Runnable follow) {
    }

    // Decision diagnose-and-correct-mutator-references-that-do-not-link: a plain left click opens a dropdown over the
    // names, a shift + left click opens the thing named.
    // Decision conversions-sentences-are-built-from-the-builders-controls: the name is the builder's ref segment,
    // hovering its card.
    private void nameControl(ControlFlow flow, Ref ref, String hoverKey, SearchDropdown names, Consumer<String> pick) {
        flow.refSegment(ref.label(), hoverKey, at -> openNames(at, names, pick), ref.follow());
    }

    private void openNames(BuilderOverlays.Anchor at, SearchDropdown names, Consumer<String> pick) {
        overlays.openDropdown(at, names, choice -> edit(() -> pick.accept(choice.id())));
    }

    // A number as the builder draws one: a segment 14 tall on the builder's line whose click opens the builder's
    // mixed-number box on the number, its text selected; the box's value, reduced, reaches commit, and text the box
    // reads as no number changes nothing.
    // Decision conversions-sentences-are-built-from-the-builders-controls.
    private void numberControl(ControlFlow flow, int number, Consumer<Fraction> commit) {
        String shown = String.valueOf(number);
        flow.openSegments(ButtonPainter.width(flow.letters(), shown), ButtonPainter.HEIGHT);
        flow.labelSegment(shown, null, at -> overlays.openMixedNumber(at, shown,
                typed -> fractionOf(typed).ifPresent(parsed -> edit(() -> commit.accept(parsed)))));
        flow.closeSegments();
    }

    // A whole count alone, as a stack applies its mutator a whole number of times.
    private void countControl(ControlFlow flow, int number, IntConsumer commit) {
        numberControl(flow, number, typed -> typed.whole().ifPresent(commit));
    }

    // Decision mutator-sentences-read-in-symbols: a divisor commits no 0.
    private void divisorControl(ControlFlow flow, int number, Consumer<Fraction> commit) {
        numberControl(flow, number, typed -> {
            if (typed.numerator() > 0) {
                commit.accept(typed);
            }
        });
    }

    /**
     * A number box's value reduced to its numerator over its denominator.
     *
     * @param numerator   above the bar
     * @param denominator below it, never 0
     */
    private record Fraction(long numerator, long denominator) {
        // The value where it is whole, empty for a fraction.
        OptionalInt whole() {
            return denominator == 1 ? OptionalInt.of((int) numerator) : OptionalInt.empty();
        }
    }

    // A whole, a fraction or a whole then a fraction, as the builder's box reads them; empty where it reads none or a
    // part outgrows an int.
    private static Optional<Fraction> fractionOf(String typed) {
        return ExpressionBuilder.mixedNumber(typed).filter(parts -> parts[0].bitLength() < Integer.SIZE
                && parts[1].bitLength() < Integer.SIZE).map(parts -> new Fraction(parts[0].longValue(),
                parts[1].longValue()));
    }

    // A ratio a : b reduced to its lowest terms, empty where a term outgrows an int; written through write.
    private static void writeRatio(long first, long second, RatioWriter write) {
        long common = gcd(first, second);
        long reducedFirst = first / common;
        long reducedSecond = second / common;
        if (reducedFirst <= Integer.MAX_VALUE && reducedSecond <= Integer.MAX_VALUE) {
            write.accept((int) reducedFirst, (int) reducedSecond);
        }
    }

    private static long gcd(long first, long second) {
        return second == 0 ? Math.max(1, first) : gcd(second, first % second);
    }

    // Takes a reduced ratio's two terms.
    @FunctionalInterface
    private interface RatioWriter {
        void accept(int first, int second);
    }

    private void write(Entry entry, Processes.Form form) {
        edit(() -> processes.setForm(entry.section(), entry.key(), form));
    }

    private void edit(Runnable change) {
        change.run();
        changed.run();
    }
}
