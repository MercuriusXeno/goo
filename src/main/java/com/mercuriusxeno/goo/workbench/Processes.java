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
 * The Processes pane's forms for _conversions, each spelled as the game's conversion
 * parser reads it: a formula ("metal / 4 -> yore / 9", "pulse / 4 -> yore * 2 / 3", "type / N -> 0"), an additive
 * ("+$waxed"), a stack ("2 @oxidation_metal"), and an assignment of a source group to a target group with a chain
 * of stacks and additives ("#exposed": "#copper @exposed_metal") and no scale (decisions
 * workbench-authors-base-values-whole, iterator-drops-its-ratio-from-row-and-grammar). The values manager's mutator controls edit
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
    // A stack applied once spells no count.
    private static final String NO_COUNT = "";
    // The game's ConversionFormulaParser reads spaces around each sign as optional.
    // Decision mutator-sentences-read-in-symbols: a target reads "type * M / K", "type * M" or "type / K".
    private static final Pattern FORMULA = Pattern.compile(
            "^\\s*([a-z0-9_:]+)\\s*/\\s*(\\d+)\\s*->\\s*(?:(0)|([a-z0-9_:]+)\\s*(?:\\*\\s*(\\d+)\\s*(?:/\\s*(\\d+))?"
                    + "|/\\s*(\\d+)))\\s*$");
    private static final int FORMULA_SOURCE = 1;
    private static final int FORMULA_SOURCE_DIVISOR = 2;
    private static final int FORMULA_ZERO_TARGET = 3;
    private static final int FORMULA_TARGET = 4;
    private static final int FORMULA_MULTIPLIER = 5;
    private static final int FORMULA_MULTIPLIED_DIVISOR = 6;
    private static final int FORMULA_DIVISOR = 7;
    private static final Pattern ADDITIVE = Pattern.compile("^\\+\\$([A-Za-z0-9_]+)$");
    private static final int ADDITIVE_CONSTANT = 1;
    private static final Pattern STACK = Pattern.compile("^(?:(\\d+) )?@([A-Za-z0-9_]+)$");
    private static final int STACK_COUNT = 1;
    private static final int STACK_FORMULA = 2;
    private static final String WHITESPACE = "\\s+";
    private static final String ITEM_MARK = ":";
    private static final String NO_SOURCE = "";

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
        /** A source group's values copied to a target group and chained. */
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
     * @param multiplier    the target's multiplier, 1 for none
     * @param divisor       the target's divisor, 1 for none
     */
    public record Formula(String source, int sourceDivisor, String target, int multiplier, int divisor)
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
     * An iterator holds no ratio; the mutators it applies carry theirs (decision
     * iterator-drops-its-ratio-from-row-and-grammar).
     *
     * @param source the source group's name, empty where the chain applies to the target's own values
     * @param chain  the processes applied in order, each "@name" of a formula, stack or additive
     */
    public record Assignment(String source, List<String> chain) implements Form {
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
        // The game's parser reads a #tag or an item id key as an assignment, any other key as a process.
        return key.startsWith(TAG_PREFIX) || key.contains(ITEM_MARK) ? assignmentOf(value, text)
                : processOf(value, text);
    }

    /**
     * @param form an entry's form
     * @return the value text the file holds for it
     */
    public static String write(Form form) {
        return switch (form) {
            case Formula formula -> formula(formula);
            case Additive additive -> additive(additive.constant());
            case Stack stack -> stack(stack.count(), stack.formula());
            case Assignment assignment -> assignment(assignment.source(), assignment.chain());
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
            return formulaOf(formula);
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

    private static Formula formulaOf(Matcher formula) {
        String source = formula.group(FORMULA_SOURCE);
        int sourceDivisor = Integer.parseInt(formula.group(FORMULA_SOURCE_DIVISOR));
        if (formula.group(FORMULA_ZERO_TARGET) != null) {
            return new Formula(source, sourceDivisor, ZERO_TARGET, 1, 1);
        }
        String divisor = formula.group(FORMULA_DIVISOR) != null ? formula.group(FORMULA_DIVISOR)
                : formula.group(FORMULA_MULTIPLIED_DIVISOR);
        return new Formula(source, sourceDivisor, formula.group(FORMULA_TARGET),
                numberOrOne(formula.group(FORMULA_MULTIPLIER)), numberOrOne(divisor));
    }

    private static int numberOrOne(String digits) {
        return digits == null ? 1 : Integer.parseInt(digits);
    }

    // An optional #source and a chain of @links; a scale, which the grammar no longer reads, or a token the game
    // would drop with a warning leaves the entry unread, so the view shows it as the file spells it.
    // Decision iterator-drops-its-ratio-from-row-and-grammar.
    private static Form assignmentOf(String value, String text) {
        List<String> tokens = tokensOf(value);
        String source = sourceOf(tokens);
        List<String> chain = tokens.subList(source.isEmpty() ? 0 : 1, tokens.size());
        if (!chain.stream().allMatch(link -> isNamed(link, STACK_PREFIX))) {
            return new Unread(text);
        }
        return new Assignment(source, List.copyOf(chain));
    }

    // Decision iterator-line-is-source-ratio-then-chain: a bare iterator, its text empty, holds no token, so it
    // reads as an assignment over itself at 1:1, as the game reads it.
    private static List<String> tokensOf(String remaining) {
        return remaining.isBlank() ? List.of() : List.of(remaining.trim().split(WHITESPACE));
    }

    // The #group a first token names, empty where the chain applies to the target's own values.
    private static String sourceOf(List<String> tokens) {
        return !tokens.isEmpty() && isNamed(tokens.getFirst(), TAG_PREFIX)
                ? tokens.getFirst().substring(TAG_PREFIX.length()) : NO_SOURCE;
    }

    // A mark and a name after it, as "#copper" or "@waxed".
    private static boolean isNamed(String token, String mark) {
        return token.startsWith(mark) && token.length() > mark.length();
    }

    /**
     * @param formula a formula's fields
     * @return the formula, its target spelled "type * M / K" with a 1 on either side left out, "type / 1" where
     *     both are 1
     */
    static String formula(Formula formula) {
        return quoted(formula.source() + DIVIDE + formula.sourceDivisor() + ARROW + target(formula));
    }

    private static String target(Formula formula) {
        if (ZERO_TARGET.equals(formula.target())) {
            return ZERO_TARGET;
        }
        String multiplied = formula.multiplier() == 1 ? formula.target()
                : formula.target() + TIMES + formula.multiplier();
        return formula.divisor() == 1 && formula.multiplier() != 1 ? multiplied
                : multiplied + DIVIDE + formula.divisor();
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
     * @param source the source group's name
     * @param chain  the processes applied in order, each by name, an @ leading it or not
     * @return the assignment's value
     */
    static String assignment(String source, List<String> chain) {
        List<String> parts = new ArrayList<>();
        if (!source.isEmpty()) {
            parts.add(TAG_PREFIX + source);
        }
        for (String link : chain) {
            parts.add(link.startsWith(STACK_PREFIX) ? link : STACK_PREFIX + link);
        }
        return quoted(String.join(SPACE, parts));
    }

    /**
     * @param model   the model
     * @param section _conversions
     * @param key     the entry's key
     * @param text    the entry's value text
     * @return the model with the entry set, last where it is new, since the parser applies the block in order
     */
    static BaseValuesModel with(BaseValuesModel model, String section, String key, String text) {
        return model.withEntry(section, key, text);
    }

    /**
     * @param model   the model
     * @param section _conversions
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
