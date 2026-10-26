package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.Processes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * One Mutators entry drawn as a sentence of controls, every field the game's conversion parser reads a click away:
 * a process chooses its kind, formula, additive or stack; a formula picks its source and target goo types through
 * the picker, nothing among them for the target, with its divisor, its multiply or divide and its amount; an
 * additive picks its tree prefab and a stack its process and count; an assignment picks its source group, or the
 * target's own values, its scale and its chain of processes. Each control names what it does on hover, the sentence
 * wraps inside the pane, and each change writes the entry through Processes' writers (decision
 * expression-builder-composes-every-form-without-typing).
 */
final class MutatorControls {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    // Nine digits always fit an int.
    private static final Pattern DIGITS = Pattern.compile("\\d{1,9}");
    private static final String NOTHING_TARGET = "0";
    private static final String DIVIDE_OPERATOR = "/";
    private static final String GROUP_MARK = "#";
    private static final String ITEM_MARK = ":";
    private static final String STACK_MARK = "@";
    private static final String NO_SOURCE = "";
    private static final String KIND = "Kind:";
    private static final String FORMULA = "formula";
    private static final String FORMULA_HINT = "Turn part of one goo type into another, or into nothing";
    private static final String ADDITIVE = "additive";
    private static final String ADDITIVE_HINT = "Add a tree prefab's value";
    private static final String STACK = "stack";
    private static final String STACK_HINT = "Apply another mutator a number of times";
    private static final String DIVIDED_BY = "divided by";
    private static final String BECOMES = "becomes";
    private static final String TIMES = "times";
    private static final String NOTHING = "[nothing]";
    private static final String TARGET_HINT = "Click to pick the goo type it becomes";
    private static final String DIVISOR_HINT = "Click to change how much of the source is taken";
    private static final String OPERATOR_HINT = "Click to switch between multiplying and dividing the result";
    private static final String AMOUNT_HINT = "Click to change the result's factor";
    private static final String ADDS = "adds the value of";
    private static final String PREFAB_HINT = "Click to pick another tree prefab";
    private static final String APPLIES = "applies";
    private static final String PROCESS_HINT = "Click to pick another mutator";
    private static final String COUNT_HINT = "Click to change how many times it applies";
    private static final String TAKES = "takes the values of";
    private static final String GROUP_HINT = "Click to pick another source group";
    private static final String OWN_VALUES = "[its own values]";
    private static final String OWN_HINT = "Click to take the values from a group instead";
    private static final String DROP_SOURCE = "[use its own values]";
    private static final String DROP_SOURCE_HINT = "Apply the chain to the target's own values, no source group";
    private static final String OVER = "over";
    private static final String MULTIPLIER_HINT = "Click to change what the values are multiplied by";
    private static final String SCALE_DIVISOR_HINT = "Click to change what the values are divided by";
    private static final String THEN = "then applies";
    private static final String REMOVE_LINK = "[x]";
    private static final String REMOVE_LINK_HINT = "Remove this mutator from the chain";
    private static final String ADD_LINK = "[+ mutator]";
    private static final String ADD_LINK_HINT = "Apply another mutator after these";
    private static final String UNREAD = "No form reads this line:";
    private static final String START_OVER = "[start over]";
    private static final String START_OVER_HINT = "Replace this line with an assignment from the first group";

    private final ProcessesTab processes;
    private final BuilderOverlays overlays;
    private final Supplier<List<String>> treePrefabs;
    private final Supplier<List<String>> gooTypes;
    private final Runnable changed;

