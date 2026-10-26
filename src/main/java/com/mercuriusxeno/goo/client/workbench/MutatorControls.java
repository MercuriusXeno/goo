package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.Processes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * The controls over one Mutators entry, drawn on a line below its row: a formula picks its source and target types
 * through the goo type picker with number boxes for the divisor and amount and a multiply or divide toggle; an
 * additive picks its prefab, a stack its formula and count, and an assignment its source group, its scale and its
 * chain of stacks and additives, each from a searchable dropdown; each change writes the entry through Processes'
 * writers (decision expression-builder-composes-every-form-without-typing).
 */
final class MutatorControls {
    private static final int GAP = BuilderOverlays.GAP;
    private static final int TEXT_NUDGE = 5;
    private static final int ICON_NUDGE = 1;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    // Nine digits always fit an int.
    private static final Pattern DIGITS = Pattern.compile("\\d{1,9}");
    private static final String NOTHING_TARGET = "0";
    private static final String DIVIDE_OPERATOR = "/";
    private static final String DIVIDE_BY = "/";
    private static final String ARROW = "->";
    private static final String TO_NOTHING = "[to nothing]";
    private static final String PICK_TARGET = "nothing [pick a type]";
    private static final String MULTIPLY = "[x]";
    private static final String DIVIDE = "[/]";
    private static final String PLUS = "+";
    private static final String STACK_MARK = "@";
    private static final String GROUP_MARK = "#";
    private static final String ADDITIVE_LINK = "+$";
    private static final String SCALE_TIMES = "times";
    private static final String SCALE_OVER = "over";
    private static final String REMOVE_LINK = "[-]";
    private static final String ADD_STACK = "[+ stack]";
    private static final String ADD_ADDITIVE = "[+ additive]";
    private static final String UNREAD = "No form reads this line, so no control builds it.";

    private final ProcessesTab processes;
    private final BuilderOverlays overlays;
    private final Supplier<List<String>> treePrefabs;
    private final Runnable changed;

    /**
     * @param processes   the Mutators tab's state
     * @param overlays    the number box, dropdown and picker the controls open
     * @param treePrefabs the tree prefabs' names, which an additive adds
     * @param changed     runs after each change written to the draft
     */
    MutatorControls(ProcessesTab processes, BuilderOverlays overlays, Supplier<List<String>> treePrefabs,
                    Runnable changed) {
        this.processes = processes;
        this.overlays = overlays;
        this.treePrefabs = treePrefabs;
        this.changed = changed;
    }

    /**
     * Draws the entry's controls on one line.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param section  the entry's block
     * @param key      the entry's key
     * @param x        the line's left edge
     * @param y        the line's top
     * @param targets  the frame's click areas
     */
    void draw(GuiGraphicsExtractor graphics, Font font, String section, String key, int x, int y,
              ClickTargets targets) {
        Line line = new Line(graphics, font, section, key, y, targets);
        switch (processes.formOf(section, key)) {
            case Processes.Formula formula -> drawFormula(line, formula, x);
            case Processes.Additive additive -> drawAdditive(line, additive, x);
            case Processes.Stack stack -> drawStack(line, stack, x);
            case Processes.Assignment assignment -> drawAssignment(line, assignment, x);
            default -> graphics.text(font, UNREAD, x, y + TEXT_NUDGE, RED);
        }
    }

    /**
     * Where one entry's controls draw.
     */
    private record Line(GuiGraphicsExtractor graphics, Font font, String section, String key, int y,
                        ClickTargets targets) {
    }

    private void drawFormula(Line line, Processes.Formula formula, int left) {
        int x = typeControl(line, formula.source(), left,
                type -> processes.setFormulaSource(line.section(), line.key(), type));
        x = label(line, DIVIDE_BY, x);
        x = numberControl(line, formula.sourceDivisor(), x,
                divisor -> processes.setFormulaDivisor(line.section(), line.key(), divisor));
        x = label(line, ARROW, x);
        if (NOTHING_TARGET.equals(formula.target())) {
            BuilderOverlays.control(line.graphics(), line.font(), PICK_TARGET, x, line.y(), line.targets(),
                    at -> overlays.openPicker(at, type -> edit(() -> processes.setFormulaTarget(line.section(),
                            line.key(), type))));
            return;
        }
        x = typeControl(line, formula.target(), x,
                type -> processes.setFormulaTarget(line.section(), line.key(), type));
        x = control(line, DIVIDE_OPERATOR.equals(formula.operator()) ? DIVIDE : MULTIPLY, x,
                () -> processes.toggleFormulaOperator(line.section(), line.key()));
        x = numberControl(line, formula.amount(), x,
                amount -> processes.setFormulaAmount(line.section(), line.key(), amount));
        control(line, TO_NOTHING, x, () -> processes.setFormulaTarget(line.section(), line.key(), NOTHING_TARGET));
    }

