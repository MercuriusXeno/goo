package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Draws a value as curated runs laid across lines of a width: a goo type as its icon beside its amount, an item as
 * its icon and name, a prefab as its name link, a number and an operator as text and a per-type object as a row per
 * type, so no brace, quote, $ or namespaced id draws (decisions curated-view-shows-no-brace-quote-or-raw-id,
 * goo-types-draw-as-icons-named-on-hover); each link lands with the caller, so a shift + left click on it opens its
 * prefab and a plain left click reaches the view beneath it, which modifies the value in place. A value the terms
 * cannot read draws its text as an error.
 * Decision references-link-to-their-variable-and-hover-a-card.
 * Decision shift-left-click-follows-plain-left-modifies.
 */
final class ExpressionLines {
    /** A line's height where a run draws an icon. */
    static final int ICON_LINE_HEIGHT = 18;
    /** A line's height where only text draws. */
    static final int TEXT_LINE_HEIGHT = 10;
    private static final int ITEM_SPRITE_SIZE = 16;
    private static final int ICON_TEXT_GAP = 2;
    private static final int RUN_GAP = 3;
    private static final int ICON_NUDGE = 1;
    private static final int GLYPH_GAP = 1;
    private static final int HALF = 2;
    private static final int RED = 0xFFFF5555;
    /**
     * The = + - x ( ) symbols' color, which stands out from the text's (decision
     * read-view-reads-type-equals-possessive-terms).
     */
    static final int SYMBOL_COLOR = 0xFFE0C060;
    private static final String NOTHING = "";
    private static final String PLUS = "+";
    private static final String MINUS = "-";
    // Decision read-view-reads-type-equals-possessive-terms: times reads x.
    private static final String TIMES = "x";
    /**
     * What follows a type row's type, its terms inline after it (decision
     * builder-inlines-terms-under-indentation-rules-with-a-colon).
     */
    static final String COLON = ":";
    private static final String POSSESSIVE = "'s";
    private static final String DIVIDE = "/";
    private static final String OPEN = "(";
    private static final String CLOSE = ")";
    private static final String DENIED = "denied";
    private static final String TYPES_SEPARATOR = ", ";
    private static final String AMOUNT_SEPARATOR = " ";
    private static final String ARROW = "->";
    private static final String OPERATOR_CHARS = "+-*/()";
    private static final char REFERENCE_MARK = '$';
    // One subtracted term draws alone, with no parentheses.
    private static final int SUBTRACTED_ALONE = 1;

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
        /** An integer an expression writes, or a count. */
        NUMBER,
        /**
         * A goo amount, drawn small a pixel lower and as a mixed number, its part under 1 stacked over its bar, as the
         * Items row draws one (decisions goo-amounts-draw-smaller-and-a-pixel-lower-everywhere,
         * groups-value-draws-as-every-other-panes-value); marks around it, as "(10½,", ride with it.
         */
        AMOUNT,
        /** An operator or a parenthesis. */
        OPERATOR,
        /** Plain text. */
        TEXT,
        /** Text drawn flush against the run before it, as a member's 's against its prefab. */
        SUFFIX,
        /** A symbol drawn flush against the run before it in the symbols' color, as a reduction's closing paren. */
        FLUSH_SYMBOL,
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
     * @param kind a run's kind
     * @return whether the run draws in the symbols' color: an operator, a parenthesis or a flush symbol
     */
    static boolean drawsAsSymbol(Kind kind) {
        return kind == Kind.OPERATOR || kind == Kind.FLUSH_SYMBOL;
    }

