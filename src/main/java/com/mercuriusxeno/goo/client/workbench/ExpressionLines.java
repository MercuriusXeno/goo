package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Draws a value as curated runs laid across lines of a width: a goo type as its icon beside its amount, an item as
 * its icon and name, a prefab as its name link, a number and an operator as text and a per-type object as a row per
 * type, so no brace, quote, $ or namespaced id draws (decisions curated-view-shows-no-brace-quote-or-raw-id,
 * goo-types-draw-as-icons-named-on-hover); each link lands with the caller, so a click on it opens its prefab
 * (decision references-link-to-their-variable-and-hover-a-card). A value the terms cannot read draws its text as
 * an error.
 */
final class ExpressionLines {
    /** A line's height where a run draws an icon. */
    static final int ICON_LINE_HEIGHT = 18;
    /** A line's height where only text draws. */
    static final int TEXT_LINE_HEIGHT = 10;
    private static final int ICON_SIZE = 16;
    private static final int ICON_TEXT_GAP = 2;
    private static final int RUN_GAP = 3;
    // Text on an icon line sits centred against the 16px icon.
    private static final int TEXT_NUDGE = 5;
    private static final int ICON_NUDGE = 1;
    private static final int RED = 0xFFFF5555;
    private static final String NOTHING = "";
    private static final String PLUS = "+";
    private static final String MINUS = "-";
    private static final String TIMES = "*";
    private static final String DIVIDE = "/";
    private static final String OPEN = "(";
    private static final String CLOSE = ")";
    private static final String DENIED = "denied";
    private static final String TYPES_SEPARATOR = ", ";
    private static final String AMOUNT_SEPARATOR = " ";
    private static final String ARROW = "->";
    private static final String OPERATOR_CHARS = "+-*/()";
    private static final char REFERENCE_MARK = '$';

    private ExpressionLines() {
    }

    /**
     * What a run draws as.
     */
    enum Kind {
        /** A goo type's icon, named on hover; its ref is the type. */
        TYPE,
        /** An item's icon and name; its ref is the item's id. */
        ITEM,
        /** A prefab's name as a link; its ref is the prefab. */
        LINK,
        /** An integer or an amount. */
        NUMBER,
        /** An operator or a parenthesis. */
        OPERATOR,
        /** Plain text. */
        TEXT,
        /** The end of a line, as a per-type object ends each type's row. */
        BREAK,
        /** Text the terms could not read, drawn red as the file spells it. */
        ERROR
    }

    /**
     * One run of a curated value.
     *
     * @param kind what it draws as
     * @param text the text it draws, empty for an icon alone
     * @param ref  the type, item or prefab it stands for, null for text
     */
    record Run(Kind kind, String text, @Nullable String ref) {
    }

    /**
     * Where one piece of a link landed.
     */
    @FunctionalInterface
    interface LinkSink {
        /**
         * @param variable the prefab the link opens
         * @param x        the piece's left edge
         * @param y        the piece's top edge
         * @param width    the piece's width
         */
        void landed(String variable, int x, int y, int width);
    }

    /**
     * Where the runs draw and what their links and icons answer.
     *
     * @param graphics  the frame's graphics
     * @param icons     the items' icons, null where no item draws
     * @param targets   the frame's click areas, which take each type icon's hover
     * @param textColor text's color
     * @param linkColor a link's color
     * @param links     takes where each piece of a link landed
     */
    record Painter(GuiGraphicsExtractor graphics, @Nullable ItemIcons icons, ClickTargets targets, int textColor,
                   int linkColor, LinkSink links) {
    }

    /**
     * @param value an entry's terms
     * @param names an item's display name by its id
     * @return the runs it draws as
     */
    static List<Run> runs(ExpressionTerms.Value value, Function<String, String> names) {
        List<Run> runs = new ArrayList<>();
        if (value instanceof ExpressionTerms.PerType perType) {
            for (ExpressionTerms.TypeRow row : perType.rows()) {
                if (!runs.isEmpty()) {
                    runs.add(new Run(Kind.BREAK, NOTHING, null));
                }
                runs.add(type(row.type()));
                addSum(runs, row.sum(), names);
            }
        } else if (value instanceof ExpressionTerms.Expression expression) {
            addSum(runs, expression.sum(), names);
        } else {
            runs.add(text(DENIED));
        }
        return runs;
    }

    /**
     * @param shown a value as the draft's edit box shows it
     * @param names an item's display name by its id
     * @return the runs its terms draw as, or its text as an error where the terms cannot read it
     */
    static List<Run> shownRuns(String shown, Function<String, String> names) {
        return ExpressionTerms.parseShown(shown).map(value -> runs(value, names))
                .orElseGet(() -> List.of(new Run(Kind.ERROR, shown, null)));
    }

