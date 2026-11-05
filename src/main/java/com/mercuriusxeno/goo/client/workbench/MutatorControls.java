package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.Processes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

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
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int HALF = 2;
    // Nine digits always fit an int.
    private static final Pattern DIGITS = Pattern.compile("\\d{1,9}");
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
                new ControlFlow.Span(area.left(), area.right(), top), ref -> { });
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
        flow.text(SOURCE_SHARE, GRAY);
        // Decision ratio-colon-takes-the-builders-spacing: a clear pixel each side of the colon.
        flow.ratioColon(RATIO, GRAY);
        divisorControl(flow, formula.sourceDivisor(), null,
                divisor -> processes.setFormulaDivisor(section, key, divisor));
        flow.text(ARROW, GRAY);
        Consumer<BuilderOverlays.Anchor> pickTarget = at -> overlays.openPicker(at,
                type -> edit(() -> processes.setFormulaTarget(section, key, type)),
                () -> edit(() -> processes.setFormulaTarget(section, key, NOTHING_TARGET)));
        if (NOTHING_TARGET.equals(formula.target())) {
            flow.control(NOTHING, null, pickTarget);
            return;
        }
        flow.typeIcon(formula.target(), pickTarget);
        numberControl(flow, formula.multiplier(), null,
                multiplier -> processes.setFormulaRatio(section, key, multiplier, formula.divisor()));
        flow.ratioColon(RATIO, GRAY);
        divisorControl(flow, formula.divisor(), null,
                divisor -> processes.setFormulaRatio(section, key, formula.multiplier(), divisor));
    }

    private void drawAdditive(Entry entry, Processes.Additive additive) {
        ControlFlow flow = entry.flow();
        flow.text(PLUS, GRAY);
        // Decision hash-marks-groups-and-dollar-marks-prefabs-everywhere: the prefab reads after $.
        nameControl(flow, new Ref(PrefabNames.marked(additive.constant()), () -> actions.openVariable(additive.constant())),
                null, SearchDropdown.over(List.of(), treePrefabs.get(), false),
                prefab -> processes.setAdditive(entry.section(), entry.key(), prefab));
        flow.hoverLast(PrefabHovers.hoverKey(additive.constant()));
        // Decision add-mutator-entry-shows-its-reduced-value: the value after the prefab, each type's icon beside its
        // amount in parentheses as the prefab's amount line reads, each pair wrapping whole; the hover stays.
        Map<String, String> amounts = prefabAmounts.apply(additive.constant());
        if (!amounts.isEmpty()) {
            AmountGridLine.typed(amounts).cells().forEach(cell -> flow.runs(cell, null, null));
        }
    }

    private void drawStack(Entry entry, Processes.Stack stack) {
        ControlFlow flow = entry.flow();
        nameControl(flow, new Ref(stack.formula(), () -> actions.openMutator(stack.formula())), null,
                SearchDropdown.overMutators(otherProcesses(entry)),
                formula -> processes.setStackFormula(entry.section(), entry.key(), formula));
        // Decision every-linkable-thing-hovers-its-preview-value: the mutator a stack applies hovers its formula.
        flow.hoverLast(ConversionHovers.hoverKey(entry.section(), stack.formula()));
        flow.text(TIMES_MARK, GRAY);
        numberControl(flow, stack.count(), null,
                count -> processes.setStackCount(entry.section(), entry.key(), count));
    }

    // "copies [source]" one indent in under the name line, then the chain; the row draws no ratio.
    // Decision iterator-reads-copies-then-mutates-using-and.
    // Decision iterator-drops-its-ratio-from-row-and-grammar.
    private void drawAssignment(Entry entry, Processes.Assignment assignment) {
        ControlFlow flow = entry.flow();
        flow.indent();
        flow.text(COPIES, GRAY);
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
            flow.linkControl(ControlFlow.dropdownLabel(GROUP_MARK + source), null, open,
                    () -> actions.openGroup(source));
            // Decision group-referent-previews-shorthand-members-then-its-value.
            flow.hoverLast(GroupHovers.hoverKey(source));
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
        flow.text(words, GRAY);
        // Decision every-prefab-reference-hovers-its-value: an applied mutator, waxed among them, hovers its value.
        flow.followLink(applied, PrefabHovers.hoverKey(applied), () -> actions.openMutator(applied));
        flow.control(REMOVE_LINK, REMOVE_LINK_HINT,
                at -> edit(() -> processes.removeAssignmentLink(entry.section(), entry.key(), index)));
    }

    private void drawUnread(Entry entry, Processes.Unread unread, boolean assignment) {
        ControlFlow flow = entry.flow();
        flow.text(UNREAD, RED);
        flow.text(unread.text(), RED);
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
    private void nameControl(ControlFlow flow, Ref ref, String hint, SearchDropdown names, Consumer<String> pick) {
        flow.linkControl(ref.label(), hint, at -> openNames(at, names, pick), ref.follow());
    }

    private void openNames(BuilderOverlays.Anchor at, SearchDropdown names, Consumer<String> pick) {
        overlays.openDropdown(at, names, choice -> edit(() -> pick.accept(choice.id())));
    }

    // A number drawn as a link whose click opens the number box; a box holding no integer changes nothing.
    private void numberControl(ControlFlow flow, int number, String hint, IntConsumer commit) {
        flow.control(String.valueOf(number), hint, at -> overlays.openNumber(at, String.valueOf(number),
                typed -> integerOf(typed).ifPresent(parsed -> edit(() -> commit.accept(parsed)))));
    }

    // Decision mutator-sentences-read-in-symbols: a divisor box takes no 0 key and commits no 0.
    private void divisorControl(ControlFlow flow, int number, String hint, IntConsumer commit) {
        flow.control(String.valueOf(number), hint, at -> overlays.openNumber(at, String.valueOf(number),
                typed -> integerOf(typed).stream().filter(parsed -> parsed > 0).findFirst()
                        .ifPresent(parsed -> edit(() -> commit.accept(parsed))), true));
    }

    private static OptionalInt integerOf(String typed) {
        String trimmed = typed.trim();
        return DIGITS.matcher(trimmed).matches() ? OptionalInt.of(Integer.parseInt(trimmed)) : OptionalInt.empty();
    }

    private void write(Entry entry, Processes.Form form) {
        edit(() -> processes.setForm(entry.section(), entry.key(), form));
    }

    private void edit(Runnable change) {
        change.run();
        changed.run();
    }
}