    private void drawAdditive(Line line, Processes.Additive additive, int left) {
        int x = label(line, PLUS, left);
        nameControl(line, additive.constant(), x, treePrefabs.get(),
                prefab -> processes.setAdditive(line.section(), line.key(), prefab));
    }

    private void drawStack(Line line, Processes.Stack stack, int left) {
        int x = numberControl(line, stack.count(), left,
                count -> processes.setStackCount(line.section(), line.key(), count));
        x = label(line, STACK_MARK, x);
        nameControl(line, stack.formula(), x, processes.processNames(line.section()),
                formula -> processes.setStackFormula(line.section(), line.key(), formula));
    }

    private void drawAssignment(Line line, Processes.Assignment assignment, int left) {
        int x = nameControl(line, GROUP_MARK + assignment.source(), left, processes.groupNames(),
                group -> processes.setAssignmentSource(line.section(), line.key(), group));
        x = label(line, SCALE_TIMES, x);
        x = numberControl(line, assignment.multiplier(), x, multiplier -> processes.setAssignmentScale(
                line.section(), line.key(), multiplier, assignment.divisor()));
        x = label(line, SCALE_OVER, x);
        x = numberControl(line, assignment.divisor(), x, divisor -> processes.setAssignmentScale(line.section(),
                line.key(), assignment.multiplier(), divisor));
        List<String> chain = assignment.chain();
        for (int index = 0; index < chain.size(); index++) {
            int link = index;
            x = label(line, linkLabel(chain.get(index)), x);
            x = control(line, REMOVE_LINK, x, () -> processes.removeAssignmentLink(line.section(), line.key(), link));
        }
        x = BuilderOverlays.control(line.graphics(), line.font(), ADD_STACK, x, line.y(), line.targets(),
                at -> overlays.openDropdown(at, names(processes.processNames(line.section())), choice -> edit(() ->
                        processes.addAssignmentLink(line.section(), line.key(), STACK_MARK + choice.id()))));
        BuilderOverlays.control(line.graphics(), line.font(), ADD_ADDITIVE, x, line.y(), line.targets(),
                at -> overlays.openDropdown(at, names(treePrefabs.get()), choice -> edit(() ->
                        processes.addAssignmentLink(line.section(), line.key(), ADDITIVE_LINK + choice.id()))));
    }

    // A chain link reads as the process it stacks, or "+ prefab" for an additive.
    private static String linkLabel(String link) {
        return link.startsWith(ADDITIVE_LINK) ? PLUS + link.substring(ADDITIVE_LINK.length()) : link;
    }

    private static SearchDropdown names(List<String> names) {
        return SearchDropdown.over(List.of(), names, false);
    }

    /**
     * A name's pick.
     */
    @FunctionalInterface
    private interface NamePick {
        void picked(String name);
    }

    /**
     * A type's pick.
     */
    @FunctionalInterface
    private interface TypePick {
        void picked(String type);
    }

    // A goo type drawn as its icon, named on hover, whose click opens the picker.
    private int typeControl(Line line, String type, int x, TypePick pick) {
        GooTypeIcons.blit(line.graphics(), type, x, line.y() + ICON_NUDGE);
        line.targets().add(x, line.y(), GooTypeIcons.SIZE, GooTypeIcons.SIZE, () -> overlays.openPicker(
                new BuilderOverlays.Anchor(x, line.y()), picked -> edit(() -> pick.picked(picked))),
                GooTypeIcons.hoverKey(type));
        return x + GooTypeIcons.SIZE + GAP;
    }

    // A name drawn as a link whose click opens a dropdown over the names.
    private int nameControl(Line line, String name, int x, List<String> names, NamePick pick) {
        return BuilderOverlays.control(line.graphics(), line.font(), name, x, line.y(), line.targets(),
                at -> overlays.openDropdown(at, names(names), choice -> edit(() -> pick.picked(choice.id()))));
    }

    // A number drawn as a link whose click opens the number box; a box holding no integer changes nothing.
    private int numberControl(Line line, int number, int x, IntConsumer commit) {
        return BuilderOverlays.control(line.graphics(), line.font(), String.valueOf(number), x, line.y(),
                line.targets(), at -> overlays.openNumber(at, String.valueOf(number),
                        typed -> integerOf(typed).ifPresent(parsed -> edit(() -> commit.accept(parsed)))));
    }

    private static OptionalInt integerOf(String typed) {
        String trimmed = typed.trim();
        return DIGITS.matcher(trimmed).matches() ? OptionalInt.of(Integer.parseInt(trimmed)) : OptionalInt.empty();
    }

    private int control(Line line, String label, int x, Runnable change) {
        return BuilderOverlays.control(line.graphics(), line.font(), label, x, line.y(), line.targets(),
                at -> edit(change));
    }

    private static int label(Line line, String text, int x) {
        line.graphics().text(line.font(), text, x, line.y() + TEXT_NUDGE, text.isEmpty() ? GRAY : WHITE);
        return x + line.font().width(text) + GAP;
    }

    private void edit(Runnable change) {
        change.run();
        changed.run();
    }
}
