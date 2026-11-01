package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.Processes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * One Mutators entry drawn as a sentence of controls, every field the game's conversion parser reads a click away:
 * a process chooses its kind, convert, additive or stack; a formula picks its source and target goo types through
 * the picker, nothing among them for the target, and reads as two ratios, "1 : N" of the source becoming "M : K" of
 * the target, no divisor taking 0; an additive reads "+ prefab" and a stack "applies mutator xN"
 * (decision mutator-sentences-read-in-symbols); an assignment picks its source group, or the
 * target's own values, its scale and its chain of processes. Each control names what it does on hover, the sentence
 * wraps inside the pane, and each change writes the entry through Processes' writers (decision
 * expression-builder-composes-every-form-without-typing).
 */
final class MutatorControls {
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    // Nine digits always fit an int.
    private static final Pattern DIGITS = Pattern.compile("\\d{1,9}");
    private static final String NOTHING_TARGET = "0";
    private static final String GROUP_MARK = "#";
    private static final String STACK_MARK = "@";
    private static final String NO_SOURCE = "";
    // Decision mutator-sentences-read-in-symbols: the word formula goes.
    private static final String CONVERT = "convert";
    private static final String CONVERT_HINT = "Turn part of one goo type into another, or into nothing";
    private static final String ADDITIVE = "additive";
    private static final String ADDITIVE_HINT = "Add a tree prefab's value";
    private static final String STACK = "stack";
    private static final String STACK_HINT = "Apply another mutator a number of times";
    private static final String SOURCE_SHARE = "1";
    private static final String BECOMES = "becomes";
    private static final String TIMES_MARK = "x";
    private static final String NOTHING = "nothing";
    private static final String TARGET_HINT = "Click to pick the goo type it becomes";
    private static final String DIVISOR_HINT = "Click to change how much of the source is taken";
    private static final String TARGET_MULTIPLIER_HINT = "Click to change what the result is multiplied by";
    private static final String TARGET_DIVISOR_HINT = "Click to change what the result is divided by";
    private static final String PLUS = "+";
    private static final String PREFAB_HINT = "Click to pick another tree prefab";
    private static final String APPLIES = "applies";
    private static final String PROCESS_HINT = "Click to pick another mutator";
    private static final String COUNT_HINT = "Click to change how many times it applies";
    private static final String GROUP_HINT = "Click to pick the group whose values it takes, or itself";
    private static final String ITSELF = "itself";
    private static final String ITSELF_HINT = "Apply the chain to the target's own values, no source group";
    private static final String RATIO = ":";
    private static final String MULTIPLIER_HINT = "Click to change what the values are multiplied by";
    private static final String SCALE_DIVISOR_HINT = "Click to change what the values are divided by";
    private static final String REMOVE_LINK = "x";
    private static final String REMOVE_LINK_HINT = "Remove this mutator from the chain";
    private static final String ADD_LINK = "+";
    private static final String ADD_LINK_HINT = "Apply another mutator after these";
    private static final String UNREAD = "No form reads this line:";
    private static final String START_OVER = "start over";
    private static final String START_OVER_HINT = "Replace this line with an assignment from the first group";

    private final ProcessesTab processes;
    private final BuilderOverlays overlays;
    private final ManagerActions actions;
    private final Supplier<List<String>> treePrefabs;
    private final Supplier<List<String>> gooTypes;
    private final Runnable changed;

    /**
     * @param processes   the Mutators tab's state
     * @param overlays    the number box, dropdown and picker the controls open
     * @param actions     where a shift + left click on a group, prefab or mutator name opens it
     * @param treePrefabs the tree prefabs' names, which an additive adds
     * @param gooTypes    the goo types' ids, the first a fresh formula's source
     * @param changed     runs after each change written to the draft
     */
    MutatorControls(ProcessesTab processes, BuilderOverlays overlays, ManagerActions actions,
                    Supplier<List<String>> treePrefabs, Supplier<List<String>> gooTypes, Runnable changed) {
        this.processes = processes;
        this.overlays = overlays;
        this.actions = actions;
        this.treePrefabs = treePrefabs;
        this.gooTypes = gooTypes;
        this.changed = changed;
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
        ControlFlow flow = new ControlFlow(graphics, font, targets, null, area.left(), area.right(), top);
        drawForm(new Entry(flow, section, key), processes.formOf(section, key), ProcessesTab.isIterator(key));
        return flow.bottom();
    }