    /**
     * @param resolved a prefab's resolved value: "type amount" pairs joined by commas, or a scalar
     * @return each type as its icon beside its amount, and any other part as text
     */
    static List<Run> resolvedRuns(String resolved) {
        List<Run> runs = new ArrayList<>();
        for (String part : resolved.split(TYPES_SEPARATOR)) {
            int space = part.indexOf(AMOUNT_SEPARATOR);
            if (space > 0 && ExpressionTerms.isGooType(part.substring(0, space))) {
                runs.add(type(part.substring(0, space)));
                runs.add(number(part.substring(space + 1)));
            } else if (!part.isEmpty()) {
                runs.add(part.chars().allMatch(Character::isDigit) ? number(part) : text(part));
            }
        }
        return runs;
    }

    /**
     * @param amounts each type an entry resolves to, with its amount
     * @return each type as its icon beside its amount
     */
    static List<Run> amountRuns(Map<String, String> amounts) {
        List<Run> runs = new ArrayList<>();
        amounts.forEach((type, amount) -> {
            runs.add(type(type));
            runs.add(number(amount));
        });
        return runs;
    }

    /**
     * @param text a mutator's entry: a formula, an additive, a stack or an assignment
     * @return its words as runs: a goo type as its icon, a prefab as its link, a number and an operator as text
     */
    static List<Run> mutatorRuns(String text) {
        List<Run> runs = new ArrayList<>();
        int at = 0;
        while (at < text.length()) {
            char c = text.charAt(at);
            if (Character.isWhitespace(c)) {
                at++;
            } else if (text.startsWith(ARROW, at)) {
                runs.add(operator(ARROW));
                at += ARROW.length();
            } else if (OPERATOR_CHARS.indexOf(c) >= 0) {
                runs.add(operator(String.valueOf(c)));
                at++;
            } else {
                int end = wordEnd(text, at);
                addMutatorWord(runs, text.substring(at, end));
                at = end;
            }
        }
        return runs;
    }

