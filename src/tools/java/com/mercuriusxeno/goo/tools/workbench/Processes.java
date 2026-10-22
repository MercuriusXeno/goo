package com.mercuriusxeno.goo.tools.workbench;

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
 * "#source * N / M") (decision workbench-authors-base-values-whole).
 */
final class Processes {
    private static final String TAG_PREFIX = "#";
    private static final String STACK_PREFIX = "@";
    private static final String ADDITIVE_PREFIX = "+$";
    private static final String ARROW = " -> ";
    private static final String DIVIDE = " / ";
    private static final String TIMES = " * ";
    private static final String SPACE = " ";
    private static final String ZERO_TARGET = "0";
    private static final Pattern FORMULA = Pattern.compile(
            "^([a-z0-9_:]+) / (\\d+) -> (?:(0)|([a-z0-9_:]+) ([/*]) (\\d+))$");
    private static final Pattern ADDITIVE = Pattern.compile("^\\+\\$([A-Za-z0-9_]+)$");
    private static final Pattern STACK = Pattern.compile("^(?:(\\d+) )?@([A-Za-z0-9_]+)$");
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "^#(\\S+?)(?: \\* (\\d+) / (\\d+))?((?: [@+]\\S+)*)$");

    private Processes() {
    }

    /**
     * Which form a process entry takes.
     */
    enum Kind {
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
        return quoted((count == 1 ? "" : count + SPACE) + STACK_PREFIX + formula);
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
        String value = stringValue(text);
        if (value == null) {
            return new Process(key, Kind.UNREAD, text);
        }
        Matcher formula = FORMULA.matcher(value);
        if (!key.startsWith(TAG_PREFIX) && formula.matches()) {
            return new Process(key, Kind.FORMULA, formula(formula.group(1), Integer.parseInt(formula.group(2)),
                    formula.group(3) != null ? ZERO_TARGET : formula.group(4),
                    formula.group(5) == null ? "/" : formula.group(5),
                    formula.group(6) == null ? 0 : Integer.parseInt(formula.group(6))));
        }
        Matcher additive = ADDITIVE.matcher(value);
        if (!key.startsWith(TAG_PREFIX) && additive.matches()) {
            return new Process(key, Kind.ADDITIVE, additive(additive.group(1)));
        }
        Matcher stack = STACK.matcher(value);
        if (!key.startsWith(TAG_PREFIX) && stack.matches()) {
            return new Process(key, Kind.STACK, stack(stack.group(1) == null ? 1 : Integer.parseInt(stack.group(1)),
                    stack.group(2)));
        }
        Matcher assignment = ASSIGNMENT.matcher(value);
        if (key.startsWith(TAG_PREFIX) && assignment.matches()) {
            List<String> chain = List.of(assignment.group(4).trim().split(" ")).stream()
                    .filter(link -> !link.isEmpty()).toList();
            return new Process(key, Kind.ASSIGNMENT, assignment(assignment.group(1),
                    assignment.group(2) == null ? 1 : Integer.parseInt(assignment.group(2)),
                    assignment.group(3) == null ? 1 : Integer.parseInt(assignment.group(3)), chain));
        }
        return new Process(key, Kind.UNREAD, text);
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