    /**
     * Draws a mutator's kind dropdown, which stands on its row's top line (decision
     * mutator-row-is-aligned-columns-name-kind-validity-rename-delete).
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param section  the entry's block
     * @param key      the entry's key
     * @param left     the dropdown's left edge
     * @param top      its line's top
     * @param targets  the frame's click areas
     */
    void drawKind(GuiGraphicsExtractor graphics, Font font, String section, String key, int left, int top,
                  ClickTargets targets) {
        ControlFlow flow = new ControlFlow(graphics, font, targets, null, left, Integer.MAX_VALUE, top);
        drawKind(new Entry(flow, section, key), processes.formOf(section, key));
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
     */
    private record Entry(ControlFlow flow, String section, String key) {
    }

    private void drawKind(Entry entry, Processes.Form form) {
        ControlFlow flow = entry.flow();
        ControlFlow.ChoiceSet kinds = new ControlFlow.ChoiceSet();
        boolean formula = form instanceof Processes.Formula;
        kinds.add(new BuilderOverlays.Option(CONVERT, CONVERT_HINT, unlessChosen(formula, () -> freshFormula(entry))),
                formula);
        if (!treePrefabs.get().isEmpty()) {
            boolean additive = form instanceof Processes.Additive;
            kinds.add(new BuilderOverlays.Option(ADDITIVE, ADDITIVE_HINT, unlessChosen(additive,
                    () -> write(entry, new Processes.Additive(treePrefabs.get().getFirst())))), additive);
        }
        List<String> others = otherProcesses(entry);
        if (!others.isEmpty()) {
            boolean stack = form instanceof Processes.Stack;
            kinds.add(new BuilderOverlays.Option(STACK, STACK_HINT, unlessChosen(stack,
                    () -> write(entry, new Processes.Stack(1, others.getFirst())))), stack);
        }
        flow.choices(kinds, overlays);
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
        flow.text(RATIO, GRAY);
        divisorControl(flow, formula.sourceDivisor(), DIVISOR_HINT,
                divisor -> processes.setFormulaDivisor(section, key, divisor));
        flow.text(BECOMES, GRAY);
        Consumer<BuilderOverlays.Anchor> pickTarget = at -> overlays.openPicker(at,
                type -> edit(() -> processes.setFormulaTarget(section, key, type)),
                () -> edit(() -> processes.setFormulaTarget(section, key, NOTHING_TARGET)));
        if (NOTHING_TARGET.equals(formula.target())) {
            flow.control(NOTHING, TARGET_HINT, pickTarget);
            return;
        }
        flow.typeIcon(formula.target(), pickTarget);
        numberControl(flow, formula.multiplier(), TARGET_MULTIPLIER_HINT,
                multiplier -> processes.setFormulaRatio(section, key, multiplier, formula.divisor()));
        flow.text(RATIO, GRAY);
        divisorControl(flow, formula.divisor(), TARGET_DIVISOR_HINT,
                divisor -> processes.setFormulaRatio(section, key, formula.multiplier(), divisor));
    }

    private void drawAdditive(Entry entry, Processes.Additive additive) {
        ControlFlow flow = entry.flow();
        flow.text(PLUS, GRAY);
        nameControl(flow, new Ref(additive.constant(), () -> actions.openVariable(additive.constant())), PREFAB_HINT,
                treePrefabs.get(), prefab -> processes.setAdditive(entry.section(), entry.key(), prefab));
        flow.hoverLast(PrefabHovers.hoverKey(additive.constant()));
    }

    private void drawStack(Entry entry, Processes.Stack stack) {
        ControlFlow flow = entry.flow();
        flow.text(APPLIES, GRAY);
        nameControl(flow, new Ref(stack.formula(), () -> actions.openMutator(stack.formula())), PROCESS_HINT,
                otherProcesses(entry), formula -> processes.setStackFormula(entry.section(), entry.key(), formula));
        flow.text(TIMES_MARK, GRAY);
        numberControl(flow, stack.count(), COUNT_HINT,
                count -> processes.setStackCount(entry.section(), entry.key(), count));
    }

    private void drawAssignment(Entry entry, Processes.Assignment assignment) {
        ControlFlow flow = entry.flow();
        String section = entry.section();
        String key = entry.key();
        drawSource(entry, assignment.source());
        // Decision factor-reads-as-a-ratio: "times 1 over 1" reads "1:1", each number its own box.
        numberControl(flow, assignment.multiplier(), MULTIPLIER_HINT, multiplier -> processes.setAssignmentScale(
                section, key, multiplier, assignment.divisor()));
        flow.text(RATIO, GRAY);
        divisorControl(flow, assignment.divisor(), SCALE_DIVISOR_HINT, divisor -> processes.setAssignmentScale(section,
                key, assignment.multiplier(), divisor));
        drawChain(entry, assignment.chain());
    }

    // Decision iterator-line-is-source-ratio-then-chain: one dropdown reads the source group or itself, listing every
    // group and itself; a shift + left click on a group opens it.
    private void drawSource(Entry entry, String source) {
        ControlFlow flow = entry.flow();
        List<BuilderOverlays.Option> sources = new ArrayList<>();
        for (String group : processes.groupNames()) {
            sources.add(new BuilderOverlays.Option(GROUP_MARK + group, GROUP_HINT, at -> setSource(entry, group)));
        }
        sources.add(new BuilderOverlays.Option(ITSELF, ITSELF_HINT, at -> setSource(entry, NO_SOURCE)));
        Consumer<BuilderOverlays.Anchor> open = at -> overlays.openOptions(at, sources);
        if (source.isEmpty()) {
            flow.control(ControlFlow.dropdownLabel(ITSELF), ITSELF_HINT, open);
        } else {
            flow.linkControl(ControlFlow.dropdownLabel(GROUP_MARK + source), GROUP_HINT, open,
                    () -> actions.openGroup(source));
        }
    }

    private void setSource(Entry entry, String group) {
        edit(() -> processes.setAssignmentSource(entry.section(), entry.key(), group));
    }

    // The processes the assignment applies in order, each removable, then one more to add.
    private void drawChain(Entry entry, List<String> chain) {
        ControlFlow flow = entry.flow();
        String section = entry.section();
        String key = entry.key();
        for (int index = 0; index < chain.size(); index++) {
            int link = index;
            String applied = chain.get(index).substring(STACK_MARK.length());
            // Decision every-prefab-reference-hovers-its-value: an applied mutator, waxed among them, hovers its value.
            flow.followLink(applied, PrefabHovers.hoverKey(applied), () -> actions.openMutator(applied));
            flow.control(REMOVE_LINK, REMOVE_LINK_HINT,
                    at -> edit(() -> processes.removeAssignmentLink(section, key, link)));
        }
        flow.control(ADD_LINK, ADD_LINK_HINT, at -> openNames(at, processes.processNames(section),
                name -> processes.addAssignmentLink(section, key, STACK_MARK + name)));
    }

    private void drawUnread(Entry entry, Processes.Unread unread, boolean assignment) {
        ControlFlow flow = entry.flow();
        flow.text(UNREAD, RED);
        flow.text(unread.text(), RED);
        List<String> groups = processes.groupNames();
        if (assignment && !groups.isEmpty()) {
            flow.control(START_OVER, START_OVER_HINT, at -> write(entry,
                    new Processes.Assignment(groups.getFirst(), 1, 1, List.of())));
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
    private void nameControl(ControlFlow flow, Ref ref, String hint, List<String> names, Consumer<String> pick) {
        flow.linkControl(ref.label(), hint, at -> openNames(at, names, pick), ref.follow());
    }

    private void openNames(BuilderOverlays.Anchor at, List<String> names, Consumer<String> pick) {
        overlays.openDropdown(at, SearchDropdown.over(List.of(), names, false),
                choice -> edit(() -> pick.accept(choice.id())));
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