    private static int wordEnd(String text, int start) {
        int end = start + 1;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))
                && OPERATOR_CHARS.indexOf(text.charAt(end)) < 0) {
            end++;
        }
        return end;
    }

    private static void addMutatorWord(List<Run> runs, String word) {
        if (word.charAt(0) == REFERENCE_MARK) {
            ExpressionTerms.parse(word).ifPresentOrElse(sum -> addSum(runs, sum, id -> id),
                    () -> runs.add(text(word.substring(1))));
        } else if (ExpressionTerms.isGooType(word)) {
            runs.add(type(word));
        } else if (word.chars().allMatch(Character::isDigit)) {
            runs.add(number(word));
        } else {
            runs.add(text(word));
        }
    }

    /**
     * @param text plain text
     * @return it as one run
     */
    static List<Run> textRuns(String text) {
        return List.of(text(text));
    }

    private static void addSum(List<Run> runs, ExpressionTerms.Sum sum, Function<String, String> names) {
        for (ExpressionTerms.Term term : sum.terms()) {
            if (term.join() != ExpressionTerms.Join.FIRST) {
                runs.add(operator(term.join() == ExpressionTerms.Join.PLUS ? PLUS : MINUS));
            }
            for (ExpressionTerms.Factor factor : term.factors()) {
                addScale(runs, factor.scale());
                addAtom(runs, factor.atom(), names);
            }
        }
    }

    // A juxtaposed factor draws beside its number with no operator between.
    private static void addScale(List<Run> runs, ExpressionTerms.Scale scale) {
        if (scale == ExpressionTerms.Scale.TIMES) {
            runs.add(operator(TIMES));
        } else if (scale == ExpressionTerms.Scale.DIVIDE) {
            runs.add(operator(DIVIDE));
        }
    }

    private static void addAtom(List<Run> runs, ExpressionTerms.Atom atom, Function<String, String> names) {
        switch (atom) {
            case ExpressionTerms.Number number -> runs.add(number(number.digits()));
            case ExpressionTerms.Prefab prefab -> {
                runs.add(new Run(Kind.LINK, prefab.name(), prefab.name()));
                addType(runs, prefab.type());
            }
            case ExpressionTerms.Item item -> {
                runs.add(new Run(Kind.ITEM, names.apply(item.id()), item.id()));
                addType(runs, item.type());
            }
            case ExpressionTerms.Group group -> {
                runs.add(operator(OPEN));
                addSum(runs, group.sum(), names);
                runs.add(operator(CLOSE));
            }
            case ExpressionTerms.Negated negated -> {
                runs.add(operator(MINUS));
                addAtom(runs, negated.atom(), names);
            }
        }
    }

    private static void addType(List<Run> runs, @Nullable String type) {
        if (type != null) {
            runs.add(type(type));
        }
    }

    private static Run type(String type) {
        return new Run(Kind.TYPE, NOTHING, type);
    }

    private static Run number(String digits) {
        return new Run(Kind.NUMBER, digits, null);
    }

    private static Run operator(String operator) {
        return new Run(Kind.OPERATOR, operator, null);
    }

    private static Run text(String text) {
        return new Run(Kind.TEXT, text, null);
    }

    /**
     * @param runs a value's runs
     * @return the height each of its lines takes: an icon line's where any run draws an icon
     */
    static int lineHeight(List<Run> runs) {
        return runs.stream().anyMatch(run -> run.kind() == Kind.TYPE || run.kind() == Kind.ITEM)
                ? ICON_LINE_HEIGHT : TEXT_LINE_HEIGHT;
    }

    /**
     * @param runs a value's runs
     * @return how far below a line's top its text draws, so a caller lines a text label up with the runs' text
     */
    static int textOffset(List<Run> runs) {
        return lineHeight(runs) == ICON_LINE_HEIGHT ? TEXT_NUDGE : 0;
    }

    /**
     * @param font  the font
     * @param runs  a value's runs
     * @param width the width the lines fill
     * @return the height the runs take, at least one line
     */
    static int height(Font font, List<Run> runs, int width) {
        return lay(font, runs, 0, 0, width, null) * lineHeight(runs);
    }

    /**
     * @param font    the font
     * @param runs    a value's runs
     * @param x       the lines' left edge
     * @param y       the first line's top edge
     * @param width   the width the lines fill
     * @param painter where they draw
     */
    static void draw(Font font, List<Run> runs, int x, int y, int width, Painter painter) {
        lay(font, runs, x, y, width, painter);
    }

    private static int runWidth(Font font, Run run) {
        return switch (run.kind()) {
            case TYPE -> ICON_SIZE;
            case ITEM -> ICON_SIZE + ICON_TEXT_GAP + font.width(run.text());
            case BREAK -> 0;
            default -> font.width(run.text());
        };
    }

    // Answers how many lines the runs take; a text run wider than a line breaks across lines, and any other run
    // starts a fresh line where it does not fit.
    private static int lay(Font font, List<Run> runs, int x, int y, int width, @Nullable Painter painter) {
        Cursor cursor = new Cursor(font, x, y, width, lineHeight(runs), painter);
        for (Run run : runs) {
            if (run.kind() == Kind.BREAK) {
                cursor.newLine();
            } else if (run.kind() == Kind.TEXT || run.kind() == Kind.ERROR) {
                cursor.placeText(run);
            } else {
                cursor.placeWhole(run);
            }
        }
        return cursor.lines;
    }

    // Where the next run lands as the runs lay across their lines.
    private static final class Cursor {
        private final Font font;
        private final int left;
        private final int top;
        private final int width;
        private final int lineHeight;
        private final @Nullable Painter painter;
        private int lines = 1;
        private int at;

        Cursor(Font font, int left, int top, int width, int lineHeight, @Nullable Painter painter) {
            this.font = font;
            this.left = left;
            this.top = top;
            this.width = width;
            this.lineHeight = lineHeight;
            this.painter = painter;
            this.at = left;
        }

        void newLine() {
            lines++;
            at = left;
        }

        private int lineTop() {
            return top + (lines - 1) * lineHeight;
        }

        private int start() {
            return at == left ? left : at + RUN_GAP;
        }

        void placeWhole(Run run) {
            int runWidth = runWidth(font, run);
            if (start() + runWidth > left + width && at > left) {
                newLine();
            }
            int start = start();
            drawPiece(font, run, run.text(), start, lineTop(), lineHeight, painter);
            at = start + runWidth;
        }

        void placeText(Run run) {
            String rest = run.text();
            int start = start();
            while (!rest.isEmpty()) {
                String piece = font.plainSubstrByWidth(rest, left + width - start);
                if (piece.isEmpty() && start > left) {
                    newLine();
                    start = left;
                    continue;
                }
                piece = piece.isEmpty() ? rest.substring(0, 1) : piece;
                drawPiece(font, run, piece, start, lineTop(), lineHeight, painter);
                start += font.width(piece);
                rest = rest.substring(piece.length());
                if (!rest.isEmpty()) {
                    newLine();
                    start = left;
                }
            }
            at = start;
        }
    }

    private static void drawItem(Font font, Run run, String piece, int x, int top, Painter painter) {
        ItemIcons icons = painter.icons();
        if (icons != null) {
            painter.graphics().item(icons.of(String.valueOf(run.ref())), x, top + ICON_NUDGE);
        }
        painter.graphics().text(font, piece, x + ICON_SIZE + ICON_TEXT_GAP, top + TEXT_NUDGE, painter.textColor());
    }

    private static void drawPiece(Font font, Run run, String piece, int x, int top, int lineHeight,
                                  @Nullable Painter painter) {
        if (painter == null) {
            return;
        }
        int textTop = top + (lineHeight == ICON_LINE_HEIGHT ? TEXT_NUDGE : 0);
        GuiGraphicsExtractor graphics = painter.graphics();
        switch (run.kind()) {
            case TYPE -> GooTypeIcons.draw(graphics, painter.targets(), String.valueOf(run.ref()), x,
                    top + ICON_NUDGE);
            case ITEM -> drawItem(font, run, piece, x, top, painter);
            case LINK -> {
                graphics.text(font, piece, x, textTop, painter.linkColor());
                painter.links().landed(String.valueOf(run.ref()), x, textTop, font.width(piece));
            }
            case ERROR -> graphics.text(font, piece, x, textTop, RED);
            default -> graphics.text(font, piece, x, textTop, painter.textColor());
        }
    }
}
