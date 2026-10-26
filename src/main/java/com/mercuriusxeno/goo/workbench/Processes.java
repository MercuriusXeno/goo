package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Processes pane's forms for _conversions and _post_conversions, each spelled as the game's conversion
 * parser reads it: a formula ("metal / 4 -> yore / 9", "pulse / 4 -> yore * 0", "type / N -> 0"), an additive
 * ("+$waxed"), a stack ("2 @oxidation_metal"), and an assignment of a source group to a target group with an
 * optional scale and a chain of stacks and additives ("#exposed": "#copper @exposed_metal", a parallel copy
 * "#source * N / M") (decision workbench-authors-base-values-whole). The values manager's mutator controls edit
 * an entry through {@link #parse} and {@link #write}, so the draft holds what the parser reads (decision
 * expression-builder-composes-every-form-without-typing).
 */
public final class Processes {
    private static final String TAG_PREFIX = "#";
    private static final String STACK_PREFIX = "@";
    private static final String ADDITIVE_PREFIX = "+$";
    private static final String ARROW = " -> ";
    private static final String DIVIDE = " / ";
    private static final String TIMES = " * ";
    private static final String SPACE = " ";
    private static final String ZERO_TARGET = "0";
    private static final String DIVIDE_OPERATOR = "/";
    // A stack applied once spells no count.
    private static final String NO_COUNT = "";
    private static final Pattern FORMULA = Pattern.compile(
            "^([a-z0-9_:]+) / (\\d+) -> (?:(0)|([a-z0-9_:]+) ([/*]) (\\d+))$");
    private static final int FORMULA_SOURCE = 1;
    private static final int FORMULA_SOURCE_DIVISOR = 2;
    private static final int FORMULA_ZERO_TARGET = 3;
    private static final int FORMULA_TARGET = 4;
    private static final int FORMULA_OPERATOR = 5;
    private static final int FORMULA_AMOUNT = 6;
    private static final Pattern ADDITIVE = Pattern.compile("^\\+\\$([A-Za-z0-9_]+)$");
    private static final int ADDITIVE_CONSTANT = 1;
    private static final Pattern STACK = Pattern.compile("^(?:(\\d+) )?@([A-Za-z0-9_]+)$");
    private static final int STACK_COUNT = 1;
    private static final int STACK_FORMULA = 2;
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "^#(\\S+?)(?: \\* (\\d+) / (\\d+))?((?: [@+]\\S+)*)$");
    private static final int ASSIGNMENT_SOURCE = 1;
    private static final int ASSIGNMENT_MULTIPLIER = 2;
    private static final int ASSIGNMENT_DIVISOR = 3;
    private static final int ASSIGNMENT_CHAIN = 4;

    private Processes() {
    }

    /**
     * Which form a process entry takes.
     */
    public enum Kind {
        /** A type converted to another at a ratio, or to nothing. */
        FORMULA,
        /** A constant's value added. */
        ADDITIVE,
        /** A formula applied a number of times. */
        STACK,
        /** A source group's values copied to a target group, scaled and chained. */
        ASSIGNMENT,
        /** A line no form reads, kept as the file spells it. */
        UNREAD
    }

    /**
     * One entry of a conversion block, read into its form and written back from it.
     *
     * @param key  the entry's key: a name, or #group for an assignment
     * @param kind its form
     * @param text its value text, written from the form
     */
    record Process(String key, Kind kind, String text) {
    }

    /**
     * An entry's value read into its form's fields.
     */
    public sealed interface Form permits Formula, Additive, Stack, Assignment, Unread {
        /**
         * @return which form it is
         */
        Kind kind();
    }

    /**
     * @param source        the source type
     * @param sourceDivisor the source type's divisor
     * @param target        the target type, or "0" for a conversion to nothing
     * @param operator      "/" or "*"
     * @param amount        the target's divisor or multiplier
     */
    public record Formula(String source, int sourceDivisor, String target, String operator, int amount)
            implements Form {
        @Override
        public Kind kind() {
            return Kind.FORMULA;
        }
    }

    /**
     * @param constant the tree constant whose value the process adds
     */
    public record Additive(String constant) implements Form {
        @Override
        public Kind kind() {
            return Kind.ADDITIVE;
        }
    }

    /**
     * @param count   how many times the formula applies
     * @param formula the formula's name
     */
    public record Stack(int count, String formula) implements Form {
        @Override
        public Kind kind() {
            return Kind.STACK;
        }
    }

    /**
     * @param source     the source group's name
     * @param multiplier the scale's multiplier, 1 for none
     * @param divisor    the scale's divisor, 1 for none
     * @param chain      the stacks and additives applied in order, a stack as "@name" and an additive as "+$name"
     */
    public record Assignment(String source, int multiplier, int divisor, List<String> chain) implements Form {
        @Override
        public Kind kind() {
            return Kind.ASSIGNMENT;
        }
    }

    /**
     * @param text the value text no form reads, as the file spells it
     */
    public record Unread(String text) implements Form {
        @Override
        public Kind kind() {
            return Kind.UNREAD;
        }
    }

    /**
     * @param key  the entry's key: a name, or #group for an assignment
     * @param text the entry's value text as the file holds it
     * @return its form
     */
    public static Form parse(String key, String text) {
        String value = stringValue(text);
        if (value == null) {
            return new Unread(text);
        }
        return key.startsWith(TAG_PREFIX) ? assignmentOf(value, text) : processOf(value, text);
    }

    /**
     * @param form an entry's form
     * @return the value text the file holds for it
     */
    public static String write(Form form) {
        return switch (form) {
            case Formula formula -> formula(formula.source(), formula.sourceDivisor(), formula.target(),
                    formula.operator(), formula.amount());
            case Additive additive -> additive(additive.constant());
            case Stack stack -> stack(stack.count(), stack.formula());
            case Assignment assignment -> assignment(assignment.source(), assignment.multiplier(),
                    assignment.divisor(), assignment.chain());
            case Unread unread -> unread.text();
        };
    }

    /**
     * @param form an entry's form
     * @return which form it is
     */
    public static Kind kindOf(Form form) {
        return form.kind();
    }

    private static Form processOf(String value, String text) {
        Matcher formula = FORMULA.matcher(value);
        if (formula.matches()) {
            return new Formula(formula.group(FORMULA_SOURCE), Integer.parseInt(formula.group(FORMULA_SOURCE_DIVISOR)),
                    formula.group(FORMULA_ZERO_TARGET) != null ? ZERO_TARGET : formula.group(FORMULA_TARGET),
                    formula.group(FORMULA_OPERATOR) == null ? DIVIDE_OPERATOR : formula.group(FORMULA_OPERATOR),
                    formula.group(FORMULA_AMOUNT) == null ? 0 : Integer.parseInt(formula.group(FORMULA_AMOUNT)));
        }
        Matcher additive = ADDITIVE.matcher(value);
        if (additive.matches()) {
            return new Additive(additive.group(ADDITIVE_CONSTANT));
        }
        Matcher stack = STACK.matcher(value);
        if (stack.matches()) {
            return new Stack(stack.group(STACK_COUNT) == null ? 1 : Integer.parseInt(stack.group(STACK_COUNT)),
                    stack.group(STACK_FORMULA));
        }
        return new Unread(text);
    }

    private static Form assignmentOf(String value, String text) {
        Matcher assignment = ASSIGNMENT.matcher(value);
        if (!assignment.matches()) {
            return new Unread(text);
        }
        List<String> chain = List.of(assignment.group(ASSIGNMENT_CHAIN).trim().split(SPACE)).stream()
                .filter(link -> !link.isEmpty()).toList();
        return new Assignment(assignment.group(ASSIGNMENT_SOURCE),
                assignment.group(ASSIGNMENT_MULTIPLIER) == null ? 1
                        : Integer.parseInt(assignment.group(ASSIGNMENT_MULTIPLIER)),
                assignment.group(ASSIGNMENT_DIVISOR) == null ? 1
                        : Integer.parseInt(assignment.group(ASSIGNMENT_DIVISOR)), chain);
    }

    /**
     * @param source        the source type
     * @param sourceDivisor the source type's divisor
     * @param target        the target type, or "0" for a conversion to nothing
     * @param operator      "/" or "*"
     * @param amount        the target's divisor or multiplier
     * @return the formula
     */
    static String formula(String source, int sourceDivisor, String target, String operator, int amount) {
        String into = ZERO_TARGET.equals(target) ? ZERO_TARGET : target + SPACE + operator + SPACE + amount;
        return quoted(source + DIVIDE + sourceDivisor + ARROW + into);
    }

    /**
     * @param constant the tree constant whose value the process adds
     * @return the additive
     */
    static String additive(String constant) {
        return quoted(ADDITIVE_PREFIX + constant);
    }

    /**
     * @param count   how many times the formula applies
     * @param formula the formula's name
     * @return the stack
     */
    static String stack(int count, String formula) {
        return quoted((count == 1 ? NO_COUNT : count + SPACE) + STACK_PREFIX + formula);
    }

    /**
     * @param source     the source group's name
     * @param multiplier the scale's multiplier, 1 for none
     * @param divisor    the scale's divisor, 1 for none
     * @param chain      the stacks and additives applied in order, each by name; an additive leads with "+$"
     * @return the assignment's value
     */
    static String assignment(String source, int multiplier, int divisor, List<String> chain) {
        StringBuilder text = new StringBuilder(TAG_PREFIX).append(source);
        if (multiplier != 1 || divisor != 1) {
            text.append(TIMES).append(multiplier).append(DIVIDE).append(divisor);
        }
        for (String link : chain) {
            text.append(SPACE).append(link.startsWith(ADDITIVE_PREFIX) || link.startsWith(STACK_PREFIX)
                    ? link : STACK_PREFIX + link);
        }
        return quoted(text.toString());
    }

    /**
     * @param model   the model
     * @param section _conversions or _post_conversions
     * @param key     the entry's key
     * @param text    the entry's value text
     * @return the model with the entry set, last where it is new, since the parser applies the block in order
     */
    static BaseValuesModel with(BaseValuesModel model, String section, String key, String text) {
        return model.withEntry(section, key, text);
    }

    /**
     * @param model   the model
     * @param section _conversions or _post_conversions
     * @return each entry read into its form, in the order the parser applies them
     */
    static List<Process> read(BaseValuesModel model, String section) {
        BaseValuesModel.Member block = model.member(section);
        if (block == null) {
            return List.of();
        }
        List<Process> processes = new ArrayList<>();
        for (BaseValuesModel.Member entry : block.entries()) {
            processes.add(readOne(entry.key(), entry.text()));
        }
        return processes;
    }

    private static Process readOne(String key, String text) {
        Form form = parse(key, text);
        return new Process(key, kindOf(form), write(form));
    }

    private static String stringValue(String text) {
        try {
            JsonElement parsed = JsonParser.parseString(text);
            return parsed.isJsonPrimitive() && parsed.getAsJsonPrimitive().isString() ? parsed.getAsString() : null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    private static String quoted(String text) {
        return new JsonPrimitive(text).toString();
    }
}