    /**
     * @param processes   the Mutators tab's state
     * @param overlays    the number box, dropdown and picker the controls open
     * @param treePrefabs the tree prefabs' names, which an additive adds
     * @param gooTypes    the goo types' ids, the first a fresh formula's source
     * @param changed     runs after each change written to the draft
     */
    MutatorControls(ProcessesTab processes, BuilderOverlays overlays, Supplier<List<String>> treePrefabs,
                    Supplier<List<String>> gooTypes, Runnable changed) {
        this.processes = processes;
        this.overlays = overlays;
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
        Entry entry = new Entry(flow, section, key);
        Processes.Form form = processes.formOf(section, key);
        boolean assignment = key.startsWith(GROUP_MARK) || key.contains(ITEM_MARK);
        if (!assignment) {
            drawKind(entry, form);
            flow.newLine();
        }
        drawForm(entry, form, assignment);
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
     */
    private record Entry(ControlFlow flow, String section, String key) {
    }

    private void drawKind(Entry entry, Processes.Form form) {
        ControlFlow flow = entry.flow();
        flow.text(KIND, GRAY);
        flow.choice(FORMULA, FORMULA_HINT, form instanceof Processes.Formula, () -> freshFormula(entry));
        if (!treePrefabs.get().isEmpty()) {
            flow.choice(ADDITIVE, ADDITIVE_HINT, form instanceof Processes.Additive,
                    () -> write(entry, new Processes.Additive(treePrefabs.get().getFirst())));
        }
        List<String> others = otherProcesses(entry);
        if (!others.isEmpty()) {
            flow.choice(STACK, STACK_HINT, form instanceof Processes.Stack,
                    () -> write(entry, new Processes.Stack(1, others.getFirst())));
        }
    }

    private void freshFormula(Entry entry) {
        List<String> types = gooTypes.get();
        if (!types.isEmpty()) {
            write(entry, new Processes.Formula(types.getFirst(), 1, NOTHING_TARGET, DIVIDE_OPERATOR, 0));
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
        flow.text(DIVIDED_BY, GRAY);
        numberControl(flow, formula.sourceDivisor(), DIVISOR_HINT,
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
        flow.control(DIVIDE_OPERATOR.equals(formula.operator()) ? DIVIDED_BY : TIMES, OPERATOR_HINT,
                at -> edit(() -> processes.toggleFormulaOperator(section, key)));
        numberControl(flow, formula.amount(), AMOUNT_HINT, amount -> processes.setFormulaAmount(section, key, amount));
    }

    private void drawAdditive(Entry entry, Processes.Additive additive) {
        ControlFlow flow = entry.flow();
        flow.text(ADDS, GRAY);
        nameControl(flow, additive.constant(), PREFAB_HINT, treePrefabs.get(),
                prefab -> processes.setAdditive(entry.section(), entry.key(), prefab));
    }

    private void drawStack(Entry entry, Processes.Stack stack) {
        ControlFlow flow = entry.flow();
        flow.text(APPLIES, GRAY);
        nameControl(flow, stack.formula(), PROCESS_HINT, otherProcesses(entry),
                formula -> processes.setStackFormula(entry.section(), entry.key(), formula));
        numberControl(flow, stack.count(), COUNT_HINT,
                count -> processes.setStackCount(entry.section(), entry.key(), count));
        flow.text(TIMES, GRAY);
    }

    private void drawAssignment(Entry entry, Processes.Assignment assignment) {
        ControlFlow flow = entry.flow();
        String section = entry.section();
        String key = entry.key();
        flow.text(TAKES, GRAY);
        Consumer<String> setSource = group -> processes.setAssignmentSource(section, key, group);
        if (assignment.source().isEmpty()) {
            flow.control(OWN_VALUES, OWN_HINT, at -> openNames(at, processes.groupNames(), setSource));
        } else {
            nameControl(flow, GROUP_MARK + assignment.source(), GROUP_HINT, processes.groupNames(), setSource);
            flow.control(DROP_SOURCE, DROP_SOURCE_HINT, at -> edit(() -> setSource.accept(NO_SOURCE)));
        }
        flow.text(TIMES, GRAY);
        numberControl(flow, assignment.multiplier(), MULTIPLIER_HINT, multiplier -> processes.setAssignmentScale(
                section, key, multiplier, assignment.divisor()));
        flow.text(OVER, GRAY);
        numberControl(flow, assignment.divisor(), SCALE_DIVISOR_HINT, divisor -> processes.setAssignmentScale(section,
                key, assignment.multiplier(), divisor));
        drawChain(entry, assignment.chain());
    }

    /**
     * @param chain the processes an assignment applies, in order
     * @return the words leading the chain, none while it is empty, so the sentence ends where the value does
     */
    static List<String> chainLead(List<String> chain) {
        return chain.isEmpty() ? List.of() : List.of(THEN);
    }

    // The processes the assignment applies in order, each removable, then one more to add.
    private void drawChain(Entry entry, List<String> chain) {
        ControlFlow flow = entry.flow();
        String section = entry.section();
        String key = entry.key();
        chainLead(chain).forEach(words -> flow.text(words, GRAY));
        for (int index = 0; index < chain.size(); index++) {
            int link = index;
            flow.text(chain.get(index).substring(STACK_MARK.length()), WHITE);
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

    // A name drawn as a link whose click opens a dropdown over the names.
    private void nameControl(ControlFlow flow, String name, String hint, List<String> names, Consumer<String> pick) {
        flow.control(name, hint, at -> openNames(at, names, pick));
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