    /**
     * @param kind a run's kind
     * @return whether the run stands flush against the run before it, with no gap
     */
    static boolean placesFlush(Kind kind) {
        return kind == Kind.SUFFIX || kind == Kind.FLUSH_SYMBOL;
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
     * @param targets the frame's click areas
     * @param follow  opens the prefab a link names
     * @return a sink laying each link's piece as an area a shift + left click follows, hovering the prefab's value
     */
    static LinkSink followedOnShiftClick(ClickTargets targets, Consumer<String> follow) {
        return (variable, x, y, width) -> targets.addFollow(x, y, width, TEXT_LINE_HEIGHT,
                () -> follow.accept(variable), PrefabHovers.hoverKey(variable));
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
                   int linkColor, LinkSink links, int symbolColor) {
        /**
         * A painter drawing the symbols in {@link #SYMBOL_COLOR}.
         *
         * @param graphics  the frame's graphics
         * @param icons     the items' icons, null where no item draws
         * @param targets   the frame's click areas, which take each type icon's hover
         * @param textColor text's color
         * @param linkColor a link's color
         * @param links     takes where each piece of a link landed
         */
        Painter(GuiGraphicsExtractor graphics, @Nullable ItemIcons icons, ClickTargets targets, int textColor,
                int linkColor, LinkSink links) {
            this(graphics, icons, targets, textColor, linkColor, links, SYMBOL_COLOR);
        }
    }

    /**
     * @param value an entry's terms
     * @param names an item's display name by its id
     * @return the runs it draws as
     */
    static List<Run> runs(ExpressionTerms.Value value, Function<String, String> names) {
        List<Run> runs = new ArrayList<>();
        if (value instanceof ExpressionTerms.PerType perType) {
            // Decision builder-inlines-terms-under-indentation-rules-with-a-colon: {type}: then the row's terms inline.
            for (ExpressionTerms.TypeRow row : perType.rows()) {
                if (!runs.isEmpty()) {
                    runs.add(new Run(Kind.BREAK, NOTHING, null));
                }
                runs.add(type(row.type()));
                runs.add(operator(COLON));
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
     * @return each type as its icon beside its amount, small and mixed, and any other part as text
     */
    static List<Run> resolvedRuns(String resolved) {
        List<Run> runs = new ArrayList<>();
        for (String part : resolved.split(TYPES_SEPARATOR)) {
            int space = part.indexOf(AMOUNT_SEPARATOR);
            if (space > 0 && ExpressionTerms.isGooType(part.substring(0, space))) {
                runs.add(type(part.substring(0, space)));
                runs.add(amount(part.substring(space + 1)));
            } else if (!part.isEmpty()) {
                runs.add(part.chars().allMatch(Character::isDigit) ? amount(part) : text(part));
            }
        }
        return runs;
    }

    /**
     * @param text a goo amount, whole, n/d, or riding marks as "(10,"
     * @return it as an amount run, drawn small and mixed (decision groups-value-draws-as-every-other-panes-value)
     */
    static Run amount(String text) {
        return new Run(Kind.AMOUNT, text, null);
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
        List<ExpressionTerms.Term> terms = sum.terms();
        if (subtractsSeveral(terms)) {
            addSubtractedSum(runs, terms, names);
            return;
        }
        for (ExpressionTerms.Term term : terms) {
            if (term.join() != ExpressionTerms.Join.FIRST) {
                runs.add(operator(term.join() == ExpressionTerms.Join.PLUS ? PLUS : MINUS));
            }
            addTerm(runs, term, names);
        }
    }

    // Decision backwards-derivation-subtracts-a-parenthesized-sum: one term less two or more draws as that term less
    // their parenthesized sum, "a - (b + c)", the same value read as what it works back from.
    private static boolean subtractsSeveral(List<ExpressionTerms.Term> terms) {
        return terms.size() > SUBTRACTED_ALONE + 1
                && terms.stream().skip(1).allMatch(term -> term.join() == ExpressionTerms.Join.MINUS);
    }

    private static void addSubtractedSum(List<Run> runs, List<ExpressionTerms.Term> terms,
                                         Function<String, String> names) {
        addTerm(runs, terms.getFirst(), names);
        runs.add(operator(MINUS));
        runs.add(operator(OPEN));
        for (int index = 1; index < terms.size(); index++) {
            if (index > 1) {
                runs.add(operator(PLUS));
            }
            addTerm(runs, terms.get(index), names);
        }
        runs.add(operator(CLOSE));
    }

    private static void addTerm(List<Run> runs, ExpressionTerms.Term term, Function<String, String> names) {
        for (ExpressionTerms.Factor factor : term.factors()) {
            addScale(runs, factor.scale());
            addAtom(runs, factor.atom(), names);
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
                // Decision diagnose-and-correct-prefabs-drawn-without-their-dollar.
                runs.add(new Run(Kind.LINK, PrefabNames.marked(prefab.name()), prefab.name()));
                // Decision read-view-reads-type-equals-possessive-terms: a member reads as the prefab's own.
                // Decision possessive-sits-flush-against-its-word-button.
                if (prefab.type() != null) {
                    runs.add(new Run(Kind.SUFFIX, POSSESSIVE, null));
                }
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
     * @param font the font
     * @param runs a value's runs
     * @return how far below a line's top its text draws, so a caller lines a text label up with the runs' text
     */
    static int textOffset(Font font, List<Run> runs) {
        return lineHeight(runs) == ICON_LINE_HEIGHT ? iconLineTextOffset(font) : 0;
    }

    // Decision operators-center-on-the-sprite: text on an icon line, an operator among it, centers its glyphs on the
    // sprite.
    private static int iconLineTextOffset(Font font) {
        return centeredTextTop(ICON_NUDGE, ITEM_SPRITE_SIZE, font.lineHeight);
    }

    /**
     * A font's line height holds an empty row under its glyphs and their shadow, so the glyphs' ink is one row shorter
     * than the line; centering the ink, not the line, centers the text on a sprite or a row.
     *
     * @param top        the band's top
     * @param height     the band's height
     * @param lineHeight the font's line height
     * @return the text's top that centers its glyphs in the band
     */
    static int centeredTextTop(int top, int height, int lineHeight) {
        return top + (height - lineHeight + GLYPH_GAP) / HALF;
    }

    /**
     * @param font  the font
     * @param runs  a value's runs
     * @param width the width the lines fill
     * @return the height the runs take, at least one line
     */
    static int height(Font font, List<Run> runs, int width) {
        return lay(runs, new Cursor(Lettering.full(font), new Span(0, 0, width, lineHeight(runs)), null,
                GooTypeIcons::sizeOf)) * lineHeight(runs);
    }

    /**
     * @param font the font
     * @param runs a value's runs
     * @return the width the runs take laid on one line
     */
    static int width(Font font, List<Run> runs) {
        return width(font, runs, GooTypeIcons::sizeOf);
    }

    /**
     * @param font     the font
     * @param runs     a value's runs
     * @param typeSize a goo type icon's side by its type, its texture's own
     * @return the width the runs take laid on one line
     */
    static int width(Font font, List<Run> runs, ToIntFunction<String> typeSize) {
        return width(Lettering.full(font), runs, typeSize);
    }

    /**
     * @param lettering the font and the scale the runs' text draws at
     * @param runs      a value's runs
     * @return the width the runs take laid on one line
     */
    static int width(Lettering lettering, List<Run> runs) {
        return width(lettering, runs, GooTypeIcons::sizeOf);
    }

    private static int width(Lettering lettering, List<Run> runs, ToIntFunction<String> typeSize) {
        int width = 0;
        for (Run run : runs) {
            if (run.kind() != Kind.BREAK) {
                boolean gapped = width != 0 && !placesFlush(run.kind());
                width += (gapped ? RUN_GAP : 0) + runWidth(lettering, run, typeSize);
            }
        }
        return width;
    }

    /**
     * @param lineTop an icon line's top
     * @param size    a goo type icon's side
     * @return the icon's top, centered in the line (decision goo-type-icons-draw-native-everywhere)
     */
    static int typeIconTop(int lineTop, int size) {
        return lineTop + GooTypeIcons.centered(ICON_LINE_HEIGHT, size);
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
        lay(runs, new Cursor(Lettering.full(font), new Span(x, y, width, lineHeight(runs)), painter,
                GooTypeIcons::sizeOf));
    }

    /**
     * Draws the runs lettered at a scale on lines of the height given, as the builder draws its terms (decision
     * builder-font-and-spacing-shrink-padding-untouched).
     *
     * @param lettering the font and the scale the runs' text draws at
     * @param runs      a value's runs
     * @param line      the lines' left edge, the first line's top, the width the lines fill and each line's height
     * @param painter   where they draw
     */
    static void draw(Lettering lettering, List<Run> runs, Span line, Painter painter) {
        lay(runs, new Cursor(lettering, line, painter, GooTypeIcons::sizeOf));
    }

    /**
     * Where runs lay.
     *
     * @param left       the lines' left edge
     * @param top        the first line's top
     * @param width      the width the lines fill
     * @param lineHeight each line's height
     */
    record Span(int left, int top, int width, int lineHeight) {
    }

    private static int runWidth(Lettering lettering, Run run, ToIntFunction<String> typeSize) {
        return switch (run.kind()) {
            case TYPE -> typeSize.applyAsInt(String.valueOf(run.ref()));
            case ITEM -> ITEM_SPRITE_SIZE + ICON_TEXT_GAP + lettering.width(run.text());
            case AMOUNT -> SmallDigits.width(lettering.font(), run.text());
            case BREAK -> 0;
            default -> lettering.width(run.text());
        };
    }

    // Answers how many lines the runs take; a text run wider than a line breaks across lines, and any other run
    // starts a fresh line where it does not fit.
    // Decision builder-inlines-terms-under-indentation-rules-with-a-colon: a type row's wrap stands one indent in
    // under its type; a parenthetical that does not fit the rest of the line takes a line of its own with its join;
    // a wrap inside it stands one indent further in, back out past its closing paren.
    private static int lay(List<Run> runs, Cursor cursor) {
        for (int index = 0; index < runs.size(); index++) {
            Run run = runs.get(index);
            switch (run.kind()) {
                case BREAK -> cursor.breakLine(0);
                case SUFFIX, FLUSH_SYMBOL -> cursor.placeFlush(run);
                case TEXT, ERROR -> cursor.placeText(run);
                default -> placeOperandOrSymbol(runs, index, cursor);
            }
        }
        return cursor.lines;
    }

    // Decision possessive-sits-flush-against-its-word-button: a word wraps with the suffix flush against it.
    private static void fitWithSuffix(List<Run> runs, int index, Cursor cursor) {
        int suffix = index + 1;
        if (suffix < runs.size() && placesFlush(runs.get(suffix).kind())) {
            cursor.fitTogether(width(cursor.lettering, runs.subList(index, suffix + 1), cursor.typeSize));
        }
    }

    private static void placeOperandOrSymbol(List<Run> runs, int index, Cursor cursor) {
        Run run = runs.get(index);
        if (opensJoinedParenthetical(runs, index)) {
            cursor.fitTogether(width(cursor.lettering, runs.subList(index, closingParen(runs, index + 1) + 1),
                    cursor.typeSize));
        }
        if (isSymbol(run, CLOSE)) {
            cursor.wrapAt(-ControlFlow.INDENT);
        }
        fitWithSuffix(runs, index, cursor);
        cursor.placeWhole(run);
        if (isSymbol(run, COLON) || isSymbol(run, OPEN)) {
            cursor.wrapAt(ControlFlow.INDENT);
        }
    }

    private static boolean isSymbol(Run run, String symbol) {
        return run.kind() == Kind.OPERATOR && symbol.equals(run.text());
    }

    // A + or - joining a parenthesized term.
    private static boolean opensJoinedParenthetical(List<Run> runs, int index) {
        Run run = runs.get(index);
        boolean join = isSymbol(run, PLUS) || isSymbol(run, MINUS);
        return join && index + 1 < runs.size() && isSymbol(runs.get(index + 1), OPEN);
    }

    // The index of the ) closing the ( at open, or the last run where none does.
    private static int closingParen(List<Run> runs, int open) {
        int depth = 0;
        for (int index = open; index < runs.size(); index++) {
            depth += isSymbol(runs.get(index), OPEN) ? 1 : 0;
            depth -= isSymbol(runs.get(index), CLOSE) ? 1 : 0;
            if (depth == 0) {
                return index;
            }
        }
        return runs.size() - 1;
    }

    // Where the next run lands as the runs lay across their lines.
    private static final class Cursor {
        private final Lettering lettering;
        private final int left;
        private final int top;
        private final int width;
        private final int lineHeight;
        private final @Nullable Painter painter;
        // The measure and the draw read one size per type icon (decision goo-type-icons-draw-native-everywhere).
        private final ToIntFunction<String> typeSize;
        private int lines = 1;
        private int at;
        // Where the current line's runs start: the left edge, or one indent step in under a type row's head.
        private int lineLeft;

        Cursor(Lettering lettering, Span span, @Nullable Painter painter, ToIntFunction<String> typeSize) {
            this.lettering = lettering;
            this.left = span.left();
            this.top = span.top();
            this.width = span.width();
            this.lineHeight = span.lineHeight();
            this.painter = painter;
            this.typeSize = typeSize;
            this.at = left;
            this.lineLeft = left;
        }

        // A wrapped line starts where the line it continues did.
        void newLine() {
            lines++;
            at = lineLeft;
        }

        void breakLine(int indent) {
            lineLeft = left + indent;
            newLine();
        }

        // Moves where a wrapped line starts, leaving the current line standing.
        void wrapAt(int step) {
            lineLeft = Math.max(left, lineLeft + step);
        }

        // Starts a fresh line where pieces this wide do not fit the rest of the current one.
        void fitTogether(int piecesWidth) {
            if (start() + piecesWidth > left + width && at > lineLeft) {
                newLine();
            }
        }

        private int lineTop() {
            return top + (lines - 1) * lineHeight;
        }

        private int start() {
            return at == lineLeft ? lineLeft : at + RUN_GAP;
        }

        void placeWhole(Run run) {
            int runWidth = runWidth(lettering, run, typeSize);
            if (start() + runWidth > left + width && at > lineLeft) {
                newLine();
            }
            int start = start();
            drawPiece(run, run.text(), start);
            at = start + runWidth;
        }

        // Against the run before, no gap and no wrap between them.
        void placeFlush(Run run) {
            drawPiece(run, run.text(), at);
            at += runWidth(lettering, run, typeSize);
        }

        void placeText(Run run) {
            String rest = run.text();
            int start = start();
            while (!rest.isEmpty()) {
                String piece = lettering.fitting(rest, left + width - start);
                if (piece.isEmpty() && start > lineLeft) {
                    newLine();
                    start = lineLeft;
                    continue;
                }
                piece = piece.isEmpty() ? rest.substring(0, 1) : piece;
                drawPiece(run, piece, start);
                start += lettering.width(piece);
                rest = rest.substring(piece.length());
                if (!rest.isEmpty()) {
                    newLine();
                    start = lineLeft;
                }
            }
            at = start;
        }

        // A text line's text stands at its top; an icon line's centers on its sprite, and a line of any other
        // height, as the builder's, centers its glyphs in the line.
        private int textOffset() {
            if (lineHeight == ICON_LINE_HEIGHT) {
                return lettering.centeredTop(ICON_NUDGE, ITEM_SPRITE_SIZE);
            }
            return lineHeight == TEXT_LINE_HEIGHT ? 0 : lettering.centeredTop(0, lineHeight);
        }

        private void drawPiece(Run run, String piece, int x) {
            if (painter == null) {
                return;
            }
            int lineTop = lineTop();
            int textTop = lineTop + textOffset();
            GuiGraphicsExtractor graphics = painter.graphics();
            switch (run.kind()) {
                case TYPE -> {
                    int size = typeSize.applyAsInt(String.valueOf(run.ref()));
                    GooTypeIcons.draw(graphics, painter.targets(), String.valueOf(run.ref()), x,
                            lineTop + GooTypeIcons.centered(lineHeight, size), size);
                }
                case ITEM -> drawItem(lettering, run, piece, new int[] {x, lineTop}, painter);
                // Decision goo-amounts-draw-smaller-and-a-pixel-lower-everywhere: as the Items row's SmallDigits.
                case AMOUNT -> SmallDigits.draw(graphics, lettering.font(), piece, x,
                        lineTop + GooTypeIcons.centered(lineHeight, SmallDigits.GLYPH_HEIGHT) + 1, painter.textColor());
                case LINK -> drawLink(lettering, run, piece, new int[] {x, textTop}, painter);
                case ERROR -> lettering.draw(graphics, piece, x, textTop, RED);
                // Decision read-view-reads-type-equals-possessive-terms.
                case OPERATOR, FLUSH_SYMBOL -> lettering.draw(graphics, piece, x, textTop, painter.symbolColor());
                default -> lettering.draw(graphics, piece, x, textTop, painter.textColor());
            }
        }
    }

    // Decision holding-shift-underlines-every-link.
    // The piece's place is its x and its text's top.
    private static void drawLink(Lettering lettering, Run run, String piece, int[] place, Painter painter) {
        int x = place[0];
        int textTop = place[1];
        int pieceWidth = lettering.width(piece);
        lettering.draw(painter.graphics(), piece, x, textTop, painter.linkColor());
        ButtonPainter.underlineWhileShiftHeld(painter.graphics(), painter.targets(), lettering, x, textTop,
                pieceWidth, painter.linkColor());
        // Decision every-prefab-reference-hovers-its-value; a link sink's own area, laid after, stands over it.
        painter.targets().addHover(x, textTop, pieceWidth, TEXT_LINE_HEIGHT,
                PrefabHovers.hoverKey(String.valueOf(run.ref())));
        painter.links().landed(String.valueOf(run.ref()), x, textTop, pieceWidth);
    }

    // The piece's place is its x and its line's top; its text centers on its sprite.
    private static void drawItem(Lettering lettering, Run run, String piece, int[] place, Painter painter) {
        int x = place[0];
        int top = place[1];
        ItemIcons icons = painter.icons();
        if (icons != null) {
            painter.graphics().item(icons.of(String.valueOf(run.ref())), x, top + ICON_NUDGE);
        }
        lettering.draw(painter.graphics(), piece, x + ITEM_SPRITE_SIZE + ICON_TEXT_GAP,
                lettering.centeredTop(top + ICON_NUDGE, ITEM_SPRITE_SIZE), painter.textColor());
    }
}
